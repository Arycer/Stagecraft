# Escenarios de Instanced en Stagecraft

Port de los escenarios `instanced.*.mjs` (en `Documents/mc-test-tools/mc-headless`) a Lua de Stagecraft, ejecutados por el director
(`tools/director.mjs`, desde la raíz de Stagecraft). Los ficheros del servidor pasan por `stage.host`, así que el servidor de
desarrollo de Instanced necesita el jar de Stagecraft en `fabric-server/run/mods/` y
`fabric-server/run/config/stagecraft-server.properties` con `enabled=true`. Los clientes se lanzan con
`--start` (o abiertos con `STAGECRAFT_LINK=on`) y `HMC_MODS` incluye los headless-mods de Stagecraft y de Instanced. Añade `:window` a un rol para verlo.

```bash
HMC_MODS="<Stagecraft>/build/headless-mods;<Instanced>/fabric-client/build/headless-mods" \
node tools/director.mjs --start --role A=HmcInstancedA:window --role B=HmcInstancedB run scenarios/instanced/tpa.lua
```

## Terminados y comprobados

Se han ejecutado de principio a fin contra el servidor de desarrollo (10/11/2026) y pueden sustituir al original.

| Lua | Original | Clientes | Notas |
|---|---|---|---|
| `instanced/config.lua` | `instanced.config.e2e.mjs` | A | Borrador sin cambios salvo el recuento del reload: el original esperaba «1 válidos» y el servidor ya tiene más niveles (el original también fallaría). |
| `instanced/tpa.lua` | `instanced.tpa.e2e.mjs` | A, B | Terminado a mano: ramas sobre la salida (`sethome`, «Test passed») y los pasos 4–5, que el grabador no llega a ver. |

## Migración en curso

También comprobados: `smoke` (desconexión y reconexión), `restart` (reinicio real), `samples`, `showcase`, `demo`, `teleports` (cinco rondas), `return` (tickets y límite) y `waiting` (instancia en espera). Los borradores de estos escenarios ya están retirados.

Los ports nuevos restantes están en la raíz, pero conservan su borrador hasta aprobar su ejecución real. La ejecución final con ventanas visibles sigue pendiente.
