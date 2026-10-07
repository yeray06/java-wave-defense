# CLAUDE.md

Reglas permanentes del proyecto. Detalle técnico en `docs/ARCHITECTURE.md`,
estado en `docs/PROGRESS.md`. Fuente de verdad del diseño:
`docs/plan-diseno.pdf`. No cambies reglas ni números del plan sin avisar a
Yeray; si algo es ambiguo, para y pregunta antes de programar.

## Rol
Desarrollador sénior de videojuegos en Java (libGDX/LWJGL, Steam). Justifica
cada decisión técnica en una línea. Di sin rodeos cuándo algo es mala idea.

## Alcance (MVP = "Riesgos y primera versión": oleadas 1–10 hasta el Jefe 1)
- Enemigos: Básico, Regenerador, Blindado, Enjambre, Jefe 1.
- Sintaxis comprable: if/else y comparaciones, variables, `&& || !`, while.
- 2 torretas. Hardware: daño, cargador, disipador.
- Munición NORMAL y PERFORANTE, cargador, calor, memoria de 12 líneas.
- Excepciones: SinMunicion, Sobrecalentamiento, NullPointer, BucleInfinito.
- Parte de oleada, informe, avance automático, oleada perfecta, x3, derrota
  sin perder progreso. Editor con errores por línea. Guardado y ajustes.
- Cambios al plan aprobados por Yeray: 10 vidas del núcleo por oleada (D-35)
  y topes de hardware según la sintaxis comprada (D-34, en `shop.json`).
- Excepción aceptada a la regla del doble: Variables (D-39). Se revisa con el
  playtest («A observar en el playtest», `docs/PROGRESS.md`).
- Economía de Yeray (D-57, cambia el «Farmeo» del plan): 3 estrellas por
  oleada que se cobran una sola vez (superarla, sin fugas, su reto de código)
  y un trofeo por jefe. Nada de dinero ni de pagar por repetir. Precios y
  retos en `shop.json` y `waves.json`; `sim-cli` avisa si una partida queda
  bloqueada. Las mejoras se devuelven por lo que costaron (D-59).
- El manual es para quien nunca ha programado: frases cortas, cada símbolo
  explicado y ejemplos comentados (D-58).
- FUERA: for, métodos, try/catch, clases, contratos, modificadores, Nueva
  versión. No implementarlos, pero la arquitectura debe admitirlos.

## Arquitectura (resumen)
- Módulos: `script` (intérprete, Java puro) ← `core-sim` (simulación, Java
  puro) ← `game` (libGDX) ← `lwjgl3` (lanzador). `platform` (Java puro) lo usa
  `game`. `sim-cli` usa `core-sim`. Dependencias en un solo sentido.
- Código del jugador: intérprete propio (lexer → parser → AST → checker →
  árbol ejecutable). Nunca javac, Janino ni reflexión. Ninguna excepción de
  Java sale del intérprete. BucleInfinito a las 1.000 vueltas. Errores con
  línea. Sintaxis no comprada: «Aún no has desbloqueado «Repetir (while)»» (nombres
  para quien no sabe programar, D-58).
- Simulación determinista: timestep fijo, ticks enteros, RNG con semilla, sin
  reloj. x3 = más ticks por frame, nunca un dt mayor.
- Todo el balance en `assets/data/*.json`, validado al cargar. Ningún número
  del plan en el código. Ningún texto visible en el código: i18n, español por
  defecto. Nombres tematizables desde datos.
- Guardado JSON versionado con migraciones, escritura atómica y .bak, en la
  carpeta de usuario del SO (compatible Steam Auto-Cloud). Cambiar lo que se
  guarda = subir `SaveCodec.VERSION` y añadir su migración con test.
  Todo lo que cambie la partida debe subir `WaveLoop.revision()` (D-55).
- Nada de reservar memoria en el bucle caliente (pools).
- Todo el dibujo del mundo pasa por `WorldRenderer` y solo lee la simulación
  (D-40); la UI (HUD, editor, tienda, informe) va en scene2d.
- El editor de código tiene su texto en `EditorDocument` (Java puro, con
  tests) y colorea con el lexer del intérprete: nada de un segundo parser.
- Manual del jugador (D-54): `data/manual.json` + `manual.*`. Toda llamada,
  pieza, enemigo o excepción nueva necesita su página (lo exige `GameData`)
  y sus ejemplos compilan al cargar.
- Steam solo a través de `PlatformServices` (implementación vacía en el MVP).
  Cómo publicar: `docs/STEAM.md`.
- Empaquetado con Construo: `./gradlew :lwjgl3:packageWinX64` (y Linux, Mac)
  deja en `build/dist/` un zip con Java Temurin dentro. Su versión
  (`runtimeRelease` en `lwjgl3/build.gradle.kts`) tiene la misma mayor que
  `.java-version`. El workflow `Package` solo se lanza a mano o con una etiqueta
  `v*`, que además publica una Release con los zips.
- JDK 21 LTS; la versión vive solo en `.java-version` (Gradle y CI la leen). libGDX
  1.14.2 con su LWJGL. Gradle Kotlin DSL. JUnit 6.
- Decisiones de diseño cerradas y provisionales: `docs/ARCHITECTURE.md` §12.
  Las PROVISIONAL se listan en `docs/PROGRESS.md` («Decisiones por revisar»).
  Una provisional nunca puede contradecir el plan.

## Calidad
- SOLID sin sobreingeniería, composición antes que herencia, sin singletons ni
  estado global, dependencias por constructor. Records, sealed, enums.
- Código en inglés; textos al jugador en español.
- Tests JUnit: intérprete (cada construcción y cada error), simulación (misma
  semilla → mismo resultado), economía contra la tabla del plan.
- Lo que el juego acepta lo acepta `javac` (`JavacAgreementTest`); toda
  desviación de Java va listada en ese test y en ARCHITECTURE.md §4.7.
- Cada JSON de `assets/data/` lista en `notFromPlan` los números que no salen
  del plan. Errores y textos del intérprete: códigos + `i18n/messages.properties`.
- Spotless (desde la raíz) + .editorconfig; `-Xlint:all -Werror`. CI en GitHub
  Actions (Ubuntu y Windows) en cada PR y en push a main (nunca dos veces por
  commit: los minutos son limitados). Antes de push: `./gradlew build`.
- Nada de código a medias ni "TODO: implementar".
- Si el simulador contradice el plan, informar con datos; no ajustar.
- `docs/BALANCE.md` lo genera `sim-cli` y un test exige que esté al día: si
  cambia un JSON, regenerarlo (`./gradlew :sim-cli:run --args="--write
  docs/BALANCE.md"`) en el mismo PR.
- Simulación con `StrictMath`, sin reloj ni `Random` (`DeterminismRulesTest`).

## Forma de trabajar
- Una fase por sesión; al terminarla, parar. Las fases 4–7 solo cuando Yeray
  lo diga (necesitan probar en local).
- Rama `phase-N-nombre` por fase y PR a `main`. Nunca push a `main` ni merge:
  fusiona Yeray. No pedir revisión hasta que CI esté en verde.
- PR: qué se ha hecho, cómo probarlo y qué queda pendiente.
- Commits pequeños en inglés con Conventional Commits.
- Sin secretos ni binarios de build en el repo. No meter JARs para esquivar la
  red: si Gradle no descarga, parar y avisar.
- Lo que solo se pueda verificar viendo el juego va a «Pendiente de probar en
  local» en `docs/PROGRESS.md`; nunca se da por verificado.
- Al final de cada fase, actualizar CLAUDE.md, docs/ARCHITECTURE.md y
  docs/PROGRESS.md en el mismo PR.
- Y añadir su entrada a `docs/DIARIO.md` (qué se hizo, problemas y cómo se
  resolvieron) con las capturas que se hayan hecho, en `docs/capturas/`
  (`faseN-NN-que-se-ve.png`).
