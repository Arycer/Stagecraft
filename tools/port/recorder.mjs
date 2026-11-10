// Stand-in for mc-headless/hmc.mjs while porting: records what the scenario asks each client to do.
const state = globalThis.__stagecraftPort;

export function startClient(name) {
    const index = state.clients.push(name) - 1;
    const record = op => state.ops.push({ client: index, ...op });
    return {
        name,
        lines: [],
        process: { on() {} },
        async ready() { record({ type: 'meta', text: 'ready' }); },
        async connect(host, port) { record({ type: 'meta', text: `connect ${host}:${port}` }); },
        send(text) { record({ type: 'send', text }); },
        async run(command, pattern, timeout) { record({ type: 'run', text: command, pattern, timeout }); return ''; },
        async waitFor(pattern, options = {}) { record({ type: 'wait', pattern, timeout: options.timeout, since: options.since }); return ''; },
        stop() { record({ type: 'meta', text: 'stop' }); },
    };
}
