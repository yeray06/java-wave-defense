# java-wave-defense

Defensa por oleadas en la que el jugador programa sus torretas en un
subconjunto de Java. Diseño: [`docs/plan-diseno.pdf`](docs/plan-diseno.pdf).
Arquitectura: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Estado:
[`docs/PROGRESS.md`](docs/PROGRESS.md). Historia del proyecto, con capturas:
[`docs/DIARIO.md`](docs/DIARIO.md). Balance: [`docs/BALANCE.md`](docs/BALANCE.md).

## Requisitos

- JDK 21 (Temurin recomendado). La versión está en [`.java-version`](.java-version);
  Gradle y la CI la leen de ahí.
- Nada más: el wrapper de Gradle descarga Gradle y las dependencias.

## Ejecutar

```bash
./gradlew :lwjgl3:run          # Windows: gradlew.bat :lwjgl3:run
```

En Windows, con el repo clonado, basta con **doble clic en `jugar.bat`**: baja
lo último de `main` con Git y abre el juego. Si no hay internet o Git, juega
con lo que ya hay; si falta Java, dice qué instalar. Para tenerlo en el
escritorio: clic derecho sobre `jugar.bat` → *Enviar a* → *Escritorio (crear
acceso directo)*.

En IntelliJ IDEA: abrir la carpeta del repo como proyecto Gradle y ejecutar
`Lwjgl3Launcher` (módulo `lwjgl3`) con *Working directory* = `assets/`.

## Testear

```bash
./gradlew build                # compila, pasa los tests y comprueba el formato
./gradlew test                 # solo tests
./gradlew spotlessApply        # formatea el código (google-java-format)
```

## Empaquetar

Un zip por sistema con el juego y su propio Java 21 (no hace falta tener Java
instalado para jugar). Se generan desde cualquier sistema con
[Construo](https://github.com/fourlastor-alexandria/construo):

```bash
./gradlew :lwjgl3:packageWinX64      # Windows x64
./gradlew :lwjgl3:packageLinuxX64    # Linux x64
./gradlew :lwjgl3:packageMacM1       # macOS Apple Silicon
./gradlew :lwjgl3:packageMacX64      # macOS Intel
```

Los zips quedan en `build/dist/`. Para jugar: descomprimir y abrir
`java-wave-defense.exe` (Windows), `java-wave-defense` (Linux) o
`Java Wave Defense.app` (macOS; sin firmar, la primera vez: clic derecho →
Abrir).

Sin compilar nada: en GitHub, *Actions* → *Package* → *Run workflow*. Al acabar,
los zips están en *Artifacts* de esa ejecución (14 días). También se lanza
solo al subir una etiqueta `v*`.

El Java que va dentro es Temurin, descargado de sus *releases* de GitHub; su
versión exacta está en `lwjgl3/build.gradle.kts` (`runtimeRelease`) y tiene
que ser de la misma versión mayor que `.java-version`. Para Steam, ver
[`docs/STEAM.md`](docs/STEAM.md).

## Balance

```bash
./gradlew :sim-cli:run --args="--write docs/BALANCE.md"
```

Juega el MVP entero con un jugador de referencia y reescribe
[`docs/BALANCE.md`](docs/BALANCE.md). Hay que hacerlo cada vez que cambia un
JSON de `assets/data/` (un test lo comprueba).

## Módulos

| Módulo | Qué es |
|---|---|
| `script` | Intérprete del Java del jugador. Java puro. |
| `core-sim` | Simulación determinista. Java puro. |
| `platform` | Carpetas del SO y servicios de tienda (Steam). Java puro. |
| `game` | El juego con libGDX. |
| `lwjgl3` | Lanzador de escritorio. |
| `sim-cli` | Simulador de balance sin gráficos. |
