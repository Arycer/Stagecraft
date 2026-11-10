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
`STAGECRAFT_LABEL`; you can set them yourself when launching by hand. To check where the windows really are:
`powershell -NoProfile -ExecutionPolicy Bypass -File tools/hmc/windows.ps1` (monitors' work areas and each Minecraft
window's outer rectangle).

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

## Suites, libraries and server restarts

`run a.lua b.lua ...` runs scenarios sequentially on the same roles; exit code 0 requires all to pass. Group suites by their required roles. A `lib/` next to the scenario (or its parent for drafts) is copied to each client's `scripts/lib/` before running.

`--server-start "gradlew.bat :fabric-server:runServer --console=plain" --server-cwd <Instanced>` enables `stage.restart_server()`. `--server-log <path>` selects the new process log. `--server-dir <path>` selects the server data directory (default `<server-cwd>/fabric-server/run`). The server must already be up at the first connection. The director waits for the port to close, then for process exit. On Windows a hung KnotServer is terminated only after its own new log confirms **All dimensions are saved**, with the captured listening PID and creation timestamp checked again. It never kills all Java processes. The server is started hidden and every role reconnects before the script resumes.

## Languages, fixtures and the Instanced suite

`--lang en_us` sets the clients' language (default `es_es`). Every scenario gets `stage.args.client_A`, `client_B`, etc., so commands need not hard-code account names. A `fixtures/` next to the scenario is copied into each client's `config/stagecraft/fixtures`; `stage.args.fixture_dir` is its absolute path for testing local-file upload UIs. Libraries and fixtures are test resources, not enabled Lua filesystem access.

`node tools/verify-instanced.mjs --party-mods build/party-client-mods` runs all 26 migrated scenarios with visible windows, grouped into Spanish single-player, English, two-player and four-player suites. Add `--headless` during development; `--group single|english|duo|party` or `--only a,b` selects a subset (preparation dependencies must already exist). It fails at the first broken scenario, closes its clients, and saves every result plus `summary.json` under a dated `build/instanced-suite-*/` directory. The test server must already be running.

Test viewports use GUI scale 1 headless and 2 windowed. Text can still wrap or truncate: check the visible prefix and verify the actual saved data, or inspect fields by their label (`widget.text`), instead of expecting one unbroken long path. Headless launchers have separate per-client working directories for patched jars/natives while sharing the Minecraft downloads.
