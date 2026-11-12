import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import net from 'node:net';
import { EventEmitter } from 'node:events';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { normalizeConfiguration, loadConfiguration } from '../director/config.mjs';
import { ServerController, portOpen } from '../director/server.mjs';
import { SessionReport } from '../director/report.mjs';
import { endpoint } from '../director/session.mjs';
import { ScenarioGroup } from '../director/scenario.mjs';
import { DirectorSession } from '../director/session.mjs';
import { startControl, requestControl } from '../director/control.mjs';

const fixture = fileURLToPath(new URL('fixtures/server.mjs', import.meta.url));

async function freePort() {
    const socket = net.createServer();
    await new Promise(resolve => socket.listen(0, '127.0.0.1', resolve));
    const port = socket.address().port;
    await new Promise(resolve => socket.close(resolve));
    return port;
}

function directory(t) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'stagecraft-director-'));
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    return root;
}

function topology(root, servers, roles = { A: { client: 'DirectorA', server: Object.keys(servers)[0] } }) {
    return normalizeConfiguration({ servers, roles, reportDir: path.join(root, 'reports') }, root);
}

function fakeServer(root, port, args = []) {
    return {
        host: '127.0.0.1', port, command: process.execPath, args: [fixture, String(port), ...args],
        cwd: root, log: path.join(root, 'server-' + port + '.log'),
        readyPattern: 'READY', savedPattern: 'SAVED', startupTimeout: 15_000, stopTimeout: 10_000,
    };
}

test('legacy configuration keeps attached server ownership and resolves one default server', () => {
    const config = normalizeConfiguration({ server: { host: 'localhost', port: 25571 }, roles: { A: 'Player:window' } });
    assert.equal(config.servers.default.managed, false);
    assert.equal(config.servers.default.stopOnClose, false);
    assert.equal(config.roles.A.server, 'default');
    assert.equal(config.roles.A.window, true);
});

test('topologies reject duplicate endpoints, unknown destinations and shared client directories', t => {
    const root = directory(t);
    assert.throws(() => topology(root, { A: { port: 1 }, B: { host: 'localhost', port: 1 } }), /Duplicate server endpoint/);
    assert.throws(() => topology(root, { A: { port: 1 } }, { A: { client: 'Player', server: 'missing' } }), /Unknown server/);
    assert.throws(() => topology(root, { A: { port: 1 } }, { A: 'Player', B: 'player' }), /Duplicate client/);
});

test('JSON paths are relative to the configuration and reports redact credentials', t => {
    const root = directory(t);
    fs.writeFileSync(path.join(root, 'network.json'), JSON.stringify({
        servers: { A: { port: 12345, runDir: 'server', rcon: { port: 12346, password: 'test-secret' } } },
        roles: { A: { client: 'Player', mods: ['mods'] } }, reportDir: 'reports',
    }));
    const config = loadConfiguration(path.join(root, 'network.json'));
    assert.equal(config.servers.A.runDir, path.join(root, 'server'));
    assert.equal(config.roles.A.mods[0], path.join(root, 'mods'));
    const report = new SessionReport(config);
    assert.ok(!fs.readFileSync(path.join(report.dir, 'session.json'), 'utf8').includes('test-secret'));
});

test('server readiness, two queued restarts and cleanup preserve process ownership', async t => {
    const root = directory(t), port = await freePort();
    const config = topology(root, { A: fakeServer(root, port) });
    const report = new SessionReport(config);
    const server = new ServerController(config.servers.A, report);
    try {
        await server.start();
        assert.equal(await portOpen(config.servers.A), true);
        const firstPid = server.identity.pid;
        await Promise.all([server.restart(), server.restart()]);
        assert.equal(server.generation, 3);
        assert.notEqual(server.identity.pid, firstPid);
        await server.stop();
        assert.equal(await portOpen(config.servers.A), false);
        assert.equal(await server.processAlive(), false);
        const events = fs.readFileSync(path.join(report.dir, 'timeline.jsonl'), 'utf8').trim().split('\n').map(JSON.parse);
        assert.deepEqual(events.filter(event => event.type === 'server.ready').map(event => event.generation), [1, 2, 3]);
    } finally {
        await server.stop();
    }
});

test('an open port without readiness times out and releases the exact spawned server', async t => {
    const root = directory(t), port = await freePort();
    const config = topology(root, { A: { ...fakeServer(root, port, ['--never-ready']), startupTimeout: 2500 } });
    const server = new ServerController(config.servers.A, new SessionReport(config));
    try {
        await assert.rejects(server.start(), /did not become ready/);
        assert.equal(await portOpen(config.servers.A), false);
        assert.equal(await server.processAlive(), false);
    } finally {
        await server.stop();
    }
});

test('partial startup failure cleans the other managed server before rejecting', async t => {
    const root = directory(t), a = await freePort(), b = await freePort();
    const config = topology(root, { A: fakeServer(root, a), B: fakeServer(root, b, ['--fail']) });
    const session = new DirectorSession(config);
    await assert.rejects(session.initialize(), /exited during startup|operation was aborted|AbortError/i);
    assert.equal(await portOpen(config.servers.A), false);
    assert.equal(await portOpen(config.servers.B), false);
    assert.equal(session.report.state, 'closed');
});

class FakeLink {
    constructor(role, script, startupDelay = 0) {
        this.role = role;
        this.script = script;
        this.startupDelay = startupDelay;
        this.events = new EventEmitter();
        this.signals = [];
        this.replies = new Map();
    }
    on(name, listener) { this.events.on(name, listener); return () => this.events.off(name, listener); }
    event(name, fields = {}) {
        this.events.emit(name, { run_id: this.runId, execution_id: this.execution, ...fields });
    }
    async run(name, { args }) {
        await delay(this.startupDelay);
        this.execution = args.execution_id;
        this.runId = this.role + '-' + this.execution;
        this.args = args;
        const result = new Promise(resolve => { this.finish = resolve; });
        this.event('started');
        Promise.resolve().then(() => this.script(this)).then(
            () => this.finish({ status: 'passed' }),
            error => this.finish({ status: 'failed', error: error.message }));
        return result;
    }
    async request(type, fields) {
        if (type === 'signal') {
            if (fields.execution_id !== this.execution || fields.run_id !== this.runId) return { accepted: false };
            this.signals.push(fields.name);
        } else if (type === 'director_reply') {
            this.replies.set(fields.request, fields);
        }
        return { accepted: true };
    }
    async cancel(reason) { this.finish?.({ status: 'cancelled', error: reason }); }
    async wait(predicate) {
        const deadline = Date.now() + 2000;
        while (!predicate()) {
            if (Date.now() >= deadline) throw new Error('Fake client wait timed out');
            await delay(2);
        }
    }
}

function groupSession(t, links) {
    const root = directory(t);
    const roles = Object.fromEntries(Object.keys(links).map(role => [role, { client: role, server: role }]));
    return {
        config: { legacy: false }, roles, links, observed: {},
        report: new SessionReport({ reportDir: root }), log() {},
        snapshot() { return { servers: {} }; },
    };
}

test('signals emitted before the peer starts are delivered once within the execution', async t => {
    const a = new FakeLink('A', async link => {
        link.event('signal', { name: 'published' });
        await link.wait(() => link.signals.includes('bought'));
    });
    const b = new FakeLink('B', async link => {
        await link.wait(() => link.signals.includes('published'));
        link.event('signal', { name: 'bought' });
    }, 50);
    const group = new ScenarioGroup(groupSession(t, { A: a, B: b }), { source: '-- scenario' });
    assert.equal((await group.run()).passed, true);
    assert.deepEqual(b.signals, ['published']);
    assert.equal(a.args.shared, b.args.shared);
});

test('signals from a previous execution are ignored and named barriers can be reused', async t => {
    let group;
    const script = async link => {
        link.event('signal', { name: 'stale', execution_id: 'old-execution' });
        for (let round = 0; round < 2; round++) {
            const id = link.role + '-' + round;
            await group.barrier(link.role, {
                id, name: 'ready', timeout: 1000, run_id: link.runId, execution_id: link.execution,
            });
            await link.wait(() => link.replies.has(id));
            assert.equal(link.replies.get(id).phase, 'done');
        }
        assert.deepEqual(link.signals, []);
    };
    group = new ScenarioGroup(groupSession(t, { A: new FakeLink('A', script), B: new FakeLink('B', script) }), {});
    assert.equal((await group.run()).passed, true);
});

test('one role failing cancels a waiting participant with the concrete cause', async t => {
    const a = new FakeLink('A', async () => { await delay(30); throw new Error('purchase failed'); });
    const b = new FakeLink('B', () => new Promise(() => {}));
    const group = new ScenarioGroup(groupSession(t, { A: a, B: b }), {});
    const outcome = await group.run();
    assert.equal(outcome.passed, false);
    assert.equal(outcome.results.B.status, 'cancelled');
    assert.match(outcome.results.B.error, /purchase failed/);
    assert.ok(outcome.millis < 1000);
});

test('manual session control reports status and closes its listener after down', async t => {
    const root = directory(t);
    const session = {
        report: new SessionReport({ reportDir: root }),
        snapshot() { return { servers: {}, clients: {} }; },
        async stop() { this.report.state = 'closed'; this.report.write(this.snapshot()); },
    };
    session.report.state = 'ready';
    const control = await startControl(session);
    t.after(() => control.close());
    const file = path.join(session.report.dir, 'session.json');
    assert.equal((await requestControl(file, 'status')).state, 'ready');
    assert.equal((await requestControl(file, 'down')).state, 'closed');
    await control.closed;
    assert.equal((await requestControl(file, 'status')).state, 'closed');
});

test('client addresses resolve to the same endpoint before and after a transfer', () => {
    assert.equal(endpoint('127.0.0.1:25621'), '127.0.0.1:25621');
    assert.equal(endpoint('/127.0.0.1:25621'), '127.0.0.1:25621');
    assert.equal(endpoint('127.0.0.1/127.0.0.1:25622'), '127.0.0.1:25622');
    assert.equal(endpoint('localhost/127.0.0.1:25622'), '127.0.0.1:25622');
    assert.equal(endpoint('localhost:25565'), '127.0.0.1:25565');
    assert.equal(endpoint('play.example.com'), 'play.example.com:25565');
    assert.equal(endpoint(undefined), null);
});
