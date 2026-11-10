# Server host (`stage.host`)

The Stagecraft jar on the **server** answers host requests from scripts. It exists so tests can prepare and
check server-side state (config files, fixtures, NBT, console-only commands) without the test runner touching
the server's disk — so the same scenario works against a remote test server.

## Enabling it

`config/stagecraft-server.properties` (created disabled on first start):

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Answer host requests at all. Keep it off outside local test servers. |
| `permission_level` | `2` | Minimum permission level of the player whose script calls the host |
| `root` | `.` | Files root, relative to the server directory. Paths are relative to it; absolute paths, `..` and links that leave it are rejected |
| `allow_write` | `true` | `false` makes the host read-only |

The server log says `[Stagecraft] Test host ENABLED (root …)` when it is on. Without the jar on the server,
`stage.host_available()` is false and `stage.host` fails with "The server has no Stagecraft host".

## Functions

All take a table of named arguments and return a table. Paths use `/`.

| Name | Arguments | Result |
|---|---|---|
| `host.info` | — | `{root, allow_write, permission_level, functions}` |
| `file.exists` | `path` | `{exists, dir}` |
| `file.read` | `path` (≤ 4 MB, UTF-8) | `{text, size}` |
| `file.list` | `path` (default root) | `{entries = {{name, dir, size}, …}}` |
| `file.write` | `path`, `text` **or** `base64` | `{size}` — creates parent folders; large contents are sent in chunks automatically |
| `file.append` | `path`, `text` or `base64` | `{size}` |
| `file.delete` | `path`, `recursive=false` | `{deleted}` (number of files/folders) |
| `nbt.read` | `path` (compressed or plain NBT) | `{json, snbt}` |
| `server.command` | `command` | `{output = {lines…}}` — runs as the console (permission 4), output captured |
| `server.players` | — | `{players = {{name, dimension, x, y, z}, …}}` |
| `world.block` | `dimension`, `x`, `y`, `z` | `{id, state}` — server truth, loads the chunk |

## Patterns

```lua
-- Fixture in, run, clean up (also after a failure).
local dir = 'config/mymod/levels/test_' .. stage.run_id
local ok, failure = pcall(function()
  stage.host('file.write', {path = dir .. '/crypt.json', text = [[{"name": "Cripta"}]]})
  stage.command('mymod reload')
  stage.expect_key('chat.mymod.reloaded', 20)
end)
stage.host('file.delete', {path = dir, recursive = true})
if not ok then error(failure, 0) end
```

```lua
-- Binary fixture (an .nbt structure): keep it as base64 in the scenario or a library.
stage.host('file.write', {path = 'config/mymod/schematics/t.nbt', base64 = 'H4sIAAAAAAAA…'})
```

```lua
-- Assert server-side state a client can't see.
local level = stage.host('file.read', {path = 'config/mymod/levels/x.json'}).text
assert(level:find('"name": "Cripta"'), 'level not saved')
local out = stage.host('server.command', {command = 'mymod slot list x'}).output
assert(table.concat(out, '\n'):find('ready 3'), 'slots not ready')
```

Limits: a request (arguments) is at most ~30 000 bytes of JSON — fine for anything but `file.write`, which is
chunked by the client. Results up to 262 144 characters. Calls time out after 30 s.
