// Loader hook for port-scenario.mjs: the old scenario's `./hmc.mjs` becomes the recording client.
const RECORDER = new URL('./recorder.mjs', import.meta.url).href;

export async function resolve(specifier, context, nextResolve) {
    if (/(^|\/)hmc\.mjs$/.test(specifier) && context.parentURL && !context.parentURL.startsWith(new URL('.', import.meta.url).href)) {
        return { url: RECORDER, shortCircuit: true };
    }
    return nextResolve(specifier, context);
}
