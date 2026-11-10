-- Ported from instanced.restart.e2e.mjs by tools/port-scenario.mjs: review the TODOs before relying on it.
-- Run: node tools/director.mjs --start --role A=HmcInstancedA run <this file>
-- Server files go through stage.host: the server needs Stagecraft with config/stagecraft-server.properties enabled=true.

local ok, failure = pcall(function()
  stage.step("ENOENT: no such file or directory, stat 'C:\\Users\\conta\\AppData\\Local\\Temp\\stagecraft-port-nn1AIK\\logs\\latest.log'", function()
    -- TODO: while recording, this step failed (failed): it checks output the recorder can't give,
    -- so what follows here and the later steps were not recorded. Finish them from the old scenario.
  end)

end)

-- Cleanup (the old scenario's finally block), also after a failure
stage.wait(3)

if not ok then error(failure, 0) end
