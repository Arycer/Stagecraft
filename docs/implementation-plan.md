# Stagecraft — implementación

## Resultado

Mod nuevo e independiente en `IdeaProjects/Stagecraft`, solo para cliente, para Minecraft 1.21.1, Yarn 1.21.1+build.3, Java 21, Fabric Loader 0.18.4 y Fabric API 0.116.11+1.21.1.

## Requisitos completados

- [x] Ejecutar scripts Lua locales desde el juego o HeadlessMC, sin un escenario Node necesario para la ejecución.
- [x] Comandos locales `list`, `run`, `status` y `cancel`; progreso y errores con archivo/línea.
- [x] Entorno Lua limitado: sin IO, OS, Java ni carga arbitraria de módulos o archivos. Una ejecución por cliente, con límite de instrucciones y tiempo. Scripts locales de confianza; no es aislamiento frente a código hostil.
- [x] Comandos secuenciales, esperas y expectativas de chat, comprobaciones de dimensión, bloques y cajas vacías.
- [x] Capturar una vez la posición, orientación y dimensión del jugador. Mantener ese origen aunque el jugador se mueva durante la ejecución.
- [x] Coordenadas fijas, dimensión explícita y orientación fija mediante argumentos del comando local.
- [x] `stage.world` para `~` y `^` desde el origen guardado; `stage.command` para comandos ordinarios y coordenadas absolutas.
- [x] Port completo de fortress: construcción, trece assets capturados, seis pools, nivel, tres slots y portal. IDs y archivos únicos por ejecución.
- [x] Guardar las definiciones de Instanced mediante su protocolo de administración existente, con validación y permisos del servidor. Stagecraft no escribe archivos del servidor.
- [x] Informes persistentes en `config/stagecraft/reports/`, con comandos y resultado.
- [x] Jar de producción que incluye LuaJ y ejemplos; copia inicial de scripts sin sobrescribir archivos existentes.
- [x] Instrucciones de instalación y ejecución en README.md.

## Verificación

Ver [verification.md](verification.md) para entorno, comandos y evidencias. No quedan tareas de implementación de esta primera versión.

Las pruebas de varios jugadores siguen requiriendo varios clientes o pruebas de servidor: un script cliente no representa otras cuentas. Fortress se orienta horizontalmente al cardinal más cercano y se coloca sobre el suelo delante/derecha del jugador, transformando también estados de bloques y cajas NBT. El origen sigue fijo durante toda la ejecución. Los nombres de escenarios se sugieren dinámicamente en `/stagecraft run`.

## Ampliación (10/11/2026)

Hecho y comprobado con clientes HMC reales contra el servidor de desarrollo de Instanced:

1. API Lua v2: pasos, claves de traducción, sondeo, argumentos, librerías e informes JSON/JUnit.
2. API de GUI: texto dibujado con coordenadas transformadas, widgets, clics, teclas y campos.
3. Enlace WebSocket (apagado por defecto, con token) y cliente Node; `verify-hmc.mjs` va por el enlace.
4. Host de servidor (mismo jar, apagado por defecto) para ficheros y estado; señales, roles y director de varios clientes.
5. `tools/port-scenario.mjs`; `config` y `tpa` portados y pasando, el resto como borradores (`mc-headless/stagecraft/README.md`).

Pendiente:
- Que el director reinicie el servidor, para `restart`, `rejoin` y los modos de `dimensions`/`slots`.
- Terminar los borradores, sobre todo los de GUI: sus lecturas de `render` pasan a `wait_text`/`click_text`.
