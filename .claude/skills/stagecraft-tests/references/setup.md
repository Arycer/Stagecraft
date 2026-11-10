# One-time setup

Commands are run from the Stagecraft repository root. On Windows Git Bash, prefix commands whose arguments
start with `/` with `MSYS_NO_PATHCONV=1` (otherwise `/stagecraft run x` becomes `C:/Program Files/Git/stagecraft…`),
and run Gradle with `env -u NoDefaultCurrentDirectoryInExePath ./gradlew …` (or `gradlew.bat`).

## 1. Client harness

```bash
node tools/hmc/setup.mjs
```

Downloads the pinned HeadlessMC launcher and hmc-specifics into `run/hmc/` (override with
`STAGECRAFT_HMC_HOME`). The first headless launch downloads Minecraft 1.21.1 + Fabric into
`run/hmc/.minecraft` (~150 MB with dummy assets). The first windowed launch downloads the real assets into
`run/hmc/assets` (~107 MB without sounds; `STAGECRAFT_HMC_SOUNDS=1` adds ~720 MB of sounds). Windowed clients
reuse the libraries a headless launch downloaded, so **launch one headless client once before the first
windowed one**.

Requirements: Java 21 on PATH (`STAGECRAFT_JAVA` to use another), Node 22+ (global `WebSocket`).

## 2. Mods for the clients

Each client loads: `run/hmc/mods/*.jar` (hmc-specifics; headless only) + every jar in the folders of
`HMC_MODS` (separated by `;` on Windows, `:` elsewhere) + per-role extra folders.

```bash
gradlew build collectHeadlessMods                     # Stagecraft → build/headless-mods (Stagecraft + Fabric API)
# in the mod under test, a task that collects its client jar and dependencies, e.g. Instanced:
./gradlew :fabric-client:collectHeadlessMods          # → fabric-client/build/headless-mods
export HMC_MODS="<Stagecraft>/build/headless-mods;<mod>/fabric-client/build/headless-mods"
```

Stagecraft must be in every client the director drives (it is the link). Mods that need a real OpenGL context
(Veil, shader mods) must not go to headless clients: give them to windowed roles only with
`--window-mods <dir>`.

## 3. The test server

Any server the clients can join (the director connects them to `--server host:port`, default
`localhost:25571`). Offline mode (`online-mode=false`) — clients use offline accounts named after the client.
Make the test players operators if scenarios run admin commands (`ops.json`, or `/op` from the console).

### Server host (only for scenarios that use `stage.host`)

1. Put the same `build/libs/stagecraft-<version>.jar` in the server's `mods/` (Fabric loader remaps it in a dev
   environment too, e.g. `fabric-server/run/mods/` of a Loom project).
2. Start the server once; it writes `config/stagecraft-server.properties` with `enabled=false`.
3. Set `enabled=true` (and `permission_level`, `root`, `allow_write` as needed — see
   [server-host.md](server-host.md)) and restart. The log shows `[Stagecraft] Test host ENABLED …`.

After rebuilding Stagecraft, copy the jar to the server again and restart it: an old jar on the server is a
common cause of `Missing argument` or unknown host function errors.

## 4. Check it works

```bash
# one headless client, the bundled self-test over the link (needs the client to be OP and creative)
node tools/hmc/hmc.mjs start HmcA &                    # or let the director start it
node tools/director.mjs --start --role A=HmcA run examples/smoke.lua
node tools/director.mjs --start --role A=HmcA:window run examples/gui.lua     # watch it
```
