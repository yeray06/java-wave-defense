# Progreso

## Fases

| Fase | Estado | Rama |
|---|---|---|
| 0. Propuesta: árbol, decisiones y dudas | ✅ Fusionada | `phase-0-propuesta` |
| 1. Esqueleto Gradle, lanzador y CI | ✅ Hecha, en PR | `phase-1-esqueleto` |
| 2. Intérprete con tests | ⏳ Siguiente | — |
| 3. core-sim y sim-cli, `docs/BALANCE.md` | Pendiente | — |
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

## Siguiente: fase 2

Intérprete con sus tests (lexer → parser → AST → checker → árbol ejecutable).

## Decisiones por revisar

Propuestas mías aplicadas como PROVISIONAL (detalle en
`docs/ARCHITECTURE.md`, sección 12). Ninguna contradice el plan.

- **D-01b**: con avance automático encendido, una oleada ya superada se
  encadena sin pausa.
- **D-02**: avance automático encendido en partida nueva.
- **D-03 (recarga a mano)**: igual que `recargar()`; no se puede con la
  torreta colgada.
- **D-05**: `disparar()` sin enemigos en rango gasta bala y calor; «más
  cercano» = a la torreta.
- **D-09**: perforante solo atraviesa blindaje.
- **D-10**: jefe paga 549 el primer derribo; después, como 20 enemigos
  normales de la oleada 10.
- **D-11**: dinero en centésimas; la UI muestra enteros hacia abajo.
- **D-13**: hardware aditivo y global.
- **D-14**: cada torreta empieza con `disparar();`.
- **D-17**: regla del doble medida con el factor de vida `k`.
- **D-18**: id interno `java-wave-defense` y paquete
  `io.github.yeray06.wavedefense`.
- **D-21**: con Variables también `--`, `-=`, `*=`, `/=`, `%=`.

Números de balance que no vienen del plan (D-12): ver
`docs/ARCHITECTURE.md` 10.1 y 10.2; irán marcados en JSON y en
`docs/BALANCE.md`.

## Pendiente de probar en local

- [ ] `gradlew.bat :lwjgl3:run` en Windows abre una ventana de 1280×720 con
  fondo gris muy oscuro y título «Defensa por oleadas».
- [ ] La ventana se cierra con la X sin errores en consola.
- [ ] Importar el repo en IntelliJ IDEA como proyecto Gradle y ejecutar
  `Lwjgl3Launcher` con *Working directory* = `assets/`.
- [ ] (Si hay un Mac a mano) el `StartupHelper` relanza con
  `-XstartOnFirstThread` y la ventana abre.
