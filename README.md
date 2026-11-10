# Stagecraft

Mod Fabric para ejecutar escenarios Lua desde una ventana de Minecraft o HeadlessMC. Los scripts corren en el cliente; en el servidor, el mismo jar puede activar un host de pruebas (apagado por defecto) para ficheros y estado del servidor. Minecraft **1.21.1**, Yarn build 3, Java **21**, Loader **0.18.4** y Fabric API **0.116.11+1.21.1**. LuaJ está incluido en el jar.

## Instalación y ejecución

1. Compilar con `gradlew.bat build collectHeadlessMods`.
2. Instalar `build/libs/stagecraft-0.1.0.jar` y Fabric API en el cliente. El servidor solo lo necesita para `stage.host` (ver «Host de servidor»).
3. Conectar al servidor y escribir `/stagecraft list`. Al arrancar se instalan `platform.lua`, `smoke.lua`, `fortress.lua` y `gui.lua` en `config/stagecraft/scripts/` dentro de la carpeta del juego. Un ejemplo que no has editado se actualiza solo cuando cambia la versión incluida en el mod; uno editado se conserva y la versión nueva queda al lado como `<nombre>.lua.new`. Los hashes de lo instalado están en `.examples.properties`.
4. Ejecutar `/stagecraft run platform`, o un script propio guardado en esa carpeta.

Los comandos se envían como el jugador conectado, con sus permisos normales. Para las construcciones y capturas de ejemplo hacen falta permisos de administrador; fortress requiere además creativo. Cada jugador real necesita su propio cliente; un script no simula otras cuentas.

## Origen de la construcción

- `/stagecraft run fortress`: guarda tu posición, dimensión y orientación al iniciar.
- `/stagecraft run fortress at 100 80 100`: fija las coordenadas en tu dimensión actual.
- `/stagecraft run fortress in assets:world at 100 80 100`: fija también la dimensión.
- `/stagecraft run platform with size=5 block="minecraft:stone"`: pasa argumentos al script (`stage.args`); `with` va siempre al final, también tras `at` o `facing`.
- `/stagecraft run platform at 100 80 100 facing 90 0`: fija yaw y pitch.
- `/stagecraft status`: estado y comando actual.
- `/stagecraft cancel`: cancela el trabajo pendiente. Lo ya construido o guardado no se deshace.

`stage.world(...)` usa ese origen estable durante toda la ejecución. `~` desplaza por los ejes del mundo; `^` usa la orientación inicial. Moverte o teletransportarte después no desplaza la construcción. `stage.command(...)` envía un comando normal, útil para administración o coordenadas absolutas.

Para fortress, sitúate sobre el suelo y mira hacia la zona de construcción. El script ignora el pitch y ajusta el yaw al cardinal más cercano: todo queda **delante y a tu derecha**, a partir de dos bloques de separación. La base se apoya encima del suelo a la altura de tus pies; no comprueba ni reemplaza el bloque de soporte. Requiere una zona despejada de **56 bloques a la derecha, 96 hacia delante y 13 de alto** (desplazamientos derecha 2..57, delante 2..97, altura 0..12). Las coordenadas, caras de bloques, carteles, zonas y cajas de captura se transforman juntas. El jugador se mueve durante las capturas y activa el vuelo permitido por el servidor; el origen permanece fijo.

`/stagecraft run <Tab>` sugiere los scripts `.lua` válidos de la carpeta, sin extensión. La lista se actualiza cuando añades o eliminas archivos, sin reiniciar. Si editaste un ejemplo y quieres la versión nueva, copia `<nombre>.lua.new` sobre `<nombre>.lua`; a partir de ahí vuelve a actualizarse solo.

## API Lua v2

`stage.api_version` es `2`. Todo lo de la v1 sigue igual.

| Función | Uso |
|---|---|
| `stage.command(texto)` | Comando normal, con o sin `/`; intervalo de 150 ms. |
| `stage.world(texto)` | Comando en el origen guardado. |
| `stage.wait(segundos)` | Espera de 0 a 120 segundos sin bloquear el juego. |
| `stage.expect_all({regex, …}, segundos=15)` | Espera a que todos los patrones aparezcan después del último comando, en cualquier orden; devuelve los textos. |
| `stage.expect_chat(regex, segundos=15)` | Espera un mensaje recibido desde el último comando; consume el mensaje encontrado. Regex de Java, máximo 120 segundos. |
| `stage.assert_dimension(id)` | Comprueba la dimensión actual del jugador. |
| `stage.assert_block(x,y,z,id)` | Comprueba un bloque por desplazamiento respecto al origen; exige chunk cargado en el cliente. |
| `stage.assert_empty(x1,y1,z1,x2,y2,z2)` | Comprueba una caja vacía y cargada, máximo 100000 bloques. Es una comprobación previa, no un bloqueo frente a ediciones simultáneas de otros jugadores. |
| `stage.fly(true/false)` | Activa/desactiva vuelo si el servidor ya lo permite. |
| `stage.log(texto)` | Progreso en chat, consola e informe. |
| `stage.origin` | Posición, dimensión y orientación guardadas: `x`, `y`, `z`, `dimension`, `yaw`, `pitch`. |
| `stage.run_id` | Identificador único de la ejecución para generar rutas e IDs. |
| `stage.instanced_save_pool(ruta,json)` | Adaptador opcional: guarda una pool por el protocolo de administración de Instanced. |
| `stage.instanced_save_level(id,json)` | Adaptador opcional: guarda un nivel por el mismo protocolo. |
| `stage.step(nombre, fn)` | Ejecuta `fn` como un paso con nombre: `ok`/`FAIL` en chat e informe, un caso JUnit por paso. Se pueden anidar (`exterior / interior`). Un fallo se propaga. |
| `stage.expect_key(clave, segundos=15)` | Como `expect_chat`, pero por clave de traducción (`chat.instanced.…`, `command.unknown.command`): no depende del idioma. Devuelve `{text, key, args}`; busca también en argumentos y hermanos del mensaje. |
| `stage.poll(cmd, regex, segundos=30, intervalo=1)` | Reenvía `cmd` cada intervalo hasta que una respuesta coincide; devuelve el texto. |
| `stage.poll_key(cmd, clave, segundos=30, intervalo=1)` | Igual, por clave; devuelve `{text, key, args}`. |
| `stage.player()` | `{name, x, y, z, yaw, pitch, dimension, gamemode, health, food, flying, on_ground, selected_slot}`. |
| `stage.inventory()` | Lista de `{slot, id, count}` de las ranuras no vacías (índices del inventario vanilla). |
| `stage.args` | Tabla de `/stagecraft run <nombre> … with clave=valor otra="con espacios"` (siempre strings). |
| `require(nombre)` | Carga `scripts/lib/<a>/<b>.lua` para `require('a.b')`, una vez por ejecución; nada fuera de `lib/`. |

### Pantallas

Las coordenadas son las de la GUI escalada, las mismas que recibe la pantalla al hacer clic. Las posiciones del texto ya incluyen la transformación de los paneles movidos o escalados (GUI Library), así que no hace falta calcular escalas. Las pantallas sin widgets vanilla, como las de GUI Library, se manejan por su texto. Toda lectura usa un frame dibujado después de la llamada, y cada acción espera dos frames para que la lectura siguiente vea su efecto.

| Función | Uso |
|---|---|
| `stage.screen()` | `{open, title, kind, class, width, height, texts, widgets, hud}`. `kind` es el nombre Yarn de las pantallas vanilla (`GameMenuScreen`, `ChatScreen`, `OptionsScreen`…), porque en producción sus clases se llaman `class_433`; para pantallas de mods es su clase. `widgets`: `{index, type, text, x, y, w, h, active, visible, field, value}`. |
| `stage.texts({hud=false})` | Textos dibujados `{text, x, y, w, h, cx, cy, screen}`, de arriba abajo y de izquierda a derecha. |
| `stage.wait_text(regex, segundos=10, {hud=false})` | Espera un texto y lo devuelve. |
| `stage.wait_screen([regex], segundos=10)` | Espera una pantalla abierta (por título, `kind` o clase) y devuelve `stage.screen()`. |
| `stage.wait_no_screen(segundos=10)` | Espera a que no haya pantalla. |
| `stage.click_text(regex, {index=1, button=0, dx=0, dy=0, timeout=5, hud=false})` | Hace clic en el centro del texto número `index` que coincida. |
| `stage.click_widget(regex | índice, {button=0})` | Hace clic en un widget vanilla por su etiqueta o por su índice en `widgets`. |
| `stage.click(x, y, button=0)`, `stage.scroll(x, y, cantidad)` | Clic y rueda en coordenadas de la GUI. |
| `stage.type(texto)` | Escribe en el elemento con foco. |
| `stage.set_field(n, texto)` | Pone el texto del campo número `n` (`field` en `widgets`) y le da el foco. |
| `stage.key('enter' | 'escape' | 'f8' | 'ctrl+a' …)` | Pulsa y suelta una tecla como el teclado real: funciona con y sin pantalla abierta. |
| `stage.close()` | Cierra la pantalla abierta como lo haría Escape. |

Ejemplo de construcción relativa:

```lua
stage.assert_empty(0, 0, 0, 4, 0, 4)
stage.world('fill ~ ~ ~ ~4 ~ ~4 minecraft:smooth_stone')
stage.expect_chat('filled|rellenado', 10)
stage.wait(1)
stage.assert_block(0, 0, 0, 'minecraft:smooth_stone')
stage.log('Plataforma lista')
```

Hay base Lua, tablas, strings y matemáticas. No se exponen IO, OS ni Java; `require` solo carga librerías de `scripts/lib/`. Los scripts son código local de confianza: regex y asignaciones de memoria de la JVM no constituyen un aislamiento frente a código hostil. Se limita la ejecución Lua a 10 millones de instrucciones y 15 minutos; se cancela al desconectar o cerrar el cliente. Un comando enviado sin una comprobación posterior no demuestra que el servidor lo haya aceptado.

Los informes se guardan en `config/stagecraft/reports/` con fecha y nombre: `.log` (traza de comandos y mensajes), `.json` (estado, origen, argumentos, número de comandos y pasos con su duración y error) y `.xml` en formato JUnit, uno por ejecución, para CI. Los errores Lua incluyen archivo y línea.

Los nombres de archivo admiten letras ASCII, números, `_` y `-`. Las respuestas del servidor pueden depender del idioma del cliente; las esperas de fortress contemplan español e inglés.

## Fortress e Instanced

`fortress.lua` construye la fortaleza, trece assets, sus bloques de captura y el portal. Captura los archivos mediante `/instanced capture`, guarda seis pools y el nivel mediante la administración existente y pregenera tres slots. Cada ejecución usa `stagecraft:<run_id>/fortress` y rutas nuevas: no sobrescribe los IDs `demo:*`. El punto de salida del portal se calcula respecto al origen y a su dimensión.

Hace falta Instanced **0.1.0** tanto en cliente como en servidor y sus dependencias. El adaptador está aislado y enlaza opcionalmente con `AdminClient.send`; no usa archivos del servidor ni añade permisos. Las versiones futuras de ese protocolo pueden requerir actualizar el adaptador: si `AdminClient` o su respuesta cambian, el guardado falla con un mensaje de versión incompatible. El límite de una definición es de 64 000 bytes en UTF-8. Los ejemplos `platform` y `smoke` no necesitan Instanced.

## Host de servidor y varios clientes

Con el jar también en el servidor y `config/stagecraft-server.properties` con `enabled=true` (el fichero se crea apagado), los scripts pueden pedir al servidor lo que un cliente no ve. Responde solo a jugadores con `permission_level` (2 por defecto) y solo toca ficheros bajo `root` (relativo a la carpeta del servidor; rutas absolutas, `..` o enlaces que salgan de ahí se rechazan). `allow_write=false` lo deja en solo lectura. **Es para servidores de prueba locales**: al arrancar avisa en el log si está activo.

| Función | Uso |
|---|---|
| `stage.host_available()` | Si el servidor tiene el canal de Stagecraft. |
| `stage.host(nombre, {args})` | Llama a una función del host y devuelve su resultado como tabla. |

Funciones: `host.info`; `file.exists`, `file.read` → `{text, size}`, `file.list` → `{entries}`, `file.write` / `file.append` (`{path, text}`; los textos grandes se envían troceados, porque un paquete al servidor admite unos 32 KB), `file.delete` (`{path, recursive}`); `nbt.read` → `{json, snbt}`; `server.command` (`{command}`, como consola, con nivel 4) → `{output}`; `server.players`; `world.block` (`{dimension, x, y, z}`) → `{id, state}`.

Para escenarios con varios clientes, `tools/director.mjs` ejecuta el mismo Lua en cada cliente con un rol (`stage.role`) y retransmite las señales: `stage.signal(nombre)` cuenta en ese cliente y en todos los demás, y `stage.wait_signal(nombre, n=1, segundos=60)` espera a que se haya dado `n` veces. Así, si cuatro clientes dan `listo` y esperan 4, se encuentran en ese punto. Pasa solo si pasan todos los roles, y deja el resumen en `build/director-<escenario>.json`.

```powershell
node tools/director.mjs --hmc <mc-headless> --role A=HmcInstancedA --role B=HmcInstancedB --start run tools/scenarios/duo.lua
```

Con `--start` lanza los clientes (con el enlace activo) y los conecta a `--server` (`localhost:25571` por defecto); sin él usa clientes ya abiertos con `STAGECRAFT_LINK=on`. Reiniciar el servidor desde el director aún no está implementado.

## Enlace (WebSocket) y HeadlessMC

Un proceso externo (Node, un director de pruebas) puede controlar el cliente sin leer su `console.log`. El enlace está **apagado por defecto**. Se enciende con la variable de entorno `STAGECRAFT_LINK=on` (o un puerto) o con `config/stagecraft/link.properties` (`enabled=true`, `port=…`). Escucha solo en 127.0.0.1 y exige el token que publica en `config/stagecraft/link.json` (`port`, `token`, `pid`, `protocol`); el token es nuevo en cada arranque y el fichero se borra al cerrar.

Protocolo JSON, una petición `{id, type, …}` y una respuesta `{id, type: "reply" | "error"}`:

| Petición | Respuesta |
|---|---|
| `status` | `status`, `run_id`, `scenarios` |
| `run` (`name`; `source` para Lua inline; `args`; `origin` con `dimension, x, y, z, yaw, pitch`, lo que falte sale del jugador) | `run_id` |
| `cancel`, `command` (`command`, enviado como el jugador), `player` (`connected`, nombre, dimensión, posición), `signal` (`name`, retransmitida desde otro cliente) | — |

Eventos para todas las conexiones `{type: "event", event, run_id}`: `started`, `log`, `step` (nombre, estado, error), `signal`, `finished` (estado, error e informe JSON completo) y `chat` (texto y claves de traducción con sus argumentos).

`tools/stagecraft-link.mjs` es el cliente Node, sin dependencias (WebSocket global de Node 22+): `connect(dirCliente)`, `run(nombre, {source, args, origin})` → `{status, error, report, logs, steps}`, `on(evento)`, `waitChat(regex | clave)`, `command`, `player`, `cancel`. También como CLI:

```powershell
$env:STAGECRAFT_LINK='on'
$env:HMC_MODS='C:UserscontaIdeaProjectsStagecraftuildheadless-mods;C:UserscontaIdeaProjectsInstancedabric-clientuildheadless-mods'
$env:HMC_VERSION='fabric-loader-0.18.4-1.21.1'
node hmc.mjs start HmcInstancedA
# Desde otra consola:
node hmc.mjs send HmcInstancedA 'connect localhost 25571'
node tools/stagecraft-link.mjs <mc-headless>/client-HmcInstancedA run platform size=5
node tools/stagecraft-link.mjs <mc-headless>/client-HmcInstancedA run-file mi-escenario.lua
```

Sin enlace, desde la consola de hmc-specifics se puede seguir enviando `/stagecraft run …`. El perfil Fabric debe estar instalado en HeadlessMC.

## Verificación de desarrollo

`gradlew.bat build` ejecuta las pruebas de origen y compilación de todos los ejemplos Lua. `tools/verify-hmc.mjs` comprueba por el enlace un cliente HMC ya conectado (con `STAGECRAFT_LINK=on`), creativo y OP en un servidor de pruebas; `--gui` añade el ejemplo `gui`: origen relativo/fijo/orientado, dimensión explícita, errores, cancelación, límite de instrucciones y opcionalmente fortress completo con portal. Escribe `build/hmc-verification.json`.

```powershell
$env:HMC_HOME='C:\Users\conta\Documents\mc-test-tools\mc-headless'
$env:INSTANCED_SERVER_DIR='C:\Users\conta\IdeaProjects\Instanced\fabric-server\run'
node tools/verify-hmc.mjs --fortress
```

Ese verificador usa zonas vacías del servidor de desarrollo; no debe ejecutarse en producción. `FORTRESS_X` cambia la coordenada x/z de la construcción de prueba (8448 por defecto). Los scripts temporales del verificador se eliminan; la construcción y sus datos quedan para inspección hasta que se limpien explícitamente.

Para repetir las comprobaciones del portal de la última fortaleza sin reconstruirla, usar `--fortress --reuse-fortress` mientras existan los datos del último informe.

### Portar escenarios antiguos

`node tools/port-scenario.mjs <escenario>.mjs <salida>.lua [-- modo]` ejecuta un escenario de `mc-headless` sin Minecraft, con un cliente que graba y la carpeta del servidor interceptada, y escribe un borrador Lua para el director:
- cada `step()` pasa a `stage.step`;
- `run` y `waitFor` pasan a `command` + `expect_chat`, y varias esperas desde el mismo `since` pasan a `stage.expect_all`;
- `key`, `close` y `click p` se traducen a la API de GUI;
- los ficheros del servidor pasan a `stage.host` (los binarios en base64);
- varios clientes pasan a roles, con señales que conservan el orden;
- el `finally` del original pasa a una limpieza que también se ejecuta tras un fallo.

Lo que depende de la salida (pantallas leídas con `render`, coordenadas calculadas, bucles de sondeo o un paso que falla al grabar) queda marcado como `TODO`. El estado del port está en `mc-headless/stagecraft/README.md`.

`tools/port-fortress.mjs` documenta la migración desde el escenario Node original y genera el Lua incluido. Es una herramienta de desarrollo; no se necesita para ejecutar el mod.
