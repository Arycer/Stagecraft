# Port de los escenarios de Instanced — completado

Encargo: terminar todos los drafts del port de `mc-headless` y ejecutar después todos los escenarios con ventanas visibles. El trabajo se realiza en Stagecraft; no hay cambios adicionales en Instanced ni se hace push.

## Requisitos

- [x] Leer y completar cada borrador contra su original, conservando sus comprobaciones.
- [x] Unificar las fases de `dimensions` y `slots` mediante un reinicio real.
- [x] Conexiones esperadas: `stage.connected`, `disconnect`, `connect`, `restart_server` y `mods`.
- [x] Director con varios escenarios por tanda, librerías y fixtures copiados al cliente, idioma y nombres de cuentas por argumentos.
- [x] Resolver el proceso servidor que no termina después de guardar: solo el PID local capturado, comprobando su identidad y un nuevo `All dimensions are saved` antes de terminarlo.
- [x] 26 escenarios aprobados con clientes reales headless; incluye los de 2 y 4 roles y Parties con HUD.
- [x] Retirar todos los drafts aprobados; no queda ninguno en `scenarios/instanced/drafts/`.
- [x] Build y 32 tests JUnit correctos; compilación Lua de todos los escenarios y librerías, declarados como inputs de Gradle.
- [x] Documentación y tabla de cobertura en `scenarios/instanced/README.md`; skill actualizada.
- [x] Commits por bloques sin `Co-Authored-By`, sin push.
- [x] 26/26 escenarios comprobados con ventanas visibles: 32 ejecuciones de cliente, 138 pasos, todos aprobados; informes y cobertura auditados.

## Ejecución visible y auditoría final (10/11/2026)

```powershell
$env:HMC_MODS='C:/Users/conta/IdeaProjects/Stagecraft/build/headless-mods;C:/Users/conta/IdeaProjects/Instanced/fabric-client/build/headless-mods'
node tools/verify-instanced.mjs --party-mods build/party-client-mods
```

Resultado agregado y auditado: **`build/instanced-visible-verification.json`**, con `passed: true`, `windowed: true`, los 26 nombres esperados, 32 informes de rol y 138 pasos aprobados. Incluye hashes del Lua, referencias a cada informe, inventarios de ventanas y los 32 JUnit sin fallos. Se conserva también la evidencia de los intentos fallidos, sin cambiar sus resultados.

| Tanda | Resultado final usado |
|---|---|
| `build/instanced-suite-2026-11-10T16-14-46-469Z/` | Primeros diez escenarios aprobados: smoke → instances. El restart inicial falló y quedó archivado. |
| `build/instanced-suite-2026-11-10T16-25-49-681Z/` | Once escenarios de un jugador aprobados: restart → admin. La primera ejecución visible de lang pasó sus comprobaciones de pantalla/chat y falló en un diagnóstico de texto; se repitió. |
| `build/instanced-suite-2026-11-10T16-35-18-284Z/` | 5/5: lang, tpa, rejoin, numismatic-parties y parties, todos los roles aprobados. |

La auditoría comprobó los 26 escenarios explícitos y el último resultado de cada uno, no una selección de pruebas favorables. Los primeros diez Lua y su runtime/helpers permanecieron iguales a `c39f607` mientras se corrigieron exclusivamente restart y lang. Las ventanas nativas quedaron registradas: una de 2560×1392, dos de 1280×1392 y cuatro de 1280×696 sobre el monitor principal, mediante `tools/hmc/windows.ps1`.

Las consolas completas son `build/port-visible-full.log`, `port-visible-remaining.log` y `port-visible-final-groups.log`. El build final es `build/port-final-build3.log`; JUnit: `build/test-results/test/` (32 tests, 0 fallos, 0 errores, 0 omitidos).

## Evidencia headless

- `build/port-foundation.log` y `port-restart.log`: conexión/reconexión y reinicio.
- `build/port-commands.log`: waiting; `port-single3.log`: showcase, teleports (5 rondas), return, instances, dimensions, slots y editor.
- `build/port-commands2.log`: samples y demo.
- `build/port-gui3.log`: capture, place y fortress; `port-gui4.log`: convert, postprocess y admin.
- `build/port-lang.log`: siete pestañas, formulario/postproceso, ocho editores, HUD y chat en inglés.
- `build/port-hud-tracker3.log`: HUD completo y seguimiento de jugadores.
- `build/port-numismatic3.log`: Parties con dos clientes y HUD.
- `build/instanced-suite-2026-11-10T16-08-21-668Z/`: TPA/homes y rejoin, todos los roles aprobados.
- `build/instanced-suite-2026-11-10T16-10-19-751Z/`: grupo de cuatro, herencia de instancia y gracia de expulsión, los cuatro roles aprobados.
- `build/port-restart-strong.log`: reinicio con conservación de dimensión, salida por el portal relativo al spawn real y reentrada verificada. También elimina el fixture temporal de la ejecución interrumpida.
- `build/port-final-build3.log` y `build/test-results/test/`: build correcto, 32 tests, sin fallos.

## Decisiones y correcciones

- Los constructores samples/demo/showcase mantienen sus fixtures, como los originales. Fortress construye una zona vacía nueva con ID único y la conserva para inspección. Los demás escenarios eliminan sus niveles y restauran exactamente la configuración guardada antes de empezar.
- La limpieza usa `instanced instance kick @s` antes de volver al overworld: un TP vanilla cambia la dimensión física pero conserva la pertenencia lógica y puede contaminar el siguiente escenario.
- Respawn se pulsa después de que termine su bloqueo inicial. Antes se pulsaba demasiado pronto, dejando al jugador muerto para pruebas posteriores.
- Los campos se eligen por etiqueta cuando hay campos registrados de pestañas ocultas. Las expectativas de GUI aceptan líneas partidas/elipsis y comprueban los ficheros, NBT y bloques reales; el historial verifica el UUID exacto de la instancia recién jugada.
- Rejoin comparte por el host el UUID de la instancia ocupada antes de desconectarse; el otro rol libera ese UUID, no una instancia cualquiera del nivel.
- Escala de GUI explícita: 1 headless y 2 visible. Cada launcher headless tiene su cwd para evitar que la limpieza de otro elimine sus jars/nativas parcheados.
- Parties requirió recompilar sus jars de producción para que coincidieran con el runtime dev del servidor; las versiones textuales iguales no garantizaban un protocolo igual. `build/party-client-mods` contiene Parties, Tab API y Cardinal Components base/entity/scoreboard, sin otra Fabric API ni Phone.
- El restart conserva el origen de la ejecución y comprueba la misma dimensión; comprueba salida y reentrada, además del log sin errores de teletransporte. La aproximación al portal usa saltos cortos confirmados y deja el último cruce a un bloque: un TP de 12 bloques directo al portal podía disparar el control vanilla de movimiento al coincidir dos teletransportes en el mismo tick. No se quitó la comprobación del log.
- El diagnóstico de textos españoles evita clases Unicode y patrones de frontera de LuaJ que provocaban `ArrayIndexOutOfBoundsException`; usa búsquedas literales y límites ASCII. Las comprobaciones de traducción de todas las pantallas y del chat se mantienen.

## Cierre y commits

Clientes y servidor de pruebas guardados y cerrados; puerto 25571 libre. Instanced conserva su árbol limpio. El apagado verificó el PID del servidor que arrancó el director y esperó un nuevo `All dimensions are saved` antes de liberar la JVM parada. Registro: `build/port-shutdown.log`.

`050f512`, `de6eff2`, `39be4aa`, `698234a`, `073eb28`, `8eee906`, `c39f607`. Todos locales y sin coautoría. El commit final documenta esta auditoría, la documentación sincronizada y las correcciones finales de restart/lang, verificadas con ventanas. No se ha hecho push.

Cobertura y comandos reproducibles: [README de escenarios](../scenarios/instanced/README.md). Skill: [stagecraft-tests](../.claude/skills/stagecraft-tests/SKILL.md).
