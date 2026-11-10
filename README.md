# Stagecraft

Mod Fabric para ejecutar escenarios Lua en clientes reales de Minecraft (con ventana o headless con HeadlessMC), también varios a la vez con el director. Los scripts corren en el cliente; en el servidor, el mismo jar puede activar un host de pruebas (apagado por defecto) para ficheros y estado del servidor. Minecraft **1.21.1**, Yarn build 3, Java **21**, Loader **0.18.4** y Fabric API **0.116.11+1.21.1**. LuaJ está incluido en el jar.

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

## Tests en clientes reales: director, clientes y enlace

> Guía completa para montar tests (también para agentes): la skill
> [`.claude/skills/stagecraft-tests`](.claude/skills/stagecraft-tests/SKILL.md), con la API completa, el
> director, el host de servidor, el port de escenarios, problemas frecuentes y plantillas probadas.

`tools/director.mjs` ejecuta un escenario Lua en uno o varios clientes reales, cada uno con un rol (`stage.role`).
Retransmite las señales entre clientes (`stage.signal` / `stage.wait_signal`) y pasa solo si pasan todos los roles.
Cada cliente puede ser **headless** (HeadlessMC, sin ventana), **con ventana** (una ventana normal que se puede
ver mientras se ejecuta) o una **combinación**. Las ventanas se colocan en mosaico sobre el monitor principal
para que todas sean visibles, y el título lleva el rol.

```bash
node tools/hmc/setup.mjs                                    # una vez: HeadlessMC y hmc-specifics en run/hmc
export HMC_MODS="<Stagecraft>/build/headless-mods;<mod>/build/headless-mods"
node tools/director.mjs --start --role A=HmcA run scenarios/duo.lua                       # headless
node tools/director.mjs --start --role A=HmcA:window --role B=HmcB run scenarios/duo.lua  # A visible, B headless
node tools/director.mjs --start --window-all --role A=HmcA --role B=HmcB run examples/gui.lua
```

- **Headless:** pasan por HeadlessMC con LWJGL simulado y assets falsos.
- **Con ventana:**
  - Se lanzan directamente con `tools/hmc/launch.mjs`, desde lo que HeadlessMC ya descargó. En Windows, HeadlessMC solo admite una cuenta offline en modo headless.
  - Usan assets reales en `run/hmc/assets`: unos 107 MB; los sonidos (unos 720 MB) solo con `STAGECRAFT_HMC_SOUNDS=1`.
  - No cargan hmc-specifics: se manejan por el enlace (`connect`, comandos, `quit`).
  - `--window-mods <dir>` añade mods solo a estas ventanas, por ejemplo Veil, que necesita OpenGL.
- **Mosaico:** Stagecraft coloca cada ventana a partir de `STAGECRAFT_WINDOW=i/n` (o `x,y,w,h`) y pone `STAGECRAFT_LABEL` en el título.

### Host de servidor

Con el mismo jar en el servidor y `enabled=true` en `config/stagecraft-server.properties` (el fichero se crea apagado), `stage.host(nombre, {args})` lee y escribe ficheros bajo `root`, ejecuta comandos de consola y lee jugadores, bloques y NBT.
- Responde solo a jugadores con `permission_level`.
- Rechaza rutas absolutas, `..` y enlaces que salgan de `root`.
- Al arrancar avisa en el log si está activo.
- Es solo para servidores de prueba locales.

Funciones y límites: [server-host.md](.claude/skills/stagecraft-tests/references/server-host.md).

### Enlace (WebSocket)

Está **apagado por defecto** y se enciende con `STAGECRAFT_LINK=on` o con `config/stagecraft/link.properties`. Escucha solo en 127.0.0.1 y exige el token que publica en `config/stagecraft/link.json`.
- Peticiones: `status`, `run` (script o Lua inline), `cancel`, `command`, `player`, `signal`, `connect`, `disconnect`, `quit`.
- Eventos: `started`, `log`, `step`, `signal`, `finished` (con el informe) y `chat` (con claves de traducción).

`tools/stagecraft-link.mjs` es el cliente Node (sin dependencias) y también una CLI:

```bash
node tools/stagecraft-link.mjs HmcA status
node tools/stagecraft-link.mjs HmcA run-file mi-escenario.lua clave=valor
```

Protocolo completo: [director.md](.claude/skills/stagecraft-tests/references/director.md).

### Escenarios

`scenarios/` guarda escenarios terminados: `duo.lua` (host + señales con dos clientes) e `instanced/` (los de Instanced, portados de `mc-headless`, con borradores en `drafts/`). Los escenarios `.mjs` antiguos siguen en `Documents/mc-test-tools/mc-headless`, donde `hmc.mjs` es ahora un shim que apunta a `tools/hmc/hmc.mjs`.

## Verificación de desarrollo

`gradlew.bat build` ejecuta las pruebas de origen y compilación de todos los ejemplos Lua. `tools/verify-hmc.mjs` comprueba por el enlace un cliente de `tools/hmc` ya conectado (`HMC_NAME`, por defecto `HmcInstancedA`; con `STAGECRAFT_LINK=on`), creativo y OP en un servidor de pruebas; `--gui` añade el ejemplo `gui`: origen relativo/fijo/orientado, dimensión explícita, errores, cancelación, límite de instrucciones y opcionalmente fortress completo con portal. Escribe `build/hmc-verification.json`.

```powershell
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

Lo que depende de la salida (pantallas leídas con `render`, coordenadas calculadas, bucles de sondeo o un paso que falla al grabar) queda marcado como `TODO`. El estado del port está en `scenarios/instanced/README.md`.

`tools/port-fortress.mjs` documenta la migración desde el escenario Node original y genera el Lua incluido. Es una herramienta de desarrollo; no se necesita para ejecutar el mod.
