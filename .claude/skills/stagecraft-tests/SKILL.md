---
name: stagecraft-tests
description: Write, run and debug in-game tests for Minecraft 1.21.1 Fabric mods with Stagecraft — Lua scenarios run by real clients (headless HeadlessMC clients, visible windowed clients, or a mix), orchestrated by the Node director, with server files and state through the Stagecraft server host. Use when asked to test a mod in a real client, add an end-to-end or multiplayer scenario, test a GUI, check chat/commands/teleports/dimensions in game, port an old mc-headless *.mjs scenario, or watch a test run in windows.
---

# Stagecraft tests

Stagecraft runs **Lua scenarios inside real Minecraft clients**. Use it for what server GameTests and unit
tests can't see: client mods, screens, chat as a player sees it, portals and dimension changes, several
players interacting. Pure server logic belongs in GameTests (faster, deterministic); use Stagecraft for the
end-to-end path.

## How the pieces fit

```
 director.mjs (Node)  ──WebSocket link──▶  client A (Stagecraft runs the Lua)  ──commands/packets──▶  server
   roles, signals,                          client B …                                                 Stagecraft
   start/stop clients                       headless (HeadlessMC) or windowed                          host (files,
                                                                                                        state)
```

- **Scenario**: one `.lua` file. The same file runs on every client; `stage.role` says which one it is.
- **Client**: a real Fabric client launched by `tools/hmc/hmc.mjs` — headless (no window, fast, no real
  rendering) or windowed (a normal game window you can watch; real OpenGL, real assets).
- **Link**: WebSocket on 127.0.0.1 with a token (`config/stagecraft/link.json` in the client's game dir). The
  director starts runs, receives steps/logs/chat, relays signals.
- **Server host** (optional): the same Stagecraft jar on the server answers `stage.host(...)` — read/write
  files under its root, run console commands, list players, read blocks and NBT. Off unless enabled.

## Workflow

1. **Prepare once** (see [references/setup.md](references/setup.md)): `node tools/hmc/setup.mjs`; build
   Stagecraft (`gradlew build collectHeadlessMods`) and the mod under test's client jars; put the
   Stagecraft jar on the test server and enable the host if the scenario touches server files or state.
2. **Write the scenario** from a template in [templates/](templates/) — `single-client.lua`,
   `multi-client.lua` or `gui.lua`. Every check goes in a `stage.step`. Full API:
   [references/lua-api.md](references/lua-api.md).
3. **Run it with the director**:
   ```bash
   HMC_MODS="<Stagecraft>/build/headless-mods;<mod>/build/headless-mods" \
   node tools/director.mjs --start --role A=HmcA run scenarios/my-test.lua
   ```
   Add `:window` to a role to watch it (`--role A=HmcA:window --role B=HmcB`), or `--window-all`. Windowed
   clients are tiled over the primary monitor. Details: [references/director.md](references/director.md).
4. **Read the result**: exit code 0 only if every role passed; per-step `ok`/`FAIL` lines on stdout;
   `build/director-<scenario>.json` (every role's report); each client's `run/hmc/client-<name>/config/stagecraft/reports/`
   (`.log`, `.json`, JUnit `.xml`).
5. **Fix and repeat.** Failures carry `file.lua:line` and the message. See
   [references/troubleshooting.md](references/troubleshooting.md) before guessing.

## Rules that prevent most failures

- **Match by translation key when the server sends translatable text** (`stage.expect_key('chat.mymod.done')`):
  it works in any client language. Use `expect_chat` regexes for vanilla/literal text, and cover both
  languages when unsure (`'Teleported|teletransportado'`). HMC clients default to `es_es` (`HMC_LANG`).
- **Expectations only see messages after the last command** (and consume what they match). To accept several
  lines in any order use `stage.expect_all({...})`. For state that settles over time use `stage.poll(cmd, re, s)`.
- **Never assume timing between clients**: use `stage.signal(name)` / `stage.wait_signal(name, n)`. A barrier for
  N clients: everyone signals `x` and waits for `x` N times.
- **Clean up even on failure**: wrap the steps in `pcall`, clean up, then rethrow (templates do this). Use
  `stage.run_id` in every id and path you create so runs never collide.
- **Don't build on occupied ground**: `stage.assert_empty(...)` before placing, relative to `stage.origin`.
- **Screens**: vanilla screens are matched by `kind` (`GameMenuScreen`, `ChatScreen`…) because production class
  names are `class_xxx`; mod screens keep their class name. Custom-drawn GUIs (GUI Library) have no widgets —
  drive them by text: `stage.wait_text`, `stage.click_text`, coordinates from `stage.texts()`.
- **Server files only through `stage.host`** — never assume the test can reach the server's disk. Requests are
  capped at ~32 KB (large writes are chunked for you); binary files go as `base64`.
- **One scenario per client at a time.** The director waits for every role; a role that fails doesn't stop the
  others by itself — make the others time out on `wait_signal` rather than hang.
- **Ids shared between roles come from `stage.args.shared`** (same on every client of a director run);
  `stage.run_id` is per client.

## Porting an old `mc-headless` scenario

`node tools/port-scenario.mjs old.mjs out.lua [-- mode]` produces a draft: steps, commands, chat waits, GUI keys
and clicks, server files via the host, roles with ordering signals, and a cleanup that runs after failures.
Everything that depended on reading output is a `TODO`. Finish those by hand (see
[references/porting.md](references/porting.md)), run it, and only then retire the old script.

## Where things are

| Path | What |
|---|---|
| `tools/director.mjs` | Runs a scenario on N roles; starts/tiles clients; relays signals |
| `tools/hmc/hmc.mjs`, `tools/hmc/setup.mjs` | Launch headless/windowed clients; one-time download |
| `tools/stagecraft-link.mjs` | Node client + CLI for one client's link (`status`, `run`, `run-file`, `player`, `command`) |
| `tools/verify-hmc.mjs` | Self-test of Stagecraft on a connected client |
| `tools/port-scenario.mjs` | Old `*.mjs` scenario → Lua draft |
| `scenarios/` | Finished scenarios (`duo.lua`; `instanced/` for the Instanced mod, with drafts) |
| `run/hmc/` | Launcher, shared `.minecraft`, client game dirs (git-ignored) |
| `examples/` | Single-client examples bundled into the mod (`platform`, `smoke`, `gui`, `fortress`) |
