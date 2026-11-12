import fs from 'node:fs';
import path from 'node:path';

function expand(value) {
    if (typeof value === 'string') {
        return value.replace(/\$\{([A-Z_][A-Z0-9_]*)\}/g, (_, name) => {
            if (process.env[name] === undefined) throw new Error('Missing environment variable ' + name);
            return process.env[name];
        });
    }
    if (Array.isArray(value)) return value.map(expand);
    if (value && typeof value === 'object') {
        return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, expand(entry)]));
    }
    return value;
}

export function loadConfiguration(file) {
    const absolute = path.resolve(file);
    const options = expand(JSON.parse(fs.readFileSync(absolute, 'utf8')));
    return normalizeConfiguration(options, path.dirname(absolute));
}

export function parseRole(spec) {
    if (spec && typeof spec === 'object') {
        return { ...spec, window: !!spec.window, mods: spec.mods ?? [] };
    }
    const [client, mode = 'headless'] = String(spec).split(':');
    if (!['headless', 'window', 'windowed'].includes(mode)) throw new Error('Unknown client mode ' + mode);
    return { client, window: mode !== 'headless', mods: [] };
}

function port(value, label) {
    const number = Number(value);
    if (!Number.isInteger(number) || number < 1 || number > 65535) throw new Error('Invalid port for ' + label);
    return number;
}

function identifier(value, label) {
    if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error('Invalid ' + label + ' ' + value);
}

function timeout(value, fallback, label) {
    const number = value ?? fallback;
    if (!Number.isFinite(number) || number <= 0) throw new Error('Invalid timeout for ' + label);
    return number;
}

/** Converts legacy single-server options and JSON topologies into the same session model. */
export function normalizeConfiguration(options, base = process.cwd()) {
    const resolve = value => value === undefined ? undefined : path.resolve(base, value);
    const legacy = !options.servers;
    const legacyServer = {
        ...options.server,
        ...options.serverControl,
        command: options.serverControl?.start,
        runDir: options.serverControl?.dir ?? (options.serverControl?.start
            ? path.join(options.serverControl.cwd ?? '.', 'fabric-server/run') : undefined),
        managed: false,
        stopOnClose: false,
    };
    const inputServers = options.servers ?? { default: legacyServer };
    if (Object.keys(inputServers).length === 0) throw new Error('At least one server is required');
    const endpoints = new Set();
    const servers = Object.fromEntries(Object.entries(inputServers).map(([id, value]) => {
        identifier(id, 'server id');
        const host = value.host ?? '127.0.0.1';
        const serverPort = port(value.port ?? (legacy ? 25571 : 25565), id);
        const endpoint = host.replace(/^localhost$/, '127.0.0.1') + ':' + serverPort;
        if (endpoints.has(endpoint)) throw new Error('Duplicate server endpoint ' + endpoint);
        endpoints.add(endpoint);
        const cwd = resolve(value.cwd ?? '.');
        const runDir = value.runDir ? resolve(value.runDir) : undefined;
        const command = value.command ?? value.start;
        const managed = value.managed ?? !!command;
        if (managed && !command) throw new Error('Managed server ' + id + ' needs a command');
        if (command && typeof command !== 'string') throw new Error('Invalid command for ' + id);
        if (value.args && (!Array.isArray(value.args) || value.args.some(arg => typeof arg !== 'string'))) {
            throw new Error('Server arguments must be strings for ' + id);
        }
        if (managed && !runDir && !value.latestLog && !value.log) {
            throw new Error('Managed server ' + id + ' needs runDir or a readiness log');
        }
        return [id, {
            ...value, id, host, port: serverPort, command, managed, cwd, runDir,
            args: value.args ?? [],
            shell: value.shell ?? (!!value.start || (legacy && !!command)),
            stopOnClose: value.stopOnClose ?? managed,
            startupTimeout: timeout(value.startupTimeout, 300_000, id),
            stopTimeout: timeout(value.stopTimeout, 120_000, id),
            readyPattern: value.readyPattern === null ? null : value.readyPattern ?? 'Done \\(',
            savedPattern: value.savedPattern === null ? null : value.savedPattern ?? 'All dimensions are saved',
            latestLog: value.latestLog ? resolve(value.latestLog)
                : runDir ? path.join(runDir, 'logs', 'latest.log') : resolve(value.log),
            log: resolve(value.log),
            rcon: value.rcon ? {
                ...value.rcon, host: value.rcon.host ?? host,
                port: port(value.rcon.port, id + ' RCON'),
            } : undefined,
        }];
    }));
    const firstServer = Object.keys(servers)[0];
    const clients = new Set();
    const roles = Object.fromEntries(Object.entries(options.roles ?? {}).map(([id, spec]) => {
        identifier(id, 'role');
        const role = parseRole(spec);
        if (typeof role.client !== 'string' || !/^[A-Za-z0-9_]{1,16}$/.test(role.client)) {
            throw new Error('Invalid Minecraft client name for ' + id);
        }
        const key = role.client.toLowerCase();
        if (clients.has(key)) throw new Error('Duplicate client ' + role.client);
        clients.add(key);
        const server = role.server ?? firstServer;
        if (!servers[server]) throw new Error('Unknown server ' + server + ' for role ' + id);
        return [id, {
            ...role, id, server,
            mods: role.mods.map(resolve),
            baseMods: role.baseMods?.map(resolve),
        }];
    }));
    if (Object.keys(roles).length === 0) throw new Error('At least one role is required');
    const services = Object.fromEntries(Object.entries(options.services ?? {}).map(([id, service]) => {
        identifier(id, 'service');
        return [id, { ...service, host: service.host ?? '127.0.0.1',
            port: port(service.port, id), timeout: timeout(service.timeout, 30_000, id) }];
    }));
    return {
        ...options, legacy, servers, roles, services, normalized: true,
        start: options.start ?? !legacy,
        hmcHome: resolve(options.hmcHome),
        mods: options.mods?.map(resolve),
        windowMods: (options.windowMods ?? []).map(resolve),
        reportDir: resolve(options.reportDir),
        scenarios: (options.scenarios ?? []).map(scenario => {
            const entry = typeof scenario === 'string' ? { file: scenario } : scenario;
            return { ...entry, file: entry.file ? resolve(entry.file) : undefined };
        }),
    };
}

export function windowSlots(roles) {
    const names = Object.entries(roles).filter(([, role]) => role.window).map(([name]) => name);
    return Object.fromEntries(names.map((name, index) => [name, { slot: index + 1, count: names.length }]));
}
