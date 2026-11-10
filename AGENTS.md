# Stagecraft

Fabric mod: scripts run on the client; the same jar has an optional server-side test host (off by default). Read README.md and docs/implementation-plan.md before changes. To write or run tests with Stagecraft (scenarios, director, clients, server host), use the skill in `.claude/skills/stagecraft-tests/` — keep it in sync when the Lua API, the director, the harness or the link change.
Minecraft 1.21.1, Yarn 1.21.1+build.3, Java 21, Fabric Loader 0.18.4, Fabric API 0.116.11+1.21.1.

- Preserve stable origin semantics: snapshot once; player movement must not alter placement.
- Schedule all Minecraft access on the client thread. Lua runs on one bounded worker; never sleep on the game thread.
- Lua scripts are local trusted input. Do not enable IO, OS, Java interop or arbitrary module/file loading; `require` goes through `LibraryLoader` (only `scripts/lib/`).

## Packages (`me.arycer.stagecraft`)

| Package | Contents | May import |
|---|---|---|
| (root) | `StagecraftClient`: entrypoint, wires runner, events, examples and command | anything |
| `command` | `/stagecraft` Brigadier tree | `script`, `placement` |
| `script` | `ScenarioRunner` (catalog, lifecycle), `ScriptContext` (per-run plumbing: send, waits, client thread, cancellation), `Sandbox` (Lua globals), `LibraryLoader`, `ScenarioArgs`, `Failures` | `script.api`, `chat`, `report`, `placement` |
| `script.api` | `StageModule`s that fill `stage.*`, one per area: `Core`, `Chat`, `World`, `Player`, `Instanced` (and `Gui`) | `script`, leaf packages |
| `placement` | `Origin` (stable frame, plain-decimal commands) | — |
| `chat` | `ChatLog`, `TextKeys` (messages and translation keys) | — |
| `report` | `RunReport` (JSON, JUnit), `RunLog` (`.log`) | `placement` |
| `examples` | `ExampleInstaller` | — |
| `integration.instanced` | `InstancedBridge` (reflection; optional) | — |
| `link` | `Link` (start, `link.json`), `LinkConfig` (off by default), `LinkServer` (WebSocket on 127.0.0.1 with token), `LinkProtocol` (requests and events, a `RunListener`) | `script`, `placement`, `report`, `chat` |
| `host` | Host payloads and limits (both sides) | — |
| `host.client` | `ClientHostChannel` (the `script.HostChannel` over the payloads) | `host`, `script` |
| `server` | `StagecraftServer` (common entrypoint: payloads; answers host requests when enabled), `HostConfig`, `ServerFiles` (root sandbox), `HostFunctions`. Never imports client classes (`ArchitectureTest`) | `host` |
| `window` | `WindowLayout` (tiles over a work area), `WindowPlacement` (applies `STAGECRAFT_WINDOW` on client start; label for the title mixin) | — |
| `gui` / `mixin` | `ScreenCapture` (text drawn per frame with its transformed box, only while a run holds a handle), `ScreenElements`, `ScreenKinds` (readable vanilla screen names; classes are intermediary in production), `Keys`; mixins on `DrawContext.drawText`, `GameRenderer.render`, `Screen.renderWithTooltip` | `mixin` → `gui` |

`ArchitectureTest` enforces the table; a new package needs a row there. A new `stage.*` function goes in the
module of its area (or a new `StageModule` listed in `ScenarioRunner.MODULES`), never in the runner.
Tests mirror the packages; `ScenarioRunner` runs scripts without a client in tests (`ApiV2Test`).
- Instanced is optional. Its adapter uses existing admin requests and preserves server-side permission/path validation.
- Build and tests: `gradlew.bat build collectHeadlessMods`.
- Test clients: `tools/hmc/` (moved here from Documents/mc-test-tools/mc-headless, which keeps a shim). Data in `run/hmc` (git-ignored, `STAGECRAFT_HMC_HOME`). Headless clients go through HeadlessMC; windowed ones are launched directly by `tools/hmc/launch.mjs` (HeadlessMC forces headless for offline accounts on Windows), without hmc-specifics, driven through the link. `tools/director.mjs` runs a scenario on roles `client[:window]`, tiles the windows (`window` package, `STAGECRAFT_WINDOW`/`STAGECRAFT_LABEL`) and relays signals.
- HMC verification goes through the link: `HMC_NAME=<client> node tools/verify-hmc.mjs [--gui] [--fortress]` on a client started with `STAGECRAFT_LINK=on` (requires a connected creative admin client and local test server; see README).
- Scenarios live in `scenarios/` (finished) and `scenarios/<mod>/drafts/` (from `tools/port-scenario.mjs`); the skill's `templates/` are verified against real clients — rerun them after changing the API.
- Examples are bundled; `ExampleInstaller` installs missing ones, updates copies the player never edited (SHA-256 in `.examples.properties`) and writes `<name>.lua.new` next to edited ones. `tools/port-fortress.mjs` is an offline migration helper; the emitted Lua has no Node dependency.
- Do not execute example constructors on occupied areas; tests must preflight their own area and clean temporary processes.
