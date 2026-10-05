# Progreso

## Fases

| Fase | Estado | Rama |
|---|---|---|
| 0. Propuesta: árbol, decisiones y dudas | ✅ Fusionada | `phase-0-propuesta` |
| 1. Esqueleto Gradle, lanzador y CI | ✅ Fusionada | `phase-1-esqueleto` |
| 2. Intérprete con tests | ✅ Fusionada | `phase-2-interprete` |
| 3. core-sim y sim-cli, `docs/BALANCE.md` | ✅ Hecha, en PR | `phase-3-simulacion` |
| 4. Juego: camino, torretas, enemigos, bucle | Solo con Yeray en casa | — |
| 5. Editor, tienda, parte de oleada, informe | Solo con Yeray en casa | — |
| 6. Guardado, ajustes, robustez | Solo con Yeray en casa | — |
| 7. Empaquetado y documentación | Solo con Yeray en casa | — |

## Fase 0 (hecha)

- Plan movido a `docs/plan-diseno.pdf` y leído entero.
- `docs/ARCHITECTURE.md`: stack, árbol de carpetas, módulos, diseño del
  intérprete, simulación, guardado, CI, reglas del juego con su origen y las
  decisiones D-01 … D-22 (sección 12).
- `CLAUDE.md` con las reglas permanentes.
- `.gitignore`.

## Fase 1 (hecha)

- Gradle 9.8.0 (wrapper) con Kotlin DSL, catálogo de versiones y los seis
  módulos (`script`, `core-sim`, `platform`, `game`, `lwjgl3`, `sim-cli`).
  Maven Central y el portal de plugins descargan bien desde la sesión.
- JDK 21; la versión vive solo en `.java-version`.
- `platform`: `PlatformServices` + `NoOpPlatformServices` y
  `UserDataDirectory` (carpeta de usuario por SO, compatible con Auto-Cloud)
  con tests de Windows, macOS y Linux.
- `core-sim`: generador `SplitMix64` con semilla y tests (vector de
  referencia, misma semilla → misma secuencia).
- `game`: `WaveDefenseGame` (limpia la pantalla) y test de humo de su ciclo de
  vida sobre el backend headless.
- `lwjgl3`: lanzador 1280×720, título desde `i18n/messages.properties` y
  `StartupHelper` para `-XstartOnFirstThread` en macOS.
- Spotless + `.editorconfig` + `.gitattributes`; `-Xlint:all -Werror`.
- CI en GitHub Actions: `./gradlew build` en Ubuntu y Windows.
- D-22 cerrada con unidades finas (ver `docs/ARCHITECTURE.md` §4.6).
- Problemas encontrados y resueltos: google-java-format 1.37.0 no funciona con
  Spotless 8.10.3 (se usa la versión que fija Spotless); el backend headless
  de libGDX 1.14.2 ya no trae GL simulado (el test instala uno vacío).

Comprobado aquí: con Xvfb y GL por software (Mesa), el lanzador abre y pinta
el área de 1280×720 con el color de fondo durante 20 s sin errores. Eso no
sustituye la prueba en Windows con GPU real.

## Fase 2 (hecha)

- Intérprete propio en `script` (Java puro, sin dependencias): lexer →
  parser → AST → checker que emite el árbol ejecutable → `CompiledScript`.
- Sintaxis del MVP: `if/else` y comparaciones, variables (`int`, `boolean`,
  `Enemigo`), aritmética, `++`/`+=`…, `&& || !`, `while`, y las 13 llamadas de
  la torreta. `for`, métodos, `try/catch` y clases se reconocen y dan «Aún no
  has desbloqueado …» sin interpretarse a medias.
- Errores de compilación con línea y columna (38 códigos, textos en
  `i18n/messages.properties`). Excepciones del juego con línea:
  SinMunicion, Sobrecalentamiento, NullPointer, BucleInfinito y Arithmetic.
- Memoria por sentencias con recuento por línea para el editor.
- Vocabulario y límites en `assets/data/script-api.json`, cargado y validado
  por `ScriptApiLoader` (core-sim, con Jackson 2.22.3).
- Tests (248 en total, 224 del intérprete): cada construcción, cada código de
  error, cada excepción, desbloqueos, memoria, entradas aleatorias,
  anidamiento extremo, 0 bytes reservados por ciclo y comparación con el
  `javac` real (lo que el juego acepta, javac también).
- Fallos encontrados por los propios tests y corregidos: un nodo nulo en
  scripts con errores y un desbordamiento de pila con `x = x = x = …`.

## Fase 3 (hecha)

- Datos de balance en `assets/data/` (`balance`, `turret`, `enemies`,
  `waves`, `shop`, `map`), leídos con Jackson en modo estricto (falta un
  campo, sobra uno o hay un valor fuera de rango → error con fichero y
  campo) y validados entre sí (cada oleada suma `min(6 + 2n, 60)`, jefe cada
  10, enjambres en grupos de 10, el script inicial compila…).
- `core-sim`: fórmulas y dinero en centésimas; `Battle` simula una oleada
  completa (torretas que ejecutan el script del jugador, calor, cargador,
  recarga, cuelgues, balas guiadas, blindaje, perforante, regeneración, jefe
  que alterna blindaje, fugas y derrota) y devuelve el informe de la oleada;
  `Progress`, `Shop` y `Progression` aplican tienda, pago, oleada perfecta,
  ×3, avance automático y derrota.
- `sim-cli` juega el MVP con un jugador de referencia y escribe
  `docs/BALANCE.md`: economía, partida simulada, muros, tiempo, ritmo de
  compras, regla del doble, contradicciones y avisos.
- 291 tests. Nuevos: economía contra la tabla del plan, cada regla de la
  torreta y de los enemigos, determinismo, cero reservas por tick, progreso y
  tienda, datos rotos, y que `BALANCE.md` esté al día.

### Resultado del simulador (detalle en `docs/BALANCE.md`)

- El jugador de referencia derrota al Jefe 1 en **22 oleadas jugadas** y
  **16,4 min** de juego (más el tiempo de escribir código; el plan apunta a
  20–30 min en total).
- Muros en las oleadas 9 y 10, con 1 y 5 pasadas: **cumple** la regla de 5.
- **La regla del doble no se cumple para ninguna pieza** (if 1,07; variables
  1,00; `&&` 0,40; while 0,75). Cada pieza mejora su propio script, pero el
  hardware del mismo precio mejora más: +25 % de daño cuesta 20 (menos que
  `if`) y la torreta extra cuesta 75 (menos que `&&`) y duplica el fuego.
- **Aviso**: con 20 vidas por oleada, las oleadas 1–6 no se pueden perder
  aunque no dispare ninguna torreta.
- **Aviso**: la oleada 1 con `disparar();` y recarga a mano sale perfecta;
  el plan quiere que «no des abasto».
- Nada de esto se ha ajustado: son datos para que decidas.

## Siguiente: fase 4

Juego: camino, torretas, enemigos y bucle de oleada. **Necesita que estés en
casa para probarla**; no empiezo sin que me lo digas. Antes conviene decidir
qué hacer con la regla del doble.

## Decisiones por revisar

Propuestas mías aplicadas como PROVISIONAL (detalle en
`docs/ARCHITECTURE.md`, sección 12). Ninguna contradice el plan.

- **D-01b**: con avance automático encendido, una oleada ya superada se
  encadena sin pausa.
- **D-02**: avance automático encendido en partida nueva.
- **D-03b (cambiada en la fase 3)**: la recarga a mano se puede hacer también
  con la torreta colgada. Prohibirla dejaba la oleada 1 sin salida (el
  cuelgue dura justo 6 ciclos y la torreta vuelve a colgarse al despertar).
- **D-05**: `disparar()` sin enemigos en rango gasta bala y calor; «más
  cercano» = a la torreta.
- **D-09**: perforante solo atraviesa blindaje.
- **D-10**: jefe paga 549 el primer derribo; después, como 20 enemigos
  normales de la oleada 10.
- **D-11**: dinero en centésimas; la UI muestra enteros hacia abajo.
- **D-13**: hardware aditivo y global.
- **D-14**: cada torreta empieza con `disparar();`.
- **D-17 (cambiada en la fase 3)**: regla del doble medida con el factor de
  vida `k` con el que la oleada sale **sin fugas** (con «superada» se saturaba).
- **D-18**: id interno `java-wave-defense` y paquete
  `io.github.yeray06.wavedefense`.
- **D-21**: con Variables también `--`, `-=`, `*=`, `/=`, `%=`.
- **D-23**: asignaciones, `++` y `--` solo como instrucción propia
  (`if ((x = 3) > 2)` y `y = x++` dan error, aunque Java los permita).
- **D-24**: `break`, `continue`, `switch`, `do`, `throw` no están en el
  juego; `return` cuenta como «métodos» y los arrays como «for y listas».
- **D-25**: el límite de 1.000 vueltas es por cada entrada en un `while`; un
  presupuesto de 100.000 pasos por ciclo cubre los bucles anidados.
- **D-26**: `apuntar(null)` lanza NullPointerException.
- **D-27**: no se comprueba el código inalcanzable (javac sí).
- **D-28**: un solo «Aún no has desbloqueado X» por pieza y línea.
- **D-29**: el calor se disipa y la recarga avanza durante un cuelgue.
- **D-30**: el jefe empieza sin blindaje y no cobra por baja (solo D-10).
- **D-31**: `hayEnemigos()` y `enemigoMasCercano()` solo ven enemigos en rango.
- **D-32**: orden de aparición de `waves.json`, huecos repartidos en 20 s.
- **D-33**: jugador de referencia del simulador (política de compra, 1 s de
  reacción). Solo afecta al informe.

Números de balance que no vienen del plan (D-12): están en la lista
`notFromPlan` de cada JSON y en la sección 2 de `docs/BALANCE.md`.

## Pendiente de probar en local

- [ ] `gradlew.bat :lwjgl3:run` en Windows abre una ventana de 1280×720 con
  fondo gris muy oscuro y título «Defensa por oleadas».
- [ ] La ventana se cierra con la X sin errores en consola.
- [ ] Importar el repo en IntelliJ IDEA como proyecto Gradle y ejecutar
  `Lwjgl3Launcher` con *Working directory* = `assets/`.
- [ ] (Si hay un Mac a mano) el `StartupHelper` relanza con
  `-XstartOnFirstThread` y la ventana abre.
