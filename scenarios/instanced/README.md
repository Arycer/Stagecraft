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

## Borradores (`instanced/drafts/`)

Generados con `node tools/port-scenario.mjs <escenario>.mjs <salida>.lua [-- modo]`. «Pasos» son los
que se llegaron a grabar; cada `TODO` señala algo que depende de la salida (pantallas leídas con `render`/`gui`,
coordenadas calculadas, bucles de sondeo, ficheros leídos) o un paso que falló durante la grabación y cortó el resto.
Para terminar uno: revisar los TODO contra el original, ejecutarlo con el director y moverlo a `instanced/`.

| Borrador | Pasos | TODO |
|---|---:|---:|
| `admin.lua` | 1 | 3 |
| `capture.lua` | 2 | 2 |
| `convert.lua` | 1 | 16 |
| `demo.lua` | — | 0 |
| `dimensions-create.lua` / `dimensions-restored.lua` | 2 / 3 | 1 / 0 |
| `editor.lua` | 2 | 2 |
| `fortress.lua` | — | 0 |
| `hud.lua` | 1 | 1 |
| `instances.lua` | 1 | 2 |
| `lang.lua` | 12 | 169 |
| `numismatic-parties.lua` (`parties.e2e.mjs`) | 6 | 1 |
| `parties.lua` | 1 | 2 |
| `place.lua` | 2 | 16 |
| `postprocess.lua` | 1 | 17 |
| `rejoin.lua` | 0 | 1 |
| `restart.lua` | 1 | 1 |
| `return.lua` | 1 | 2 |
| `samples.lua` | — | 0 |
| `showcase.lua` | — | 0 |
| `slots-start.lua` / `slots-resume.lua` | 2 / 3 | 2 / 1 |
| `smoke.lua` | 3 | 1 |
| `teleports.lua` | 1 | 2 |
| `tracker.lua` | 1 | 16 |
| `waiting.lua` | 1 | 2 |

Los de GUI (`admin`, `capture`, `convert`, `editor`, `place`, `postprocess`, `tracker`, `lang`) traducen
`click p x y` y `key`, pero sus lecturas de `render`/`gui` quedan como TODO: se terminan con `stage.wait_text`,
`stage.click_text` y `stage.screen()`, que ya dan las coordenadas transformadas (sin la geometría de paneles
del original). `restart`, `rejoin` y `dimensions`/`slots` (modos que reinician el servidor entre medias)
necesitan que el director reinicie el servidor, que aún no está implementado.
