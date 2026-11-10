# Stagecraft

Standalone client-only Fabric mod. Read README.md and docs/implementation-plan.md before changes.
Minecraft 1.21.1, Yarn 1.21.1+build.3, Java 21, Fabric Loader 0.18.4, Fabric API 0.116.11+1.21.1.

- Preserve stable origin semantics: snapshot once; player movement must not alter placement.
- Schedule all Minecraft access on the client thread. Lua runs on one bounded worker; never sleep on the game thread.
- Lua scripts are local trusted input. Do not enable IO, OS, Java interop or arbitrary module/file loading; `require` goes through `LibraryLoader` (only `scripts/lib/`).
- Pure, testable pieces live outside `ScenarioRunner`: `ChatLog`/`TextKeys` (messages and translation keys), `ScenarioArgs`, `LibraryLoader`, `RunReport` (JSON and JUnit). `ScenarioRunner` runs scripts without a client in tests (`ApiV2Test`).
- Instanced is optional. Its adapter uses existing admin requests and preserves server-side permission/path validation.
- Build and tests: `gradlew.bat build collectHeadlessMods`.
- HMC verification: `HMC_HOME=... node tools/verify-hmc.mjs --fortress` (requires a connected creative admin client and local test server; see README).
- Examples are bundled; `ExampleInstaller` installs missing ones, updates copies the player never edited (SHA-256 in `.examples.properties`) and writes `<name>.lua.new` next to edited ones. `tools/port-fortress.mjs` is an offline migration helper; the emitted Lua has no Node dependency.
- Do not execute example constructors on occupied areas; tests must preflight their own area and clean temporary processes.
