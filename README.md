# java-wave-defense

Defensa por oleadas en la que el jugador programa sus torretas en un
subconjunto de Java. Diseño: [`docs/plan-diseno.pdf`](docs/plan-diseno.pdf).
Arquitectura: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Estado:
[`docs/PROGRESS.md`](docs/PROGRESS.md).

## Requisitos

- JDK 21 (Temurin recomendado). La versión está en [`.java-version`](.java-version);
  Gradle y la CI la leen de ahí.
- Nada más: el wrapper de Gradle descarga Gradle y las dependencias.

## Ejecutar

```bash
./gradlew :lwjgl3:run          # Windows: gradlew.bat :lwjgl3:run
```

En IntelliJ IDEA: abrir la carpeta del repo como proyecto Gradle y ejecutar
`Lwjgl3Launcher` (módulo `lwjgl3`) con *Working directory* = `assets/`.

## Testear

```bash
./gradlew build                # compila, pasa los tests y comprueba el formato
./gradlew test                 # solo tests
./gradlew spotlessApply        # formatea el código (google-java-format)
```

## Empaquetar

Llega en la fase 7 (Construo, con JRE incluido). Mientras tanto,
`./gradlew :lwjgl3:installDist` deja un lanzador en
`lwjgl3/build/install/java-wave-defense/bin/` que necesita Java instalado.

## Módulos

| Módulo | Qué es |
|---|---|
| `script` | Intérprete del Java del jugador. Java puro. |
| `core-sim` | Simulación determinista. Java puro. |
| `platform` | Carpetas del SO y servicios de tienda (Steam). Java puro. |
| `game` | El juego con libGDX. |
| `lwjgl3` | Lanzador de escritorio. |
| `sim-cli` | Simulador de balance sin gráficos. |
