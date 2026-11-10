# Director, clients and the link

## Running a scenario

```bash
node tools/director.mjs [--start] [--server host:port] [--hmc <home>] [--window-all] [--window-mods <dir>]
     --role A=<client>[:window] [--role B=<client>[:window] …]  run <file.lua | script name> [key=value …]
```

| Flag | Meaning |
|---|---|
| `--role A=HmcA` | Role `A` is client `HmcA` (its game dir is `run/hmc/client-HmcA`, its player name `HmcA`), headless |
| `--role A=HmcA:window` | Same, with a visible game window |
| `--window-all` | Every role windowed |
| `--start` | Launch the clients, wait for them and join `--server`; stop them at the end. Without it the director uses clients already running with `STAGECRAFT_LINK=on` and joins them if they aren't in a world |
| `--server` | Default `localhost:25571` |
| `--window-mods <dir>` | Extra mods for windowed roles only (repeatable) |
| `--hmc <home>` | Harness data folder (default `run/hmc` or `STAGECRAFT_HMC_HOME`) |
| `run file.lua` | The file is sent inline to every client; `run name` runs `config/stagecraft/scripts/<name>.lua` on each |
| `key=value` | Arguments for `stage.args` (every role also gets `role`) |

Exit code: 0 if every role passed. Output: `[director] A ok <step>` / `FAIL`, signals, and a summary line;
`build/director-<scenario>.json` has every role's full report.

### Headless, windowed or mixed

- **Headless** (default): HeadlessMC with patched LWJGL — no window, no real rendering, dummy assets (vanilla
  texts fall back to English keys' defaults), fast, several at once. GUI text capture still works: screens are
  rendered logically.
- **Windowed** (`:window`): a normal window, real OpenGL and assets, sound at 20 %. Launched directly by
  `tools/hmc/launch.mjs` from HeadlessMC's downloads (HeadlessMC can't combine an offline account and a window
  on Windows), **without hmc-specifics** — it is driven entirely through the Stagecraft link (connect, commands,
  quit). Heavier: 2–3 windows are fine on a normal machine.
- **Mixed**: e.g. the player you watch windowed and the others headless:
  `--role A=HmcA:window --role B=HmcB --role C=HmcC`.

Windowed clients are tiled over the primary monitor's work area in role order: 1 window = full area,
2 = side by side, 3–4 = 2×2, 5–6 = 3×2, … The title shows `[<role> · <client>]`. Placement is done by
Stagecraft from `STAGECRAFT_WINDOW=i/n` (or `x,y,w,h` for an explicit outer rectangle) and the label from
`STAGECRAFT_LABEL`; you can set them yourself when launching by hand.

### Library use (custom Node test runners)

```js
import { director } from './tools/director.mjs';
const d = await director({ roles: { A: { client: 'HmcA', window: true }, B: 'HmcB' }, start: true });
try {
  const outcome = await d.run({ file: 'scenarios/my.lua', args: { level: 'demo:crypt' }, perRole: { B: { slow: '1' } } });
  if (!outcome.passed) process.exitCode = 1;
} finally { await d.stop(); }
```

`d.links.A` is the role's link (below) for extra requests between runs.

## Launching clients by hand

```bash
node tools/hmc/hmc.mjs start HmcA                         # headless; keeps running (background it)
node tools/hmc/hmc.mjs start HmcA --window --slot 1/2 --label A
node tools/hmc/hmc.mjs send HmcA "connect localhost 25571" # hmc-specifics console (headless only)
node tools/hmc/hmc.mjs log HmcA 50
node tools/hmc/hmc.mjs stop HmcA
```

`startClient(name, { windowed, window, label, mods, env, lang })` from `tools/hmc/hmc.mjs` returns a client with
`ready()`, `connect(host, port)`, `stop()`, `lines` (console), `waitFor(regex)`; headless ones also `send()` and
`run(cmd, regex)` for hmc-specifics commands (`render`, `gui`, `click`, `key`…). Each client's console is in
`run/hmc/client-<name>/console.log`. Language: `HMC_LANG` (default `es_es`) or the `lang` option.

## The link (one client)

Enabled with `STAGECRAFT_LINK=on` (the director and windowed clients set it) or
`config/stagecraft/link.properties` (`enabled=true`, `port`). It listens on 127.0.0.1 and publishes
`config/stagecraft/link.json` (`port`, `token`, `pid`) in the client's game dir; a new token per start.

CLI: `node tools/stagecraft-link.mjs <client name | game dir> status | player | command "<cmd>" | run <script> [k=v…] | run-file <file.lua> [k=v…]`

Node: `connect(dir)` → `link.run(name, { source, args, origin })` → `{ status, error, report, logs, steps }`;
`link.on('log' | 'step' | 'signal' | 'chat' | 'finished' | 'event', fn)`; `link.waitChat(regex | key)`;
`link.command(cmd)`, `link.player()`, `link.cancel()`, `link.request(type, fields)`.

Protocol (JSON, request `{id, type, …}` → `{id, type: 'reply' | 'error'}`):

| Request | Fields → reply |
|---|---|
| `status` | → `status`, `run_id`, `scenarios` |
| `run` | `name`, `source?`, `args?`, `origin?` (`dimension, x, y, z, yaw, pitch`; missing from the player) → `run_id` |
| `cancel` | stops the current run |
| `command` | `command` (sent as the player) |
| `player` | → `connected`, `name`, `dimension`, `x`, `y`, `z`, `yaw`, `pitch` |
| `signal` | `name` (a signal from another client) |
| `connect` | `host`, `port` (joins; poll `player.connected`) |
| `disconnect`, `quit` | leave the world; close the game |

Events to every connection `{type: 'event', event, run_id, …}`: `started` (scenario), `log` (text), `step`
(name, status, error), `signal` (name), `finished` (status, error, report), `chat` (text, keys: `[{key, args}]`).
