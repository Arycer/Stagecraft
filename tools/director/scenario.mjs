import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

/** A scenario has one execution id across roles and separate client run ids on the wire. */
export class ScenarioGroup {
    constructor(session, options) {
        this.session = session;
        this.options = options;
        this.id = crypto.randomBytes(12).toString('hex');
        this.shared = crypto.randomBytes(6).toString('hex');
        this.roles = options.roles ?? Object.keys(session.roles);
        if (!this.roles.length || new Set(this.roles).size !== this.roles.length) throw new Error('Invalid scenario roles');
        for (const role of this.roles) {
            if (!session.roles[role]) throw new Error('Unknown scenario role ' + role);
        }
        this.name = (options.name ?? path.basename(options.file ?? 'inline.lua', '.lua')).replace(/[^A-Za-z0-9_-]/g, '_');
        this.runIds = new Map();
        this.finished = new Set();
        this.queuedSignals = [];
        this.barriers = new Map();
        this.subscriptions = [];
        this.deliveries = new Set();
    }

    accepts(role, event) {
        return this.roles.includes(role) && this.runIds.get(role) === event.run_id
            && (event.execution_id === this.id || (this.session.config.legacy && !event.execution_id));
    }

    track(promise) {
        this.deliveries.add(promise);
        promise.finally(() => this.deliveries.delete(promise)).catch(() => {});
        return promise;
    }

    relay(from, event) {
        for (const role of this.roles) {
            if (role === from || this.finished.has(role)) continue;
            this.track(this.session.links[role].request('signal', {
                name: event.name, execution_id: this.id, run_id: this.runIds.get(role),
            }).catch(error => this.fail('Signal from ' + from + ' to ' + role + ': ' + error.message)));
        }
        this.session.report.event('scenario.signal', { execution: this.id, role: from, name: event.name });
    }

    fail(reason) {
        if (this.failure) return;
        this.failure = reason;
        this.session.report.event('scenario.failure', { execution: this.id, reason });
        for (const barrier of this.barriers.values()) {
            clearTimeout(barrier.timer);
            for (const [role, request] of barrier.requests) {
                this.track(this.reply(role, request, 'error', reason).catch(() => {}));
            }
        }
        this.barriers.clear();
        for (const role of this.roles) {
            if (!this.finished.has(role)) this.track(this.session.links[role].cancel(reason).catch(() => {}));
        }
    }

    reply(role, request, phase, error) {
        return this.session.links[role].request('director_reply', { request, phase, ...(error ? { error } : {}) });
    }

    async barrier(role, event) {
        if (!this.accepts(role, event)) throw new Error('Barrier belongs to a different execution');
        const participants = event.roles ? event.roles.split(',') : this.roles;
        if (!participants.includes(role) || new Set(participants).size !== participants.length
                || participants.some(name => !this.roles.includes(name))) {
            throw new Error('Invalid barrier participants');
        }
        const signature = [...participants].sort().join(',');
        let barrier = this.barriers.get(event.name);
        if (!barrier) {
            const timeout = Number(event.timeout ?? 60_000);
            if (!Number.isFinite(timeout) || timeout <= 0 || timeout > 900_000) throw new Error('Invalid barrier deadline');
            barrier = { signature, participants, requests: new Map() };
            barrier.timer = setTimeout(() => this.fail('Barrier ' + event.name + ' timed out waiting for '
                + participants.filter(name => !barrier.requests.has(name)).join(', ')), timeout);
            this.barriers.set(event.name, barrier);
        }
        if (barrier.signature !== signature) throw new Error('Barrier participants disagree for ' + event.name);
        if (barrier.requests.has(role)) throw new Error('Role ' + role + ' entered the same barrier twice');
        barrier.requests.set(role, event.id);
        this.session.report.event('scenario.barrier', { execution: this.id, role, name: event.name });
        if (barrier.requests.size === participants.length) {
            clearTimeout(barrier.timer);
            this.barriers.delete(event.name);
            await Promise.all([...barrier.requests].map(([participant, request]) => this.reply(participant, request, 'done')));
        }
    }

    prepareFiles() {
        const fixtureDirs = {};
        if (!this.options.file) return fixtureDirs;
        const directory = path.dirname(this.options.file);
        const fixtures = path.join(directory, 'fixtures');
        const libraries = [path.join(directory, 'lib'), path.join(directory, '..', 'lib')].find(fs.existsSync);
        for (const role of this.roles) {
            const clientDir = this.session.clientDir(role);
            if (fs.existsSync(fixtures)) {
                const target = path.join(clientDir, 'config/stagecraft/fixtures');
                fs.cpSync(fixtures, target, { recursive: true });
                fixtureDirs[role] = target.replaceAll('\\', '/');
            }
            if (libraries) {
                const target = path.join(clientDir, 'config/stagecraft/scripts/lib');
                fs.mkdirSync(target, { recursive: true });
                fs.cpSync(libraries, target, { recursive: true });
            }
        }
        return fixtureDirs;
    }

    async run() {
        const started = Date.now();
        const source = this.options.source ?? (this.options.file ? fs.readFileSync(this.options.file, 'utf8') : undefined);
        const fixtures = this.prepareFiles();
        this.session.report.event('scenario.started', { execution: this.id, scenario: this.name, roles: this.roles });
        for (const role of this.roles) {
            const link = this.session.links[role];
            this.subscriptions.push(link.on('started', event => {
                if (event.execution_id !== this.id && !(this.session.config.legacy && !event.execution_id)) return;
                this.runIds.set(role, event.run_id);
                if (this.failure) link.cancel(this.failure).catch(() => {});
                if (this.runIds.size === this.roles.length) {
                    for (const [from, signal] of this.queuedSignals.splice(0)) this.relay(from, signal);
                }
            }));
            this.subscriptions.push(link.on('signal', event => {
                if (!this.accepts(role, event)) return;
                if (this.runIds.size === this.roles.length) this.relay(role, event);
                else this.queuedSignals.push([role, event]);
            }));
            this.subscriptions.push(link.on('step', event => {
                if (!this.accepts(role, event)) return;
                this.session.log('[director] ' + role + ' ' + (event.status === 'passed' ? 'ok ' : 'FAIL ')
                    + event.name + (event.error ? ': ' + event.error : ''));
                this.session.report.event('scenario.step', { execution: this.id, role, ...event });
            }));
        }
        try {
            const entries = await Promise.all(this.roles.map(async role => {
                const roleNames = Object.fromEntries(Object.entries(this.session.roles).map(([name, value]) => ['client_' + name, value.client]));
                const args = {
                    shared: this.shared, ...roleNames,
                    ...(fixtures[role] ? { fixture_dir: fixtures[role] } : {}),
                    ...this.options.args, ...this.options.perRole?.[role],
                    role, execution_id: this.id, server_id: this.session.observed[role]?.server ?? '',
                };
                let result;
                try {
                    result = await this.session.links[role].run(this.name, {
                        source, args, timeout: this.options.timeout ?? 16 * 60_000,
                    });
                } catch (error) {
                    result = { status: 'failed', error: error.message, steps: [], logs: [] };
                }
                this.finished.add(role);
                if (result.status !== 'passed') this.fail('Role ' + role + ' ' + result.status + ': ' + (result.error ?? 'scenario failed'));
                return [role, result];
            }));
            await Promise.allSettled([...this.deliveries]);
            const results = Object.fromEntries(entries);
            const passed = !this.failure && Object.values(results).every(result => result.status === 'passed');
            const outcome = {
                scenario: this.name, executionId: this.id, shared: this.shared, passed,
                ...(this.failure ? { error: this.failure } : {}),
                millis: Date.now() - started, roles: this.roles,
                servers: this.session.snapshot().servers, clients: this.session.observed, results,
            };
            this.session.report.addRun(outcome);
            this.session.log('[director] ' + this.name + ': ' + (passed ? 'passed' : 'FAILED'));
            return outcome;
        } finally {
            for (const unsubscribe of this.subscriptions) unsubscribe();
            for (const barrier of this.barriers.values()) clearTimeout(barrier.timer);
            this.barriers.clear();
        }
    }
}
