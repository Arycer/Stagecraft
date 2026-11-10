# Lua API (`stage.*`, API version 2)

Scripts are trusted local Lua 5.2 (LuaJ): base, table, string and math libraries. No `io`, `os`, `load`,
`dofile`, Java interop; `require` only loads `scripts/lib/`. Limits: 10 million instructions, 15 minutes per
run. Every blocking call checks cancellation. Errors raise Lua errors with `file.lua:line`.

Times are seconds. Regexes are **Java** regexes (`(?i)` for case-insensitive, `\d`, `\(`), written as Lua
strings — use long brackets to avoid escaping: `[[Niveles: \d+ válidos]]`.

## Run context

| Name | Value |
|---|---|
| `stage.api_version` | `2` |
| `stage.run_id` | 12 hex characters, unique per run: put it in ids and paths you create |
| `stage.role` | the director's role (`'A'`, `'B'`…) or `nil` when run by hand |
| `stage.args` | table of strings from `/stagecraft run x with k=v k2="two words"` or the director's `args`; under the director also `role` and `shared` (an id common to every role of the run) |
| `stage.origin` | `{x, y, z, dimension, yaw, pitch}` captured once at start (or given): building is relative to it |

## Steps, logging, waiting

| Function | Use |
|---|---|
| `stage.step(name, fn)` | Runs `fn` as a named step: `ok name` / `FAIL name: error` in chat, stdout and reports; one JUnit case per step. Nest freely (`outer / inner`). Re-raises failures. |
| `stage.log(text)` | Progress line (chat, stdout, report, link `log` event) |
| `stage.wait(s)` | Sleep 0–120 s without blocking the game |

## Commands and chat

Every command resets the **cursor**: later expectations only see messages that arrive after it. An expectation
that matches moves the cursor past what it matched.

| Function | Use |
|---|---|
| `stage.command(text)` | Sends a command as the player (with or without `/`); 150 ms pause after |
| `stage.world(text)` | Same, wrapped in `execute in <origin dim> positioned <origin> rotated <origin>`: `~` and `^` are relative to the stable origin, not the player's current position |
| `stage.expect_chat(regex, s=15)` | First message after the cursor matching `regex`; returns its text |
| `stage.expect_all({regex, …}, s=15)` | All patterns, in any order; returns their texts |
| `stage.expect_key(key, s=15)` | First message containing translation key `key` (also inside arguments/siblings); returns `{text, key, args}` |
| `stage.poll(cmd, regex, s=30, interval=1)` | Resends `cmd` every interval until a reply matches; returns the text |
| `stage.poll_key(cmd, key, s=30, interval=1)` | Same by key; returns `{text, key, args}` |

## World and player (client side)

| Function | Use |
|---|---|
| `stage.assert_dimension(id)` | Player's current dimension |
| `stage.assert_block(dx, dy, dz, id)` | Block at origin + offset (chunk must be loaded on the client) |
| `stage.assert_empty(x1,y1,z1, x2,y2,z2)` | Box of air relative to the origin, ≤ 100 000 blocks, loaded |
| `stage.player()` | `{name, x, y, z, yaw, pitch, dimension, gamemode, health, food, flying, on_ground, selected_slot}` |
| `stage.inventory()` | `{ {slot, id, count}, … }` non-empty slots (vanilla indices) |
| `stage.fly(bool=true)` | Toggle flying (the server must allow it: creative/spectator) |

## Screens (GUI)

Coordinates are GUI-scaled (what `mouseClicked` receives). Text boxes already include the transform of moved or
scaled panels. Each read uses a frame drawn after the call; each action waits two frames.

| Function | Use |
|---|---|
| `stage.screen()` | `{open, title, kind, class, width, height, texts, widgets, hud}`; `widgets[i] = {index, type, text, x, y, w, h, active, visible, field, value}` |
| `stage.texts({hud=false})` | Drawn text `{text, x, y, w, h, cx, cy, screen}`, top-to-bottom, left-to-right |
| `stage.wait_text(regex, s=10, {hud=false})` | Waits for drawn text; returns it |
| `stage.wait_screen([regex], s=10)` | Waits for a screen (title, `kind` or class); returns `stage.screen()` |
| `stage.wait_no_screen(s=10)` | Waits until no screen is open |
| `stage.click_text(regex, {index=1, button=0, dx=0, dy=0, timeout=5, hud=false})` | Clicks the centre of the `index`-th matching text |
| `stage.click_widget(regex or index, {button=0})` | Clicks a vanilla widget by label or `widgets` index |
| `stage.click(x, y, button=0)`, `stage.scroll(x, y, amount)` | Raw mouse |
| `stage.type(text)` | Types into the focused element |
| `stage.set_field(n, text)` | Sets text field `n` (the `field` number in `widgets`) and focuses it |
| `stage.key(name)` | Press + release like the real keyboard: `enter`, `escape`, `tab`, `f8`, `t`, `ctrl+a`, `shift+tab` |
| `stage.close()` | Closes the open screen (as Escape) |

`kind` names: `GameMenuScreen`, `ChatScreen`, `OptionsScreen`, `GameOptionsScreen`, `CreativeInventoryScreen`,
`InventoryScreen`, `HandledScreen`, `SignEditScreen`, `BookEditScreen`, `DeathScreen`, `ConfirmScreen`,
`DisconnectedScreen`, `DownloadingTerrainScreen`, `TitleScreen`; anything else is the class simple name.

## Several clients

| Function | Use |
|---|---|
| `stage.signal(name)` | Counts `name` here and (via the director) on every other client |
| `stage.wait_signal(name, n=1, s=60)` | Waits until `name` was raised `n` times in total; returns the count |

Signals are cleared when a run starts; the director queues signals raised before every client started.

## Server host

| Function | Use |
|---|---|
| `stage.host_available()` | The server has Stagecraft's host channel |
| `stage.host(name, {args})` | Calls a host function; returns its JSON result as a table |

Functions and arguments: see [server-host.md](server-host.md).

## Instanced adapter

`stage.instanced_save_pool(path, json)` and `stage.instanced_save_level(id, json)` save definitions through
Instanced's admin protocol (permission-checked by Instanced). Only when Instanced is on the client.

## Libraries

`require('a.b')` loads `scripts/lib/a/b.lua` once per run and returns its value. In director runs the source is
sent inline but `require` still reads the *client's* `scripts/lib/`, so prefer self-contained scenarios or copy
the libraries into the clients' `config/stagecraft/scripts/lib/`.

## Connections and restarts

`stage.connected()` returns whether the client is in a world. `stage.disconnect()` leaves intentionally without cancelling the run. `stage.connect([host, port])` joins (by default the last server) and waits for the world. Unexpected disconnects still cancel the scenario.

`stage.restart_server(seconds=300)` asks the director to prepare, stops the server through the host, waits for the director to restart it and reconnect every role. Requires `--server-start`, host permissions, and a local test server. `stage.mods()` returns loaded client mod IDs mapped to their versions.
