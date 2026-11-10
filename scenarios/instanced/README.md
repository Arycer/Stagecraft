# Escenarios de Instanced en Stagecraft

26 escenarios migrados de `Documents/mc-test-tools/mc-headless` a Lua, con clientes reales. Verificados con ventanas visibles (26 escenarios, 32 ejecuciones de cliente, 138 pasos aprobados): [handoff-port-scenarios.md](../../docs/handoff-port-scenarios.md). El escenario adicional `marker-portals.lua` pertenece a la implementación de los portales internos.

## Ejecutar la suite

Desde la raíz de Stagecraft, con el servidor de desarrollo de Instanced ya arrancado en `localhost:25571`, Stagecraft instalado en él y `config/stagecraft-server.properties` con `enabled=true`. `HmcInstancedA`–`D` deben ser OP. Usar un servidor de pruebas: los constructores `samples`, `demo` y `showcase` preparan los fixtures `demo:*` y el área de muestra junto al spawn; se conservan para los siguientes escenarios. Fortress construye en una zona vacía nueva y conserva su ID único para inspección.

```powershell
.\gradlew.bat build collectHeadlessMods
$env:HMC_MODS='C:/Users/conta/IdeaProjects/Stagecraft/build/headless-mods;C:/Users/conta/IdeaProjects/Instanced/fabric-client/build/headless-mods'
node tools/verify-instanced.mjs --party-mods build/party-client-mods
```

La suite abre ventanas visibles por defecto y las coloca en mosaico. Ejecuta los escenarios de un jugador, otro cliente en inglés, los de dos jugadores y los de cuatro. Hace tres reinicios reales del servidor mediante el director; cierra sus clientes al terminar. No inicia el primer servidor: para los reinicios usa `gradlew.bat :fabric-server:runServer` en el proyecto hermano Instanced (`--server-cwd` permite cambiarlo).

`build/party-client-mods` debe contener jars de **la misma compilación que el servidor**: Numismatic Parties, Numismatic Tab API y Cardinal Components base/entity/scoreboard. Se obtienen con `:numismatic-parties:build :numismatic-parties:collectHeadlessMods` en Numismatic. Copiar solo esos cinco jars; el director ya recibe Fabric API y GUI Library por `HMC_MODS`. No incluir otra Fabric API ni Phone. Tras cambiar el protocolo de un mod, reiniciar ambos lados.

Opciones: `--headless` para desarrollar sin ventanas; `--group single|english|duo|party` por bloque; `--only capture,place` por nombres. Los subconjuntos necesitan sus fixtures previos: `showcase` antes de `hud` no es necesario, pero sí antes de `tpa`, `restart`, `rejoin`, `tracker` y `postprocess`; `samples` antes de `editor`.

Cada ejecución deja `build/instanced-suite-<fecha>/summary.json` y un JSON por escenario, con modos, idiomas, roles, pasos y resultado. El primer fallo detiene la tanda; los informes de cliente en `run/hmc/client-*/config/stagecraft/reports/` incluyen comandos, mensajes y JUnit XML. Los escenarios individuales también funcionan con `tools/director.mjs`.

## Cobertura

Los originales llevan prefijo `instanced.` y sufijo `.e2e.mjs`, salvo los constructores (`.mjs`), `smoke` (`.mjs`), `lang` (`.mjs`) y `numismatic-parties` (`parties.e2e.mjs`). Las dos fases de `dimensions` y de `slots` se reúnen en un escenario con reinicio real.

| Lua | Roles | Comprueba |
|---|---|---|
| `smoke` | A | Mod cargado, entrada, desconexión y reconexión |
| `config` | A | Nivel válido/roto, estados, errores y cambios detectados en reload |
| `samples` | A | Templates y pools de muestra |
| `demo` | A | Fixture de cripta y dimensión cargada |
| `showcase` | A | Capturas, pools, nivel, slot y portal de la sala de muestra |
| `fortress` | A | Fortaleza, 13 assets, 6 pools y 3 slots con recursión |
| `teleports` | A | 5 rondas de entrada/salida, slot tp y liberación forzada; log sin duplicados |
| `return` | A | Ticket, misma instancia, reset, caducidad y límite horario |
| `waiting` | A | Instancia en espera tras caducar el ticket y nueva entrada |
| `instances` | A | Inicio, reconexión, salida, muerte/reaparición, liberación y límite |
| `restart` | A | Reinicio dentro de instancia y portales después de reconectar |
| `dimensions` | A | Crear, entrar, recuperar tras reinicio, borrar y expulsar |
| `slots` | A | Colocación lenta persistida, reanudación y wipe que reinicia la cuadrícula |
| `hud` | A | F8, niveles/cola, sala, jugadores, NBT, navegación y Enter |
| `tracker` | A | Instancia activa, historial del jugador, eventos y reinicio del límite |
| `capture` | A | Ajustar caja, guardar schematic, reemplazar y aviso de templates |
| `place` | A | Selector y confirmación, colocación, edición conservada y fichero inexistente |
| `editor` | A | Ancla, zona/pool y dirección/color/nivel/salida de todo un portal |
| `convert` | A | Conversión, confirmación de reemplazo, menú contextual y cofre conservado |
| `postprocess` | A | Reparto, grupo añadido, selección de pool y JSON guardado |
| `admin` | A | Subida local, carpetas, renombrado, pool, nivel, pregen, jugar, ajustes y borrado en uso |
| `lang` | A, en_us | 7 pestañas, formulario/postproceso, 8 editores, HUD y chat sin claves crudas |
| `tpa` | A, B | TPA, TPHere y homes bloqueados al cruzar límites de instancia |
| `rejoin` | A, B | Liberar la instancia de A desconectado, reconectar y volver a entrar |
| `numismatic-parties` | A, B | Invitación, aceptación, chat, HUD y disolución reservada al líder |
| `parties` | A, B, C, D | Herencia de instancia solo→grupo, cuatro dentro y expulsión tras la gracia |

## Helpers y limpieza

`lib/instanced.lua` centraliza mensajes vanilla por clave, dimensiones, host, fixtures únicos y restauración exacta de la configuración. `lib/gui.lua` selecciona campos por etiqueta y textos dibujados; las expectativas toleran líneas partidas y elipsis, y verifican los datos reales guardados. Las subidas usan `fixtures/`, que el director copia al cliente y expone como `stage.args.fixture_dir`, sin habilitar IO en Lua.

La limpieza usa `pcall` y propaga los fallos. Además de llevar al jugador al overworld, ejecuta `instanced instance kick @s`: un `tp` vanilla por sí solo conserva la pertenencia lógica y contamina el escenario siguiente. Los escenarios que crean niveles propios usan IDs únicos y eliminan sus datos; los constructores de preparación conservan sus fixtures deliberadamente.
