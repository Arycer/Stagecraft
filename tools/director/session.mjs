import crypto from 'node:crypto';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { normalizeConfiguration, windowSlots } from './config.mjs';
import { SessionReport } from './report.mjs';
import { ServerController, waitPort, portOpen } from './server.mjs';
import { ScenarioGroup } from './scenario.mjs';
import { connect } from '../stagecraft-link.mjs';

/**
 * host:port of an address as Minecraft or Java prints it: "127.0.0.1:25565", "/127.0.0.1:25565" or, once a
 * transfer resolved the host, "127.0.0.1/127.0.0.1:25565" (Java's "name/ip:port"). The part after the last
 * slash is the endpoint.
 */
export function endpoint(address) {
    if (!address) return null;
    try {
        const url = new URL('minecraft://' + address.slice(address.lastIndexOf('/') + 1));
        return url.hostname.replace(/^localhost$/, '127.0.0.1').toLowerCase() + ':' + (url.port || '25565');
    } catch { return null; }
}

/** Owns a topology, its client links, execution coordination and resource cleanup. */
export class DirectorSession {
    constructor(options) {
        this.config = options.normalized ? options : normalizeConfiguration(options);
        this.roles = this.config.roles;
        this.log = this.config.log ?? console.log;
        this.report = new SessionReport(this.config);
        this.links = {};
        this.clients = {};
        this.observed = {};
        this.scopes = new Map();
        this.serverOperations = new Map();
        this.roleOperations = new Map();
        this.unsubscribers = [];
        this.startupAbort = new AbortController();
        this.servers = Object.fromEntries(Object.entries(this.config.servers).map(([id, config]) =>
            [id, new ServerController(config, this.report, (server, command) => this.commandOnServer(server, command))]));
    }

    clientDir(role) {
        return this.hmc.clientDir(this.roles[role].client, this.config.hmcHome ?? this.hmc.HOME);
    }

    snapshot() {
        return {
            reportDir: this.report.dir,
            servers: Object.fromEntries(Object.entries(this.servers).map(([id, server]) => [id, server.snapshot()])),
            clients: structuredClone(this.observed),
        };
    }

    async initialize() {
        try {
            const services = await Promise.allSettled(Object.entries(this.config.services).map(async ([name, service]) => {
                if (!await waitPort(service, true, service.timeout, this.startupAbort.signal)) {
                    throw new Error('External service ' + name + ' is unavailable');
                }
                this.report.event('service.ready', { service: name });
            }));
            const failedService = services.find(result => result.status === 'rejected');
            if (failedService) throw failedService.reason;
            const servers = await Promise.allSettled(Object.values(this.servers).map(server =>
                server.initialize(this.startupAbort.signal).catch(error => {
                    this.startupAbort.abort(error);
                    throw error;
                })));
            const failedServer = servers.find(result => result.status === 'rejected');
            if (failedServer) throw failedServer.reason;
            this.startupAbort.signal.throwIfAborted();
            this.hmc = await import(new URL('../hmc/hmc.mjs', import.meta.url).href);
            const slots = windowSlots(this.roles);
            if (this.config.start) {
                for (const [role, spec] of Object.entries(this.roles)) {
                    this.clients[role] = this.hmc.startClient(spec.client, {
                        windowed: spec.window, window: slots[role], home: this.config.hmcHome,
                        label: role + ' · ' + spec.client,
                        mods: spec.window ? [...this.config.windowMods, ...spec.mods] : spec.mods,
                        baseMods: spec.baseMods ?? this.config.mods,
                        env: { ...spec.env, STAGECRAFT_LINK: 'on' }, lang: spec.lang ?? this.config.lang,
                    });
                }
                const clients = await Promise.allSettled(Object.values(this.clients).map(client =>
                    client.ready(this.config.clientTimeout ?? (client.windowed ? 1_800_000 : 600_000)).catch(async error => {
                        await Promise.allSettled(Object.values(this.clients).map(started => started.stop({ park: false })));
                        throw error;
                    })));
                const failedClient = clients.find(result => result.status === 'rejected');
                if (failedClient) throw failedClient.reason;
            }
            this.startupAbort.signal.throwIfAborted();
            await Promise.all(Object.keys(this.roles).map(async role => {
                const link = await connect(this.clientDir(role), { timeout: this.config.clientTimeout ?? 120_000 });
                this.links[role] = link;
                if (!this.config.legacy && (link.hello.protocol ?? 1) < 2) {
                    throw new Error('Role ' + role + ' needs the updated Stagecraft link protocol 2');
                }
                this.unsubscribers.push(link.on('connection', state => this.updateObserved(role, state)));
                this.unsubscribers.push(link.on('director', event => {
                    this.handleDirector(role, event).catch(error => this.log('[director] ' + error.message));
                }));
                this.unsubscribers.push(link.on('close', () => {
                    this.report.event('client.link-closed', { role });
                    this.activeRun?.fail('Role ' + role + ' lost its Stagecraft link');
                }));
                const player = await link.player();
                this.updateObserved(role, player);
                if (!player.connected || (!this.config.legacy && this.observed[role].server !== this.roles[role].server)) {
                    await this.connectRole(role, this.roles[role].server);
                }
                this.log('[director] ' + role + ' connected to ' + this.observed[role].server);
            }));
            this.report.state = 'ready';
            this.report.write(this.snapshot());
            this.observer = setInterval(() => this.pollClients(), 500);
            this.observer.unref();
            return this;
        } catch (error) {
            this.report.event('session.start-failed', { error: error.message });
            this.report.state = 'failed';
            try { await this.stop(); }
            catch (cleanupError) { error.message += '; cleanup: ' + cleanupError.message; }
            throw error;
        }
    }

    serverFor(player) {
        for (const address of [player.remote_address, player.server_address]) {
            const match = Object.values(this.config.servers).find(server =>
                endpoint(server.host + ':' + server.port) === endpoint(address));
            if (match) return match.id;
        }
        return null;
    }

    updateObserved(role, player) {
        const previous = this.observed[role];
        const server = player.connected ? this.serverFor(player)
            ?? (this.config.legacy ? previous?.server ?? this.roles[role].server : null) : null;
        this.observed[role] = {
            ...player, role, client: this.roles[role].client, initialServer: this.roles[role].server,
            connected: !!player.connected, server,
        };
        if (!previous || previous.connected !== !!player.connected || previous.server !== server) {
            this.report.event('client.connection', { role, ...this.observed[role] });
            this.report.write(this.snapshot());
        }
    }

    async pollClients() {
        if (this.polling || this.closing) return;
        this.polling = true;
        try {
            await Promise.all(Object.values(this.servers).filter(server => server.state === 'ready').map(async server => {
                if (!await portOpen(server.config) && server.state === 'ready') {
                    server.event('failed', { error: 'Server stopped unexpectedly' });
                    this.activeRun?.fail('Server ' + server.id + ' stopped unexpectedly');
                }
            }));
            await Promise.allSettled(Object.entries(this.links).map(async ([role, link]) => {
                this.updateObserved(role, await link.player());
                const scopes = this.scopes.get(role);
                if (!scopes) return;
                for (const [operation, scope] of scopes) {
                    if ((scope.retained && this.observed[role].connected) || Date.now() >= scope.deadline) {
                        if (!this.observed[role].connected) {
                            this.activeRun?.fail('Role ' + role + ' did not reconnect before ' + operation + ' expired');
                        }
                        await this.endExpectation(role, operation);
                    }
                }
            }));
        } finally { this.polling = false; }
    }

    async commandOnServer(server, command) {
        for (const [role, link] of Object.entries(this.links)) {
            const player = await link.player().catch(() => ({ connected: false }));
            this.updateObserved(role, player);
            if (!player.connected || this.observed[role].server !== server) continue;
            try {
                await link.host('server.command', { command });
                return true;
            } catch {
                await link.command(command);
                return true;
            }
        }
        return false;
    }

    async beginExpectations(roles, operation, timeout) {
        const results = await Promise.allSettled(roles.map(async role => {
            if (!this.scopes.has(role)) this.scopes.set(role, new Map());
            this.scopes.get(role).set(operation, { deadline: Date.now() + timeout, retained: false });
            await this.links[role].request('expect_disconnect', { on: true, operation, timeout });
        }));
        const failed = results.find(result => result.status === 'rejected');
        if (failed) {
            await Promise.allSettled(roles.map(role => this.endExpectation(role, operation)));
            throw failed.reason;
        }
    }

    async endExpectation(role, operation) {
        this.scopes.get(role)?.delete(operation);
        await this.links[role]?.request('expect_disconnect', { on: false, operation }).catch(() => {});
    }

    async withExpectations(roles, timeout, work) {
        const operation = crypto.randomUUID();
        await this.beginExpectations(roles, operation, timeout);
        try { return await work(); }
        finally {
            await Promise.allSettled(roles.map(async role => {
                const player = await this.links[role].player().catch(() => ({ connected: false }));
                this.updateObserved(role, player);
                if (player.connected || this.closing) await this.endExpectation(role, operation);
                else {
                    const scope = this.scopes.get(role)?.get(operation);
                    if (scope) scope.retained = true;
                }
            }));
        }
    }

    connectRole(role, server, { timeout = 90_000, command } = {}) {
        if (!this.roles[role]) return Promise.reject(new Error('Unknown role ' + role));
        if (!this.servers[server]) return Promise.reject(new Error('Unknown server ' + server));
        const prior = this.roleOperations.get(role) ?? Promise.resolve();
        const operation = prior.catch(() => {}).then(() => this.withExpectations([role], timeout, async () => {
            const link = this.links[role];
            const target = this.config.servers[server];
            const player = await link.player();
            this.updateObserved(role, player);
            if (player.connected && this.observed[role].server === server) return { server };
            if (command) {
                if (!player.connected) throw new Error('Role ' + role + ' is offline before transfer');
                await link.command(command);
            } else {
                if (player.connected) {
                    await link.request('disconnect');
                    const leaveDeadline = Date.now() + Math.min(timeout, 20_000);
                    while ((await link.player()).connected) {
                        if (Date.now() >= leaveDeadline) throw new Error('Role ' + role + ' did not disconnect');
                        await delay(50);
                    }
                }
                await link.request('connect', { host: target.host, port: target.port });
            }
            let lastAttempt = Date.now();
            const deadline = Date.now() + timeout;
            while (Date.now() < deadline) {
                const state = await link.player().catch(() => ({ connected: false }));
                this.updateObserved(role, state);
                if (state.connected && this.observed[role].server === server) {
                    this.report.event(command ? 'client.transferred' : 'client.connected', { role, server });
                    return { server };
                }
                if (!command && !state.connected && state.connecting === false && Date.now() - lastAttempt >= 1000) {
                    await link.request('connect', { host: target.host, port: target.port });
                    lastAttempt = Date.now();
                }
                await delay(100);
            }
            throw new Error('Role ' + role + ' did not reach ' + server + '; observed ' + this.observed[role]?.server);
        }));
        this.roleOperations.set(role, operation);
        return operation;
    }

    operateServer(action, id, { reconnect = action === 'restart', timeout = 300_000 } = {}) {
        const server = this.servers[id];
        if (!server) return Promise.reject(new Error('Unknown server ' + id));
        if (!['start', 'stop', 'restart', 'crash'].includes(action)) return Promise.reject(new Error('Unknown server operation ' + action));
        const prior = this.serverOperations.get(id) ?? Promise.resolve();
        const operation = prior.catch(() => {}).then(async () => {
            const affected = [];
            for (const [role, link] of Object.entries(this.links)) {
                this.updateObserved(role, await link.player().catch(() => ({ connected: false })));
                if (this.observed[role].connected && this.observed[role].server === id) affected.push(role);
            }
            return this.withExpectations(affected, timeout, async () => {
                await server[action](this.startupAbort.signal);
                if (reconnect) await Promise.all(affected.map(role => this.connectRole(role, id, { timeout })));
                this.report.write(this.snapshot());
                return { server: id, state: server.state };
            });
        });
        this.serverOperations.set(id, operation);
        return operation;
    }

    async handleDirector(role, event) {
        const link = this.links[role];
        const reply = (phase, error, values) => link.request('director_reply', {
            request: event.id, phase, ...(error ? { error } : {}), ...(values ? { values } : {}),
        });
        try {
            if (!this.activeRun?.accepts(role, event)) throw new Error('Director request belongs to an inactive execution');
            if (event.action === 'barrier') {
                await this.activeRun.barrier(role, event);
                return;
            }
            const timeout = Number(event.timeout ?? 300_000);
            if (!Number.isFinite(timeout) || timeout <= 0 || timeout > 900_000) throw new Error('Invalid operation timeout');
            const server = event.server || this.observed[role]?.server;
            let result;
            if (event.action.startsWith('server.')) {
                result = await this.operateServer(event.action.slice(7), server, { timeout });
            } else if (event.action === 'client.connect' || event.action === 'client.transfer') {
                result = await this.connectRole(role, server, {
                    timeout, command: event.action === 'client.transfer' ? event.command : undefined,
                });
            } else if (event.action === 'client.where') {
                this.updateObserved(role, await link.player());
                result = { server: this.observed[role].server ?? '', connected: String(this.observed[role].connected) };
            } else if (event.action === 'restart_server') {
                // Protocol-1 Lua first asks permission, then stops its own server through the host.
                const affected = Object.keys(this.roles).filter(name => this.observed[name]?.server === server);
                const operation = crypto.randomUUID();
                await this.servers[server].prepareExternalStop();
                await this.beginExpectations(affected, operation, timeout);
                try {
                    await reply('ready');
                    if (!await waitPort(this.config.servers[server], false, timeout)) throw new Error('Legacy server did not stop');
                    await this.servers[server].stop();
                    await this.servers[server].start();
                    await Promise.all(affected.map(name => this.connectRole(name, server, { timeout })));
                } finally {
                    await Promise.allSettled(affected.map(name => this.endExpectation(name, operation)));
                }
                result = { server };
            } else {
                throw new Error('Unknown director action ' + event.action);
            }
            await reply('done', null, result);
        } catch (error) {
            await reply('error', error.message).catch(() => {});
            if (event.action === 'barrier') this.activeRun?.fail(error.message);
        }
    }

    async run(options = {}) {
        if (this.activeRun) throw new Error('A scenario is already running');
        if (this.closing) throw new Error('The director is closing');
        const group = new ScenarioGroup(this, options);
        this.activeRun = group;
        this.runPromise = group.run();
        try { return await this.runPromise; }
        finally { this.activeRun = null; }
    }

    stop() {
        if (this.stopPromise) return this.stopPromise;
        this.closing = true;
        this.stopPromise = this.stopNow();
        return this.stopPromise;
    }

    async stopNow() {
        clearInterval(this.observer);
        this.activeRun?.fail('Director session is closing');
        await this.runPromise?.catch(() => {});
        await Promise.allSettled([...this.serverOperations.values()]);
        const errors = [];
        const stopped = await Promise.allSettled(Object.values(this.servers)
            .filter(server => server.owned && server.config.stopOnClose).map(server => server.stop()));
        for (const result of stopped) if (result.status === 'rejected') errors.push(result.reason);
        const clients = await Promise.allSettled(Object.values(this.clients).map(client =>
            client.stop({ park: this.config.legacy })));
        for (const result of clients) if (result.status === 'rejected') errors.push(result.reason);
        for (const role of Object.keys(this.clients)) {
            this.report.copyLog(path.join(this.clientDir(role), 'console.log'), 'client-' + role + '.log');
        }
        await Promise.allSettled(Object.values(this.links).map(link => link.close()));
        for (const unsubscribe of this.unsubscribers) unsubscribe();
        this.report.state = errors.length ? 'cleanup-failed' : 'closed';
        this.report.event('session.closed', { errors: errors.map(error => error.message) });
        this.report.write({ ...this.snapshot(), closed: new Date().toISOString() });
        if (errors.length) throw new AggregateError(errors, 'Director cleanup failed: ' + errors.map(error => error.message).join('; '));
    }
}

export async function director(options) {
    return new DirectorSession(options).initialize();
}
