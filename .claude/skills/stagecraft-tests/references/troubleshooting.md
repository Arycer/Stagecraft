# Troubleshooting

Look first at: the director's `FAIL` line (`file.lua:line` + message), the client's
`run/hmc/client-<name>/console.log`, and its report `config/stagecraft/reports/<date>-<scenario>.log` (every
command, step and log line with timestamps).

| Symptom | Cause / fix |
|---|---|
| `No chat matching …` but the message is in the console | It arrived **before** the last command (cursor), was already consumed by an earlier expectation, or differs in language/format. Expect it right after the command that causes it; use `expect_all` for several lines; match by key (`expect_key`) or both languages. Vanilla texts in headless clients are English fallbacks (dummy assets); windowed clients have real `es_es`. |
| `No message with key …` | The server sends literal text there (not translatable). Use `expect_chat`. |
| `No frame rendered in 5 s` | The client isn't rendering (minimised window, client still loading). Wait for `stage.wait_screen()` or keep the window visible. |
| `No screen matching GameMenu` | Match `kind` names (`GameMenuScreen`), not class names: production classes are `class_xxx`. |
| `No text matching … to click` | GUI Library/custom panels have no widgets; their text appears only once drawn. Wait for it (`wait_text`) before clicking; check spelling and language; use `index` for repeated labels. |
| `The server has no Stagecraft host` | Jar missing on the server. `…host is disabled` → `enabled=true` in `config/stagecraft-server.properties` and restart. `needs permission level N` → op the test player. |
| `host file.write failed: Missing argument: text` (with `base64`) / unknown function | The server has an older Stagecraft jar: copy the new one and restart the server. |
| `Host arguments exceed 30000 bytes` | One request too big (other than `file.write`, which is chunked). Split it. |
| `<role> did not join localhost:25571` | Server not running, wrong `--server`, whitelist, or the previous run left the player connected (kick it / wait). |
| `No Stagecraft link in …/link.json` | The client wasn't started with `STAGECRAFT_LINK=on`, Stagecraft isn't in its mods, or it is still loading (first windowed start downloads assets). |
| Windowed client runs headless (no window) | It went through HeadlessMC: windowed clients must be started with `--window` / `windowed: true` (direct launch). |
| `Missing library …` when starting a window | Launch one headless client first: windowed launches reuse HeadlessMC's downloads. |
| Windows overlap, are cut off or land on another monitor | Check with `tools/hmc/windows.ps1`. Placement uses the **primary** monitor's work area; the client log says `Window placed in … content at x,y`. An old Stagecraft jar in the client (before the size-then-position fix) leaves windows centred. |
| Veil/shader crash in headless clients | OpenGL-only mods go to windowed roles only (`--window-mods`). |
| `/stagecraft run x` becomes `C:/Program Files/Git/…` | Git Bash path conversion: `MSYS_NO_PATHCONV=1`. |
| `Instruction budget exceeded` | An infinite or very long Lua loop (10 M instructions). Waiting calls don't count — use them instead of busy loops. |
| A run hangs on `wait_signal` | Another role failed or never raised the signal. Give every `wait_signal` a timeout shorter than the run; check the other role's `FAIL`. |
| `A scenario is already running` | A previous run on that client is still going: `link.cancel()` / `/stagecraft cancel`, or wait. |
| Leftover state breaks the next run | Clean up after `pcall`, use `stage.run_id` in names, and reset what the scenario changed (gamemode, position, files). |
