# Verificación de Stagecraft

Ejecutada el 10 de noviembre de 2026 en Windows, Java 21.

## Build

`gradlew.bat build collectHeadlessMods --warning-mode all --console=plain` terminó correctamente. Tres pruebas JUnit aprobadas: conservación del origen y la orientación independientemente del locale, precisión en los límites de bloque y compilación de todos los ejemplos con el compilador Lua incluido.

Artefacto: `build/libs/stagecraft-0.1.0.jar`. Incluye metadatos de mod solo cliente, LuaJ y los tres ejemplos. `build/headless-mods` contiene el mod y Fabric API para HMC.

## Cliente real

HeadlessMC con Minecraft 1.21.1, **Fabric Loader 0.18.4** y Fabric API 0.116.11+1.21.1, contra el servidor de desarrollo local de Instanced en 25571. El propio log del cliente confirma esas versiones. Se usó el comando local registrado por Fabric, el mismo que se escribe en el chat de un cliente normal; no se ha comprobado el render de una ventana de juego.

`tools/verify-hmc.mjs --fortress --reuse-fortress` terminó con código 0. La última fortaleza ya se había construido en una pasada anterior con el mismo motor y se reutilizó para verificar entrada y salida.

Comprobaciones reales:

- Ejecución de Lua desde la consola HMC mediante `/stagecraft run`.
- Colocación desde origen actual y explícito; el jugador se mueve antes del segundo bloque y ambas posiciones siguen referidas al origen inicial.
- Coordenadas `^` con yaw fijo 90 y pitch 0, comprobando el bloque resultante.
- Dimensión explícita `minecraft:the_end`, con traslado del jugador y comprobación de bloque allí.
- Rechazo de una caja sin chunks cargados y de una zona ocupada.
- Error deliberado con archivo/línea de Lua.
- Bucle infinito detenido por el presupuesto de instrucciones.
- Espera de 120 segundos cancelada por el comando local.
- Entorno Lua sin `io`, `os`, `luajava`, `package` ni `require`.
- Fortress: construcción y capturas en el servidor, trece `.nbt`, seis JSON de pools, un `.schem`, definición del nivel y tres slots listos.
- Portal de entrada: dimensión `instanced:level/stagecraft/e667bf316ac0/fortress` confirmada en el servidor.
- Portal de salida: posición `[8699.5d, 200.0d, 8694.5d]`, calculada respecto al origen de la construcción `(8704,200,8704)`.

Las comprobaciones de archivos del escenario leen únicamente la configuración del servidor de pruebas desde la herramienta de verificación. El mod y sus scripts usan el protocolo de administración y comandos de Instanced.

## Evidencias locales

- `build/hmc-verification.json`: resultados de la última pasada.
- `build/hmc-output.log`: salida del verificador.
- `build/test-results/test/`: XML de JUnit.
- `build-output.log`: salida del build.
- `mc-headless/client-HmcInstancedA/console.log`: mensajes del cliente real, incluyendo resultados y versiones.
- `mc-headless/client-HmcInstancedA/config/stagecraft/reports/`: informes del motor Lua.
- `build/cleanup-output.log`: limpieza de los fixtures de verificación.

Los informes bajo `build/` son artefactos locales y se eliminan con `clean`. El README documenta cómo repetir las pruebas en otro entorno.

## Limpieza

Se retiraron las cuatro construcciones temporales (orígenes 8320, 8448, 8576 y 8704), sus capturas, pools, niveles y dimensiones generadas. El servidor volvió a sus cuatro niveles originales válidos y once pools. El repositorio de Instanced no tiene cambios de código por esta tarea.

El cliente de prueba está cerrado. El servidor guardó todas las dimensiones tras `/stop`; se terminó su proceso residual después de comprobar el guardado completo.

## Construcción desde suelo y autocompletado

Actualización del mismo día: seis pruebas JUnit aprobadas. Las nuevas pruebas ejecutan fortress completo con una API simulada en cuatro orientaciones; comprueban que la base arranca sobre el suelo, que el volumen queda delante/derecha y que los offsets/tamaños de captura cubren los bloques transformados. El catálogo devuelve solo nombres de scripts ejecutables y refleja nuevos archivos sin reiniciar.

Prueba real adicional con Loader 0.18.4: `tools/verify-hmc.mjs --fortress` terminó con código 0 para origen `(9000,200,9000)` y yaw 0. Se capturaron los trece assets, guardaron las seis pools, generaron tres slots y verificaron ambos portales. El punto de retorno transformado fue `[8998.5d, 201.0d, 9005.5d]`. También se ejecutó una comprobación separada con una plataforma de piedra a y=199: el preflight aceptó el aire superior sin exigir borrar el suelo. La plataforma temporal se retiró.

La copia de fortress del cliente `HmcInstancedA` está actualizada; su archivo anterior se conserva con sufijo `.before-ground-facing.bak`. En otros clientes se conserva la política de no sobrescribir scripts existentes: se puede copiar el ejemplo actualizado según el README.

La fortaleza adicional del test se retiró junto con sus datos. Se utilizó el servidor de desarrollo que ya estaba activo; se conserva ese servidor. Se cerró el cliente de pruebas y se terminó únicamente el intento adicional de servidor que había fallado al obtener el bloqueo del mundo.
