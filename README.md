# Stagecraft

Mod de cliente Fabric para ejecutar escenarios Lua desde una ventana de Minecraft o HeadlessMC. Minecraft **1.21.1**, Yarn build 3, Java **21**, Loader **0.18.4** y Fabric API **0.116.11+1.21.1**. LuaJ está incluido en el jar.

## Instalación y ejecución

1. Compilar con `gradlew.bat build collectHeadlessMods`.
2. Instalar `build/libs/stagecraft-0.1.0.jar` y Fabric API en el cliente. El servidor no necesita Stagecraft.
3. Conectar al servidor y escribir `/stagecraft list`. Al arrancar se instalan `platform.lua`, `smoke.lua` y `fortress.lua` en `config/stagecraft/scripts/` dentro de la carpeta del juego. Un ejemplo que no has editado se actualiza solo cuando cambia la versión incluida en el mod; uno editado se conserva y la versión nueva queda al lado como `<nombre>.lua.new`. Los hashes de lo instalado están en `.examples.properties`.
4. Ejecutar `/stagecraft run platform`, o un script propio guardado en esa carpeta.

Los comandos se envían como el jugador conectado, con sus permisos normales. Para las construcciones y capturas de ejemplo hacen falta permisos de administrador; fortress requiere además creativo. Cada jugador real necesita su propio cliente; un script no simula otras cuentas.

## Origen de la construcción

- `/stagecraft run fortress`: guarda tu posición, dimensión y orientación al iniciar.
- `/stagecraft run fortress at 100 80 100`: fija las coordenadas en tu dimensión actual.
- `/stagecraft run fortress in assets:world at 100 80 100`: fija también la dimensión.
- `/stagecraft run platform at 100 80 100 facing 90 0`: fija yaw y pitch.
- `/stagecraft status`: estado y comando actual.
- `/stagecraft cancel`: cancela el trabajo pendiente. Lo ya construido o guardado no se deshace.

`stage.world(...)` usa ese origen estable durante toda la ejecución. `~` desplaza por los ejes del mundo; `^` usa la orientación inicial. Moverte o teletransportarte después no desplaza la construcción. `stage.command(...)` envía un comando normal, útil para administración o coordenadas absolutas.

Para fortress, sitúate sobre el suelo y mira hacia la zona de construcción. El script ignora el pitch y ajusta el yaw al cardinal más cercano: todo queda **delante y a tu derecha**, a partir de dos bloques de separación. La base se apoya encima del suelo a la altura de tus pies; no comprueba ni reemplaza el bloque de soporte. Requiere una zona despejada de **56 bloques a la derecha, 96 hacia delante y 13 de alto** (desplazamientos derecha 2..57, delante 2..97, altura 0..12). Las coordenadas, caras de bloques, carteles, zonas y cajas de captura se transforman juntas. El jugador se mueve durante las capturas y activa el vuelo permitido por el servidor; el origen permanece fijo.

`/stagecraft run <Tab>` sugiere los scripts `.lua` válidos de la carpeta, sin extensión. La lista se actualiza cuando añades o eliminas archivos, sin reiniciar. Si editaste un ejemplo y quieres la versión nueva, copia `<nombre>.lua.new` sobre `<nombre>.lua`; a partir de ahí vuelve a actualizarse solo.

## API Lua v1

| Función | Uso |
|---|---|
| `stage.command(texto)` | Comando normal, con o sin `/`; intervalo de 150 ms. |
| `stage.world(texto)` | Comando en el origen guardado. |
| `stage.wait(segundos)` | Espera de 0 a 120 segundos sin bloquear el juego. |
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

Ejemplo de construcción relativa:

```lua
stage.assert_empty(0, 0, 0, 4, 0, 4)
stage.world('fill ~ ~ ~ ~4 ~ ~4 minecraft:smooth_stone')
stage.expect_chat('filled|rellenado', 10)
stage.wait(1)
stage.assert_block(0, 0, 0, 'minecraft:smooth_stone')
stage.log('Plataforma lista')
```

Hay base Lua, tablas, strings y matemáticas. No se exponen IO, OS, Java, módulos ni carga arbitraria de archivos. Los scripts son código local de confianza: regex y asignaciones de memoria de la JVM no constituyen un aislamiento frente a código hostil. Se limita la ejecución Lua a 10 millones de instrucciones y 15 minutos; se cancela al desconectar o cerrar el cliente. Un comando enviado sin una comprobación posterior no demuestra que el servidor lo haya aceptado.

Los informes UTF-8 se guardan en `config/stagecraft/reports/` con fecha, nombre, comandos y resultado. Los errores Lua incluyen archivo y línea.

Los nombres de archivo admiten letras ASCII, números, `_` y `-`. Las respuestas del servidor pueden depender del idioma del cliente; las esperas de fortress contemplan español e inglés.

## Fortress e Instanced

`fortress.lua` construye la fortaleza, trece assets, sus bloques de captura y el portal. Captura los archivos mediante `/instanced capture`, guarda seis pools y el nivel mediante la administración existente y pregenera tres slots. Cada ejecución usa `stagecraft:<run_id>/fortress` y rutas nuevas: no sobrescribe los IDs `demo:*`. El punto de salida del portal se calcula respecto al origen y a su dimensión.

Hace falta Instanced **0.1.0** tanto en cliente como en servidor y sus dependencias. El adaptador está aislado y enlaza opcionalmente con `AdminClient.send`; no usa archivos del servidor ni añade permisos. Las versiones futuras de ese protocolo pueden requerir actualizar el adaptador: si `AdminClient` o su respuesta cambian, el guardado falla con un mensaje de versión incompatible. El límite de una definición es de 64 000 bytes en UTF-8. Los ejemplos `platform` y `smoke` no necesitan Instanced.

## HeadlessMC

No hace falta un escenario Node para ejecutar Lua: desde la consola de hmc-specifics se envía `/stagecraft run fortress`. Con el wrapper que ya usa este entorno:

```powershell
$env:HMC_MODS='C:\Users\conta\IdeaProjects\Stagecraft\build\headless-mods;C:\Users\conta\IdeaProjects\Instanced\fabric-client\build\headless-mods'
$env:HMC_VERSION='fabric-loader-0.18.4-1.21.1'
node hmc.mjs start HmcInstancedA
# Desde otra consola:
node hmc.mjs send HmcInstancedA 'connect localhost 25571'
node hmc.mjs send HmcInstancedA '/stagecraft run fortress'
node hmc.mjs stop HmcInstancedA
```

El perfil Fabric debe estar instalado en HeadlessMC. El cliente tiene sus propios scripts en `client-HmcInstancedA/config/stagecraft/scripts/`. Para usar solo Stagecraft, basta con su carpeta `build/headless-mods` y hmc-specifics; fortress añade el requisito de Instanced.

## Verificación de desarrollo

`gradlew.bat build` ejecuta las pruebas de origen y compilación de todos los ejemplos Lua. `tools/verify-hmc.mjs` comprueba un cliente HMC ya conectado, creativo y OP en un servidor de pruebas: origen relativo/fijo/orientado, dimensión explícita, errores, cancelación, límite de instrucciones y opcionalmente fortress completo con portal. Escribe `build/hmc-verification.json`.

```powershell
$env:HMC_HOME='C:\Users\conta\Documents\mc-test-tools\mc-headless'
$env:INSTANCED_SERVER_DIR='C:\Users\conta\IdeaProjects\Instanced\fabric-server\run'
node tools/verify-hmc.mjs --fortress
```

Ese verificador usa zonas vacías del servidor de desarrollo; no debe ejecutarse en producción. `FORTRESS_X` cambia la coordenada x/z de la construcción de prueba (8448 por defecto). Los scripts temporales del verificador se eliminan; la construcción y sus datos quedan para inspección hasta que se limpien explícitamente.

Para repetir las comprobaciones del portal de la última fortaleza sin reconstruirla, usar `--fortress --reuse-fortress` mientras existan los datos del último informe.

`tools/port-fortress.mjs` documenta la migración desde el escenario Node original y genera el Lua incluido. Es una herramienta de desarrollo; no se necesita para ejecutar el mod.
