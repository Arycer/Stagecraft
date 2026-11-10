# Porting old `mc-headless` scenarios

The old scenarios (`instanced.*.mjs` in `Documents/mc-test-tools/mc-headless`) drive HMC clients from Node and
touch the server's files directly. They still run (their `hmc.mjs` is a shim to `tools/hmc/hmc.mjs`), but new
tests are Lua + director.

## 1. Draft

```bash
node tools/port-scenario.mjs <old>.mjs scenarios/<mod>/drafts/<name>.lua [-- start|resume …]
```

The tool imports the old scenario with its `./hmc.mjs` replaced by a recording client and
`INSTANCED_SERVER_DIR` pointing at a temporary folder, then writes:

| Old | Lua |
|---|---|
| `step('t', fn)` + its `ok t` line | `stage.step('t', function() … end)` |
| `client.send('/cmd')` | `stage.command('cmd')` |
| `client.run('/cmd', /re/, ms)` | `stage.command('cmd')` + `stage.expect_chat([[re]], s)` |
| `client.waitFor(/re/)` | `stage.expect_chat` — several from the same `since` → `stage.expect_all` |
| `send('key x')`, `send('close')`, `send('click p x y b')` | `stage.key`, `stage.close`, `stage.click` |
| `fs.writeFileSync` / `fs.rmSync` under the server dir | `stage.host('file.write' / 'file.delete', …)` (Buffers as `base64`) |
| `sleep(ms)` | `stage.wait(s)` |
| several `startClient` | roles `A`, `B`, … with `if stage.role == 'X'` blocks and `order_N` signals between consecutive actions of different clients |
| `finally { … }` | a cleanup after `pcall` that also runs after a failure |

The header says which roles map to which old client names and how to run it.

## 2. Finish the TODOs

The recorder returns empty output, so everything that *reads* output is marked:

| TODO | What to write |
|---|---|
| `render` / `gui` read the screen | `stage.wait_text(re)`, `stage.click_text(re)`, `stage.screen().widgets`. Positions are already transformed: drop the old panel geometry maths |
| `text <id> "…"` | `stage.set_field(n, text)` (`n` = the `field` number in `stage.screen().widgets`) or click the field + `stage.type` |
| "sent N times in a polling loop" | `stage.poll(cmd, re, seconds)` with the reply the old loop waited for |
| "the old scenario read <file>" | `local text = stage.host('file.read', {path = …}).text` and the check it did |
| "this step failed while recording" | The step compared output (e.g. `if (!/passed/.test(where)) throw …`): write that check with the value `expect_chat` returns, then port the remaining steps from the old file by hand |
| "recording stopped early" | The old script needs arguments (`-- start`) or files that only exist on the real server |

Also check, in every draft:
- **Branches on output** (`if (!client.lines.some(…))`) were recorded as the path empty output took — restore
  the condition with the text `expect_chat` returned.
- **Hard-coded expectations against server state** (counts like "1 válidos") may be stale; prefer `\d+` or
  translation keys.
- **Implicit ordering between clients**: the generated `order_N` signals keep the recorded order; replace them
  with named signals at the real synchronisation points (see `scenarios/instanced/tpa.lua`).
- **Server restarts** (`restart`, `rejoin`, `dimensions`/`slots` modes): the director can't restart the server
  yet — leave those on the old script.

## 3. Verify and retire

Run the port against the same server state the old one expected, until it passes, then move it from
`drafts/` to `scenarios/<mod>/` and note it in that folder's README. Only then stop using the old script.
