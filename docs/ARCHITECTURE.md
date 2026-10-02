# Arquitectura

Documento vivo. Cada decisión lleva su motivo en una línea. La fuente de verdad
del diseño es [`plan-diseno.pdf`](plan-diseno.pdf); si algo de aquí lo
contradice, manda el plan y hay que avisar.

Estado: **propuesta de la fase 0**. Las secciones marcadas con ❓ están
pendientes de que Yeray las confirme (ver «Dudas abiertas» al final).

---

## 1. Stack y versiones

Comprobado el 2026-10-02 contra Maven Central y el portal de plugins de Gradle.

| Pieza | Versión | Motivo |
|---|---|---|
| JDK | 25 LTS (Temurin), fallback 21 | Pedido en el prompt. Records, sealed, `switch` con patrones. |
| libGDX | 1.14.2 (última publicada) | Pedido. Trae LWJGL 3.3.3. |
| Gradle | 9.8.0 (wrapper) | Última estable; ejecuta y compila con Java 25. |
| DSL de Gradle | Kotlin DSL + catálogo `gradle/libs.versions.toml` | Autocompletado en IntelliJ y versiones en un único sitio. ⚠️ gdx-liftoff genera Groovy: mantengo su estructura de módulos y carpetas, no su DSL. |
| JSON (módulos puros) | Jackson databind 2.22.x | `core-sim` no puede usar `Json` de libGDX; Jackson soporta records y da línea/columna en errores de datos. |
| Tests | JUnit Jupiter ❓ 6.1.x (o 5.14.4) | JUnit 6 es la versión actual y tiene la misma API Jupiter que la 5. Ver duda D-16. |
| Formato | Spotless 8.x + google-java-format | Formato automático, sin discusiones de estilo. |
| Empaquetado | Construo 2.2.x (plugin Gradle) | Genera Windows/Linux/macOS con JRE recortado **desde Linux**; jpackage obliga a ejecutar en cada SO. |
| Fuente | JetBrains Mono (OFL 1.1) vía gdx-freetype | Monoespaciada, libre, nítida a cualquier escala. Se incluye `OFL.txt`. |

Avisos de compatibilidad:

- Java 24+ avisa del acceso nativo de LWJGL (JEP 472). El lanzador añadirá
  `--enable-native-access=ALL-UNNAMED` para que no salga el aviso ni falle en
  el futuro.
- macOS necesita `-XstartOnFirstThread`; se usa el `StartupHelper` que genera
  gdx-liftoff.
- ⚠️ **Red de la sesión cloud**: Maven Central y el portal de plugins
  responden, pero `api.foojay.io`, `api.adoptium.net` y `github.com`
  (descargas de JDK) están bloqueados y en el contenedor solo hay JDK 21. Sin
  esos dominios Gradle no puede descargar el JDK 25 por toolchain. Ver D-15.

## 2. Árbol de carpetas

```
java-wave-defense/
├── .editorconfig
├── .gitattributes                 # LF en el repo, CRLF solo en gradlew.bat
├── .gitignore
├── .github/workflows/ci.yml       # build + tests + spotlessCheck en cada push y PR
├── CLAUDE.md
├── README.md                      # ejecutar, testear y empaquetar
├── build.gradle.kts               # convenciones comunes (toolchain, Spotless, JUnit)
├── settings.gradle.kts            # incluye los 6 módulos
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── gradlew, gradlew.bat
├── assets/                        # convención gdx-liftoff; la leen game y sim-cli
│   ├── data/
│   │   ├── balance.json           # fórmulas: vida, dinero, nº enemigos, núcleo, bala
│   │   ├── enemies.json           # tipos de enemigo y sus multiplicadores
│   │   ├── waves.json             # composición de las oleadas 1–10 y jefe
│   │   ├── turret.json            # cargador, calor, ciclos, memoria, recarga, cuelgue, rango
│   │   ├── shop.json              # árbol de sintaxis y hardware (precios, requisitos)
│   │   ├── script-api.json        # nombre visible y desbloqueo de cada llamada
│   │   ├── map.json               # camino y huecos de torreta
│   │   └── names.json             # nombres visibles tematizables (enemigos, torretas…)
│   ├── i18n/
│   │   └── messages.properties    # español (por defecto); messages_en.properties más tarde
│   └── fonts/
│       ├── JetBrainsMono-Regular.ttf
│       └── OFL.txt
├── script/                        # intérprete. Java puro, sin dependencias
│   └── src/{main,test}/java/io/github/yeray06/wavedefense/script/
│       ├── lexer/                 # Token, TokenType, Lexer
│       ├── ast/                   # sealed Node/Stmt/Expr como records
│       ├── parser/                # Parser descendente recursivo
│       ├── check/                 # Checker: desbloqueos, tipos, ámbitos, conteo de memoria
│       ├── runtime/               # Compiler (AST → árbol ejecutable tipado), Executor, presupuesto
│       ├── api/                   # TurretApi, EnemyRef, AmmoType, ApiCatalog, Feature
│       └── diag/                  # CompileError, ScriptFault, códigos de error
├── core-sim/                      # simulación determinista. Java puro (+ Jackson)
│   └── src/{main,test}/java/io/github/yeray06/wavedefense/sim/
│       ├── data/                  # records de balance + carga y validación de JSON
│       ├── world/                 # Simulation, PathTrack, Enemy, Bullet, pools, Core
│       ├── turret/                # Turret, Heat, Magazine, TurretApiAdapter
│       ├── wave/                  # WaveDirector, SpawnSchedule, WaveReport
│       ├── economy/               # Money (punto fijo), recompensas
│       ├── shop/                  # Shop, compras de sintaxis y hardware
│       ├── progress/              # Profile, reglas de avance/derrota/x3
│       ├── save/                  # SaveData versionado, migraciones, códec JSON
│       └── rng/                   # SplitMix64 con semilla
├── platform/                      # servicios del SO / Steam. Java puro
│   └── src/{main,test}/java/io/github/yeray06/wavedefense/platform/
│       ├── PlatformServices.java  # logros, nube…
│       ├── NoOpPlatformServices.java
│       ├── UserDataDirectory.java # carpeta de usuario por SO (compatible Auto-Cloud)
│       └── AtomicFileStore.java   # temporal + renombrar + .bak
├── game/                          # libGDX (equivale al "core" de gdx-liftoff)
│   └── src/{main,test}/java/io/github/yeray06/wavedefense/game/
│       ├── WaveDefenseGame.java   # composición raíz: crea y conecta todo
│       ├── loop/                  # FixedStepClock (acumulador, x1/x3)
│       ├── screen/                # Briefing, Battle, Report, Shop, Settings
│       ├── render/                # ShapeRenderer: camino, enemigos, balas, barras
│       ├── ui/                    # scene2d.ui, skin generado por código
│       ├── editor/                # CodeEditor (widget propio), resaltado y errores por línea
│       ├── audio/                 # sonidos sintetizados por código propio
│       ├── input/
│       ├── i18n/                  # Texts: envoltorio de I18NBundle
│       ├── settings/              # Settings persistidos
│       └── log/                   # handler global y log rotativo
├── lwjgl3/                        # lanzador de escritorio + configuración de Construo
│   └── src/main/java/io/github/yeray06/wavedefense/lwjgl3/
│       ├── Lwjgl3Launcher.java
│       └── StartupHelper.java
├── sim-cli/                       # simulador sin gráficos
│   └── src/main/
│       ├── java/io/github/yeray06/wavedefense/simcli/
│       │   ├── SimMain.java
│       │   ├── ReferencePlayer.java   # política de compra y avance de un jugador tipo
│       │   ├── WallReport.java        # pasadas por muro
│       │   └── DoubleRuleReport.java  # regla del doble
│       └── resources/reference-scripts/*.txt
└── docs/
    ├── plan-diseno.pdf
    ├── ARCHITECTURE.md
    ├── PROGRESS.md
    ├── BALANCE.md                 # fase 3
    └── STEAM.md                   # fase 7
```

Paquete raíz `io.github.yeray06.wavedefense` (convención de gdx-liftoff para
quien no tiene dominio propio).

## 3. Módulos y dependencias

```
lwjgl3 ──► game ──► core-sim ──► script
             │                     ▲
             ├──► platform         │
             └──────────────────────┘
sim-cli ──► core-sim ──► script
```

- **script** no depende de nadie. Define la interfaz cerrada `TurretApi` que
  necesita; `core-sim` la implementa. Así el intérprete se prueba con dobles
  sin simulación.
- **core-sim** no conoce libGDX ni el disco: recibe los JSON ya leídos como
  texto o `InputStream` y devuelve modelos validados.
- **platform** sabe de carpetas del SO y ficheros, no del juego.
- **game** es la raíz de composición: construye todo por constructor. Sin
  singletons ni estáticos con estado.
- Gradle lo hace cumplir: un módulo no puede importar lo que no declara.

## 4. El intérprete (módulo `script`)

### 4.1 Por qué uno propio

Ni `javac` ni Janino ni reflexión: el código del jugador no puede tocar nada
fuera de la API de la torreta y no puede colgar el juego. Un intérprete propio
lo garantiza por construcción y además permite mensajes como «Aún no has
desbloqueado while».

### 4.2 Tubería

```
texto ─► Lexer ─► tokens ─► Parser ─► AST ─► Checker ─► Compiler ─► árbol ejecutable
                                                │
                                                └─► CompileError(código, línea, columna, args)
```

- **Lexer**: reconoce todas las palabras reservadas de Java, también las
  que aún no se usan (`for`, `try`, `class`…). Comentarios `//` y `/* */`.
- **Parser**: descendente recursivo, con precedencias de Java. Recuperación en
  modo pánico (sincroniza en `;` y `}`) para dar varios errores a la vez.
  Si encuentra una construcción conocida pero no implementada en el MVP
  (`for`, `try`, `class`, declaración de método) emite
  `FEATURE_LOCKED` y no la interpreta a medias.
- **AST**: `sealed interface Stmt` / `sealed interface Expr` con records. Cada
  nodo guarda su línea y columna. Añadir `for`, `try/catch`, métodos y clases
  es añadir records y casos al `switch`; el compilador de Java avisa de los
  `switch` que se quedan sin cubrir.
- **Checker**: una pasada que resuelve nombres y ámbitos, comprueba tipos
  estáticos (`int`, `double`, `boolean`, `Enemigo`, `Municion`), aplica los
  desbloqueos y cuenta la memoria.
- **Compiler**: convierte el AST comprobado en un árbol de nodos ejecutables
  tipados (`IntNode.eval()`, `BoolNode.eval()`, `RefNode.eval()`…) con las
  variables en ranuras de arrays primitivos. Motivo: cero reservas de memoria
  por ciclo (nada de `Integer` ni `Object[]` de valores) y sin comprobaciones
  de tipo en tiempo de ejecución.

### 4.3 Desbloqueos (`Feature`)

`enum Feature { IF_ELSE, VARIABLES, LOGIC_OPS, WHILE, FOR, METHODS, TRY_CATCH, CLASSES }`.
El checker recibe el conjunto comprado; el MVP solo vende los cuatro primeros.

| Pieza | Qué habilita (propuesta ❓ D-07) |
|---|---|
| Gratis | Sentencias `disparar();` y `recargar();`, comentarios. |
| if/else y comparaciones | `if`, `else`, `else if`, `== != < > <= >=`, paréntesis, literales `int`/`boolean`; llamadas `municion()`, `calor()`, `hayEnemigos()`. |
| Variables | Declaraciones (`int`, `double`, `boolean`, `Enemigo`), asignación, `null`, aritmética `+ - *` (`/` y `%`: D-08); llamadas `enemigoMasCercano()`, `apuntar(e)`, `e.vida()`, `e.velocidad()`, `e.blindado()`, `e.distanciaAlNucleo()`. |
| `&&`, `||`, `!` | Esos tres operadores. |
| while | `while`. |
| Oleada 6 (no se compra) | `usarMunicion(NORMAL)` / `usarMunicion(PERFORANTE)`. |

Usar algo no desbloqueado da error de compilación con el texto del plan:
«Aún no has desbloqueado while». Para llamadas: «Aún no has desbloqueado
`municion()`: llega con if/else».

### 4.4 Qué cuenta como línea de memoria ❓ D-06

Propuesta: **una línea = una sentencia**, no una línea física.

- Cuentan 1: cada declaración, asignación, llamada, cabecera de `if`
  (un `else if` es otro `if`) y cabecera de `while`.
- Cuentan 0: `else`, llaves, líneas en blanco y comentarios.

Motivo: si contaran líneas físicas, el jugador metería todo en una línea y el
límite dejaría de existir. Con sentencias, el formato da igual. El ejemplo del
plan con `if/else` ocupa 3 líneas de memoria; el de `for` de la oleada 12,
7. El editor muestra el contador `7/12` y marca qué líneas cuentan.

### 4.5 Presupuesto y errores de ejecución

- El script se ejecuta entero, de forma síncrona, en el tick de simulación en
  que toca el ciclo. La simulación no avanza mientras corre, así que el
  resultado es determinista.
- **BucleInfinitoException**: cada `while` lleva un contador por ciclo; a la
  vuelta 1.001 salta (el plan dice «pasa de 1.000 vueltas»). Además hay un
  presupuesto global de pasos por ciclo (100.000 nodos, en `turret.json`) que
  también lanza BucleInfinito: cubre `while` anidados (999 × 999).
- **Profundidad**: el parser limita el anidamiento (64 niveles) para que
  ningún script provoque `StackOverflowError`.
- **Ninguna excepción de Java sale del intérprete**: las excepciones del juego
  son un `ScriptFault` (tipo + línea) que el ejecutor devuelve como resultado,
  no como `throw` hacia la simulación. Como red de seguridad, el `Executor`
  envuelve la ejecución en un `catch (RuntimeException | StackOverflowError)`
  que lo convierte en un fallo interno con línea, lo registra en el log y
  cuelga la torreta como cualquier otra excepción.
- Excepciones del MVP: `SinMunicionException`, `SobrecalentamientoException`,
  `NullPointerException`, `BucleInfinitoException`. ❓ D-08 sobre la división
  entre cero.

⚠️ **Diferencia con el plan**: la tabla de riesgos propone «ejecución en un
hilo aparte». Propongo **no** usar hilo: con el presupuesto de pasos ningún
script puede bloquear, y un hilo rompería el determinismo y complicaría
pausar, x3 y los tests. Si un día se compila a bytecode real, entonces sí haría
falta.

### 4.6 La API cerrada

```java
public interface TurretApi {           // lo único que ve un script
  void fire();  void reload();  int ammo();  int heat();  boolean hasEnemies();
  EnemyRef nearestEnemy();  void aim(EnemyRef e);  void useAmmo(AmmoType t);
  double health(EnemyRef e); double speed(EnemyRef e);
  boolean armored(EnemyRef e); double distanceToCore(EnemyRef e);
}
```

- `EnemyRef` es un asa opaca (índice + generación). Si el enemigo murió o el
  hueco del pool se reutilizó, la generación no coincide y salta
  NullPointerException, como en Java con una referencia muerta.
- Los nombres que ve el jugador (`disparar`, `Enemigo`, `PERFORANTE`…) salen
  de `script-api.json`, no del código, para poder cambiar el tema.
- No existe ninguna otra clase, ni `System`, ni `String`: `System.exit(0)` da
  «símbolo desconocido».

## 5. Simulación (módulo `core-sim`)

- **Timestep fijo** de 1/60 s; todo se cuenta en ticks enteros. Un ciclo de
  torreta = 30 ticks (2 por segundo, sale de `turret.json`).
- **x3** = 3 ticks por frame de render. El `dt` nunca cambia. Si un frame se
  retrasa, el acumulador limita a 5 ticks por frame para evitar la espiral.
- **Determinismo**: sin reloj, sin `HashMap` iterado en la lógica, RNG
  SplitMix64 propio con semilla (no `java.util.Random`, cuyo algoritmo no es
  contrato entre JDK). Desde Java 17 el punto flotante es estricto en todas
  las plataformas. Test: misma semilla y mismos scripts → mismo hash de estado
  tras N ticks.
- **Pools**: enemigos y balas en arrays de capacidad fija preasignados
  (60 enemigos + jefe + margen; 512 balas). Nada de `new` en el tick.
- **Dinero en punto fijo** (`long` en centésimas): la fórmula da 2,3; 2,645…
  y con `double` acumularíamos errores al repetir oleadas. La UI muestra
  enteros. ❓ D-11.
- **Datos**: cada JSON se valida al cargar (rangos, referencias cruzadas, que
  la composición de cada oleada sume `min(6 + 2n, 60)`). Un dato inválido da
  un error con fichero y campo, nunca un valor por defecto silencioso.

## 6. Juego (módulo `game`)

- `FitViewport` de 1280×720 para el mundo; `ScreenViewport` con escala de UI
  para scene2d. Ventana y pantalla completa, ajustes en `settings.json`.
- Render con `ShapeRenderer` (formas geométricas) y la fuente con freetype.
  El render solo lee el estado de la simulación.
- **Editor**: widget scene2d propio (el `TextArea` de libGDX no sirve para
  código: sin números de línea ni marcas). Números de línea, resaltado, marca
  roja en la línea con error y tooltip con el mensaje, contador de memoria.
  Es la pieza de UI con más riesgo: fase 5.
- **Textos**: `I18NBundle` con `messages.properties` en español. El código
  solo usa claves. Los módulos puros devuelven códigos + argumentos; quien
  pinta el texto es `game` (o `sim-cli`, que lee el mismo fichero).
- **Audio**: sonidos sintetizados por código propio, sin ficheros de terceros.

## 7. Guardado, ajustes y robustez

- **Ruta** (compatible con Steam Auto-Cloud):
  - Windows: `%APPDATA%\JavaWaveDefense\`
  - Linux: `$XDG_DATA_HOME/java-wave-defense/` (o `~/.local/share/…`)
  - macOS: `~/Library/Application Support/JavaWaveDefense/`
  - No uso las `Preferences` de libGDX: van a `~/.prefs/` y no son atómicas.
- **Formato**: `save.json` con `"version": N`. Al cargar se aplican las
  migraciones `vN → vN+1` en orden; cada una tiene test.
- **Escritura atómica**: escribir `save.json.tmp`, `fsync`, copiar el actual a
  `save.json.bak` y renombrar con `ATOMIC_MOVE` (si el sistema no lo soporta,
  `REPLACE_EXISTING`).
- **Carga tolerante**: si `save.json` está corrupto se prueba `.bak`; si
  también, partida nueva y aviso al jugador. El fichero roto se conserva como
  `save.json.corrupt-<fecha>` para depurar. El juego nunca se cierra por eso.
- **Logs**: `java.util.logging` con `FileHandler` rotativo (5 ficheros × 1 MB)
  en `<carpeta>/logs/`. Sin dependencia extra. `Gdx.app` redirige ahí.
- **Handler global**: `Thread.setDefaultUncaughtExceptionHandler` y
  `try/catch` alrededor de `render()` que registra y muestra un aviso en vez
  de cerrar.

## 8. Steam (sin integrar)

Todo lo de Steam pasará por `PlatformServices` (`unlockAchievement`,
`isCloudEnabled`…); en el MVP, `NoOpPlatformServices`. La nube se hará con
Auto-Cloud (por rutas), así que el guardado no necesita API. `docs/STEAM.md`
se escribe en la fase 7.

## 9. Herramientas y CI

- Spotless con google-java-format, `.editorconfig` coherente con él.
- GitHub Actions: `./gradlew build` (compila, tests, `spotlessCheck`) en
  `ubuntu-latest`, y en `windows-latest` los tests de `platform` y `core-sim`
  (rutas y renombrado atómico se comportan distinto en Windows).
- Validación del wrapper de Gradle en CI.

## 10. Reglas del juego tal y como se implementarán

Lo que sale literal del plan va sin marca. Lo que el plan no fija y propongo
yo va con ❓ y su número de duda.

### 10.1 Torreta

| Regla | Valor | Origen |
|---|---|---|
| Ciclos por segundo | 2 | Plan |
| Cargador | 12 balas (+4 por nivel) | Plan |
| Calor | +20 por disparo NORMAL, +40 PERFORANTE, −30 por ciclo, máx. 100 | Plan |
| Memoria | 12 líneas (hardware de memoria fuera del MVP) | Plan |
| Daño de bala | 5 (+25 % por nivel) | Plan |
| `recargar()` | La torreta no hace nada durante 3 ciclos | Plan; detalle ❓ D-03 |
| Excepción sin capturar | Torreta colgada 3 s, error con línea encima | Plan |
| Orden en el ciclo | Disipar calor → ejecutar script | ❓ D-04 |
| `disparar()` con calor ≥ 100 | SobrecalentamientoException; con 90 dispara y queda en 100 | ❓ D-04 |
| `disparar()` sin enemigos en rango | Bala «al aire»: gasta bala y calor | ❓ D-05 |
| Objetivo por defecto | Enemigo vivo más cercano a la torreta dentro del rango | Plan («más cercano»); «a la torreta» ❓ D-05 |
| Objetivo fijado | `apuntar(e)` lo mantiene entre ciclos hasta que muere o sale de rango | ❓ D-20 |
| Rango | 200 u | ❓ D-12 |
| Balas | Proyectil guiado a 600 u/s; si el objetivo muere antes, bala perdida | ❓ D-12 (el informe de derrota cuenta «balas en enemigos ya muertos», así que no pueden ser instantáneas) |
| Perforante | Ignora la reducción del blindaje; un solo objetivo | ❓ D-09 |
| Script inicial de la 2.ª torreta | `disparar();` | ❓ D-14 |

### 10.2 Enemigos y oleadas

| Regla | Valor | Origen |
|---|---|---|
| Vida | `10 · 1,18^(n−1)` | Plan |
| Dinero por enemigo | `2 · 1,15^(n−1)` | Plan |
| Nº de enemigos | `min(6 + 2n, 60)` | Plan |
| Núcleo | 20 vidas por oleada; cada enemigo que llega quita 1 | Plan |
| Regenerador | Se cura si pasa 1 s sin daño; ritmo 25 % de vida máx./s | 1 s plan; ritmo ❓ D-12 |
| Blindado | −80 % de daño con NORMAL; velocidad ×0,8 | 80 % plan; velocidad ❓ |
| Enjambre | Grupos de 10, juntos; vida ×0,3, dinero ×0,3, velocidad ×1,2; cada miembro cuenta como 1 enemigo | 10 plan; resto ❓ D-12 |
| Jefe 1 | Vida = 20 × vida de la oleada 10; alterna blindaje cada 5 s; si llega al núcleo se pierde la oleada | Plan |
| Recompensa del jefe | 1.er derribo = 3 × dinero de la oleada 10; los siguientes ❓ | ❓ D-10 |
| Duración | ~45 s: camino de 1.500 u, velocidad base 60 u/s (25 s), salidas repartidas en 20 s | Objetivo del plan; números ❓ D-12 |

Composición propuesta (❓ D-12). B = Básico, R = Regenerador, Bl = Blindado,
E = Enjambre:

| Oleada | Total | Mezcla |
|---|---|---|
| 1 | 8 | 8 B |
| 2 | 10 | 10 B |
| 3 | 12 | 12 B |
| 4 | 14 | 10 B + 4 R |
| 5 | 16 | 10 B + 6 R |
| 6 | 18 | 8 B + 5 R + 5 Bl |
| 7 | 20 | 8 B + 6 R + 6 Bl |
| 8 | 22 | 6 B + 3 R + 3 Bl + 10 E |
| 9 | 24 | 4 B + 4 R + 6 Bl + 10 E |
| 10 | 26 | 6 B + 5 R + 5 Bl + 10 E + Jefe |

### 10.3 Economía, tienda y progreso

| Regla | Valor | Origen |
|---|---|---|
| Sintaxis MVP | if 25 · Variables 60 (req. if) · `&& || !` 100 (req. Variables) · while 200 (req. `&&`) | Plan |
| Árbol visible | Las 8 piezas con precio desde el inicio; las 4 de fuera del MVP, bloqueadas | Plan |
| Hardware MVP | Daño 20 ×1,6 · Cargador 15 ×1,5 · Disipador 40 ×1,6 | Plan |
| Efecto por nivel | Aditivo sobre la base: daño 5 → 6,25 → 7,5 | Plan («cada nivel… da lo mismo»); ❓ D-13 |
| Ámbito del hardware | Global, para todas las torretas | ❓ D-13 |
| Segunda torreta | «Torreta extra» a 75; tope 2 en el MVP | Plan |
| Oleada perfecta | +25 % del pago de la oleada si no se cuela nadie | Plan |
| Avance automático | Interruptor; apagado repite la oleada. Por defecto encendido | Plan; valor por defecto ❓ D-02 |
| Velocidad ×3 | Solo en oleadas superadas alguna vez sin fugas | Plan |
| Derrota | Cobras lo matado, conservas todo, vuelves a la oleada anterior (mín. 1) con avance automático apagado | Plan |
| Flujo entre oleadas | ❓ D-01 | |

## 11. Simulador (`sim-cli`)

- Juega las oleadas 1–10 con scripts de referencia (uno por nivel de sintaxis)
  y un `ReferencePlayer` con una política fija: compra la siguiente sintaxis
  en cuanto puede; si no, el hardware más barato; si pierde, farmea la
  anterior.
- **Pasadas por muro**: cuántas veces repite una oleada antes de superar la
  siguiente. Falla si alguna pasa de 5.
- **Regla del doble** (❓ D-17): para cada pieza de precio P en la oleada en
  que «llega», se compara (a) el script con la pieza frente a (b) el script
  sin ella más P gastado en el mejor hardware. Métrica: el mayor factor `k`
  de vida de enemigos con el que la oleada se supera (búsqueda binaria). Se
  cumple si `k(a) ≥ 2 · k(b)`.
- Salida en texto y en `docs/BALANCE.md`. Si contradice el plan, se informa
  con datos; no se ajusta nada por mi cuenta.

## 12. Dudas abiertas

Las que bloquean una fase están marcadas. Para cada una doy mi recomendación.

- **D-01 Flujo entre oleadas** *(bloquea fase 3)*. El bucle del plan tiene
  «Desplegar» como paso explícito, pero farmear es «dejar que el código repita
  la oleada mientras escribes». Propuesta: tras el informe, cuenta atrás de 5 s
  y despliegue automático, con botón «Esperar» y «Desplegar ya». Editor y
  tienda abiertos en todo momento; lo que cambies entra en la siguiente pasada
  (es la «edición en caliente» del plan, sale casi gratis).
- **D-02 Avance automático por defecto**: encendido. Es lo que espera alguien
  nuevo.
- **D-03 `recargar()`** *(bloquea fase 3)*. ¿Termina el ciclo en curso? ¿El
  cargador se llena al empezar o al acabar los 3 ciclos? Propuesta:
  `recargar()` corta el script en ese punto (sin excepción), la torreta no
  ejecuta los 3 ciclos siguientes y el cargador se llena al final. Llamarlo con
  el cargador lleno también cuesta los 3 ciclos.
- **D-04 Calor** *(bloquea fase 3)*. Propuesta: al empezar cada ciclo se disipa
  y luego corre el script; la excepción salta si el calor ya es 100 antes de
  disparar; un disparo con 90 se hace y deja 100. Así un disparo por ciclo es
  sostenible y una ráfaga en frío da 5 disparos.
- **D-05 Disparar sin objetivo y «más cercano»**. Propuesta: sin enemigos en
  rango, la bala sale al aire y gasta bala y calor (es el «disparar al aire»
  que te quita el `if`). «Más cercano» = a la torreta.
- **D-06 Qué cuenta como línea** *(bloquea fase 2)*: sentencias, ver 4.4.
- **D-07 Qué trae cada pieza** *(bloquea fase 2)*: tabla de 4.3. En concreto,
  ¿la aritmética llega con Variables? Sin ella no hay contadores en el `while`.
- **D-08 División entre cero** *(bloquea fase 2)*. Si entra `/`, Java lanza
  `ArithmeticException`, que no está en el plan. Opciones: (a) añadir
  `ArithmeticException` (real, como NullPointer); (b) dejar `/` y `%` fuera del
  MVP. Recomiendo (b): nada del MVP necesita dividir.
- **D-09 Perforante**: ¿solo atraviesa blindaje o también atraviesa enemigos?
  Propuesta: solo blindaje.
- **D-10 Recompensa del jefe**. «El primer derribo paga el equivalente a tres
  oleadas»: ¿tres oleadas 10 (3 × 183 = 549)? ¿Y los derribos siguientes al
  farmear la 10? Propuesta: 549 la primera vez; después, como 20 enemigos
  normales de la oleada.
- **D-11 Decimales del dinero**. Propuesta: se guarda exacto en centésimas y
  la UI muestra enteros redondeando hacia abajo.
- **D-12 Números que el plan no fija** *(bloquea fase 3)*: rango, velocidad de
  bala, longitud del camino, velocidades, ritmo de salida, curación del
  regenerador, enjambre y la composición de la tabla 10.2. Van a JSON marcados
  como «propuesta» y el simulador dirá si funcionan.
- **D-13 Hardware**: aditivo y global. ¿Correcto?
- **D-14 Segunda torreta**: ¿empieza con `disparar();` o vacía?
- **D-15 JDK en la sesión cloud** *(bloquea fase 1)*. Para que Gradle
  descargue Temurin 25 hay que permitir `api.foojay.io`, `api.adoptium.net` y
  `github.com` (más `objects.githubusercontent.com` y
  `release-assets.githubusercontent.com`, donde redirige) en el acceso de red
  del entorno. Alternativa: compilar con el JDK 21 que ya hay y dejar 25 solo
  en CI y en tu IntelliJ; no lo recomiendo, porque los tests de aquí no
  probarían la misma versión.
- **D-16 JUnit**: pediste JUnit 5; la versión actual es JUnit 6 (misma API
  Jupiter, requiere Java 17+). Propongo 6; si prefieres 5, 5.14.4.
- **D-17 Métrica de la regla del doble**: ver sección 11. ¿Te vale?
- **D-18 Nombre interno**: id `java-wave-defense` / `JavaWaveDefense` para la
  carpeta de guardado y el paquete. El nombre visible va en `names.json` y se
  cambia cuando decidas el tema.
- **D-19 Sin hilo aparte para los scripts**: ver el aviso de 4.5 (sin hilo
  aparte). Necesito tu visto bueno porque cambia lo que dice el plan.
- **D-20 Cómo «no soltar al objetivo» con variables** *(bloquea fase 2, la más
  importante)*. El script se ejecuta entero en cada ciclo y, como en Java, sus
  variables locales se pierden al terminar. Con Variables solo se puede
  obtener `enemigoMasCercano()`, que es lo mismo que el objetivo por defecto.
  Así que, tal como está el plan, comprar Variables no cambia nada contra el
  Regenerador. Opciones:
  - (a) **Recomendada**: `apuntar(e)` fija el objetivo en la torreta y se
    mantiene entre ciclos hasta que muere o sale del rango; se añade la lectura
    `objetivo()` (devuelve `null` si no hay), disponible con Variables. Script
    típico: `Enemigo o = objetivo(); if (o == null) { o = enemigoMasCercano(); apuntar(o); } disparar();`.
    Enseña `null` y provoca NullPointerException de forma natural. Es una
    llamada nueva respecto al plan: necesito tu sí.
  - (b) Que las variables sobrevivan entre ciclos. No es Java y enseñaría algo
    falso. No la recomiendo.
  - (c) Dejarlo como está y que Variables valga por otras razones. Entonces el
    simulador probablemente diga que no cumple la regla del doble.
