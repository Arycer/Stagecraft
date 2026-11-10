-- Ported from instanced.fortress.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

stage.command("gamemode creative")
stage.command("execute in minecraft:overworld run tp @s 32 120 66")
stage.wait(3)
stage.command("execute in minecraft:overworld if blocks 8 99 24 60 111 45 8 239 24 all")
stage.wait(5)
stage.wait(3)
