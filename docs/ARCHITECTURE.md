# Arquitectura

Documento vivo. Cada decisión lleva su motivo en una línea. La fuente de verdad
del diseño es [`plan-diseno.pdf`](plan-diseno.pdf); si algo de aquí lo
contradice, manda el plan y hay que avisar.

Estado: **fase 3 hecha** (simulación, `sim-cli` y `docs/BALANCE.md`) y **revisión de balance hecha** (decisiones A y B de Yeray: D-34 … D-37). Las decisiones están en la sección 12. Las que
llevan **PROVISIONAL** son propuestas mías aceptadas para poder avanzar; se
listan en `docs/PROGRESS.md` («Decisiones por revisar»). Los números que no
vienen del plan van marcados en los JSON y en `docs/BALANCE.md`.

---

## 1. Stack y versiones

Comprobado el 2026-10-02 contra Maven Central y el portal de plugins de Gradle.

| Pieza | Versión | Motivo |
|---|---|---|
| JDK | 21 LTS (D-15) | El que tiene el contenedor; la red no deja bajar el 25. Records, sealed y `switch` con patrones ya están en 21. La versión está en un único sitio, el fichero `.java-version`, que leen Gradle (toolchain) y GitHub Actions (`setup-java`). Subir a 25 = cambiar esa línea. |
| libGDX | 1.14.2 (última publicada) | Pedido. LWJGL 3.3.3, el que trae libGDX, sin sobrescribir. |
| Gradle | 9.8.0 (wrapper) | Última estable; funciona con Java 21 y con 25. |
| DSL de Gradle | Kotlin DSL + catálogo `gradle/libs.versions.toml` | Autocompletado en IntelliJ y versiones en un único sitio. ⚠️ gdx-liftoff genera Groovy: mantengo su estructura de módulos y carpetas, no su DSL. |
| JSON (módulos puros) | Jackson databind 2.22.x | `core-sim` no puede usar `Json` de libGDX; Jackson soporta records y da línea/columna en errores de datos. |
| Tests | JUnit Jupiter 6.1.x (D-16) | Versión actual; misma API Jupiter que la 5; pide Java 17+. |
| Formato | Spotless 8.10.3, aplicado una vez desde la raíz: google-java-format para Java y ktfmt (estilo Google) para `*.gradle.kts` | Formato automático. ⚠️ google-java-format va en la versión que fija Spotless: la 1.37.0 rompe su código de enlace (`NoSuchMethodError` en `Style.valueOf`). Necesita `--add-exports` de `jdk.compiler` en `org.gradle.jvmargs`. |
| Empaquetado | Construo 2.2.x (plugin Gradle) | Genera Windows/Linux/macOS con JRE recortado **desde Linux**; jpackage obliga a ejecutar en cada SO. |
| Fuente | JetBrains Mono (OFL 1.1) vía gdx-freetype | Monoespaciada, libre, nítida a cualquier escala. Se incluye `OFL.txt`. |

Avisos de compatibilidad:

- Al subir a Java 24+: avisa del acceso nativo de LWJGL (JEP 472). Habrá que
  añadir `--enable-native-access=ALL-UNNAMED` al lanzador. Con 21 no hace
  falta.
- macOS necesita `-XstartOnFirstThread`; se usa el `StartupHelper` que genera
  gdx-liftoff.
- **Red de la sesión cloud**: Maven Central y el portal de plugins
  responden; las descargas de JDK están bloqueadas. Por eso se usa el JDK 21
  instalado y no se configura descarga automática de toolchains.

## 2. Árbol de carpetas

```
java-wave-defense/
├── .editorconfig
├── .gitattributes                 # LF en el repo, CRLF solo en gradlew.bat
├── .gitignore
├── .java-version                 # versión de Java: única fuente para Gradle y CI
├── .github/workflows/ci.yml       # build + tests + spotlessCheck en cada PR y push a main
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
│   │   └── map.json               # camino y huecos de torreta
│   ├── i18n/
│   │   └── messages.properties    # todos los textos y nombres visibles (también los tematizables); español por defecto
│   └── fonts/
│       ├── JetBrainsMono-Regular.ttf
│       └── OFL.txt
├── script/                        # intérprete. Java puro, sin dependencias
│   └── src/{main,test}/java/io/github/yeray06/wavedefense/script/
│       ├── lexer/                 # Token, TokenType, Lexer
│       ├── ast/                   # sealed Node/Stmt/Expr como records
│       ├── parser/                # Parser descendente recursivo
│       ├── ScriptCompiler.java    # fachada: texto → CompileResult
│       ├── check/                 # Checker: desbloqueos, tipos, ámbitos, memoria; emite el árbol
│       ├── runtime/               # Nodes (fábricas), Frame, CompiledScript (ejecuta un ciclo)
│       ├── api/                   # TurretApi, ScriptApi (vocabulario), Feature, Unlocks, límites
│       └── diag/                  # ErrorCode, CompileError, FaultType, CycleOutcome
├── core-sim/                      # simulación determinista. Java puro (+ Jackson)
│   └── src/{main,test}/java/io/github/yeray06/wavedefense/sim/
│       ├── data/                  # GameData: records de cada JSON, validados al cargar
│       ├── economy/               # Formulas (las del plan), Money (centésimas)
│       ├── world/                 # Battle (una oleada), Turret, Enemy, Bullet, pools, PathTrack, WaveReport
│       ├── progress/              # Progress, Shop, Progression (pago, avance, derrota, ×3)
│       ├── save/                  # fase 6: SaveData versionado, migraciones, códec JSON
│       └── rng/                   # SplitMix64 con semilla (reservado: el MVP no tiene azar)
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
│       │   ├── ReferencePlayer.java   # scripts de referencia + recarga a mano
│       │   ├── ProgressionRun.java    # partida completa con política de compra; muros
│       │   ├── DoubleRule.java        # regla del doble
│       │   └── BalanceReport.java     # escribe docs/BALANCE.md
│       └── resources/reference/       # player.json + scripts/L0…L4-ammo(-b, -c).txt
└── docs/
    ├── plan-diseno.pdf
    ├── ARCHITECTURE.md
    ├── PROGRESS.md
    ├── BALANCE.md                 # generado por sim-cli; un test exige que esté al día
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
  estáticos (`int`, `boolean`, `Enemigo`; `Municion` solo como constante),
  la asignación definitiva como javac («puede que la variable aún no tenga
  valor»), aplica los desbloqueos y cuenta la memoria.
- **Árbol ejecutable**: el mismo checker lo construye mientras comprueba, con
  las fábricas de `runtime/Nodes` (`IntEval`, `BoolEval`, `RefEval`, `Exec`),
  y las variables en ranuras de arrays primitivos. Cambio respecto a la
  propuesta: no hay una pasada «Compiler» aparte, porque habría resuelto cada
  nombre dos veces. El árbol solo se usa si no hubo errores. Motivo de los
  nodos tipados: cero reservas de memoria por ciclo (lo comprueba
  `AllocationTest`: 0 bytes en 100.000 ciclos) y ninguna comprobación de tipo
  en ejecución.
- **Fachada**: `ScriptCompiler.compile(texto, desbloqueos, memoria)` devuelve
  `CompileResult` (errores ordenados, script ejecutable si no hay errores,
  memoria usada por línea para el editor). Nunca lanza: un fallo del propio
  compilador se convierte en `INTERNAL_ERROR`.
- **Errores**: cada uno es un `ErrorCode` con línea, columna y argumentos. El
  texto en español está en `i18n/messages.properties` (`script.error.*`); el
  módulo no contiene ningún texto para el jugador. Máximo 50 errores por
  compilación.

### 4.3 Desbloqueos (`Feature`)

`enum Feature { IF_ELSE, VARIABLES, LOGIC_OPS, WHILE, FOR, METHODS, TRY_CATCH, CLASSES }`.
El checker recibe el conjunto comprado; el MVP solo vende los cuatro primeros.

| Pieza | Qué habilita (D-07) |
|---|---|
| Gratis | Sentencias `disparar();` y `recargar();`, comentarios. |
| if/else y comparaciones | `if`, `else`, `else if`, `== != < > <= >=`, paréntesis, literales `int` y `true`/`false`; lecturas de la torreta `municion()`, `calor()`, `hayEnemigos()`. |
| Variables | Tipos `int`, `boolean` y `Enemigo`; declaración (también `int a = 1, b;`), asignación, `null`; aritmética `+ - * / %` y `-` unario, `++`, `+=` (y por simetría `--`, `-=`, `*=`, `/=`, `%=`, PROVISIONAL D-21); llamadas `enemigoMasCercano()`, `objetivo()`, `apuntar(e)`, `e.vida()`, `e.velocidad()`, `e.blindado()`, `e.distanciaAlNucleo()`. |
| `&&`, `||`, `!` | Solo esos tres operadores. |
| while | Solo el bucle `while`. |
| Oleada 6 (no se compra) | `usarMunicion(NORMAL)` / `usarMunicion(PERFORANTE)`. |

Usar algo no desbloqueado da error de compilación con el texto del plan:
«Aún no has desbloqueado while». Para llamadas: «Aún no has desbloqueado
`municion()`: llega con if/else».

### 4.4 Qué cuenta como línea de memoria (D-06)

**Una línea = una sentencia**, no una línea física. Frase para el jugador:
«Cada instrucción ocupa una línea de memoria; las llaves, `else`, los
comentarios y las líneas en blanco no ocupan».

- Cuentan 1: cada declaración, asignación, llamada, cabecera de `if`
  (un `else if` es otro `if`) y cabecera de `while`.
- Cuentan 0: `else`, llaves, líneas en blanco y comentarios.

Motivo: si contaran líneas físicas, el jugador metería todo en una línea y el
límite dejaría de existir. Con sentencias, el formato da igual. El ejemplo del
plan con `if/else` ocupa 3 líneas de memoria; el de `for` de la oleada 12,
7. El editor muestra el contador en vivo (`7/12`) y marca qué líneas cuentan.

### 4.5 Presupuesto y errores de ejecución

- El script se ejecuta entero, de forma síncrona, en el tick de simulación en
  que toca el ciclo. La simulación no avanza mientras corre, así que el
  resultado es determinista.
- **BucleInfinitoException**: cada vez que se entra en un `while` empieza su
  cuenta; la vuelta 1.001 salta en la línea del `while` (el plan dice «pasa de
  1.000 vueltas»). Cambio respecto a la propuesta: la cuenta es **por
  entrada**, no por ciclo, para que un `while` dentro de otro no salte por
  sumar vueltas de entradas distintas. Además hay un presupuesto de 100.000
  pasos por ciclo (sentencias y vueltas) que también lanza BucleInfinito, en
  la línea del bucle más interno: cubre `while` anidados (999 × 999).
- **Profundidad**: el parser corta a 64 niveles de anidamiento (sentencias y
  expresiones; un paréntesis gasta 2) para que ningún script provoque
  `StackOverflowError`. Probado con 100.000 llaves, paréntesis, `!` y `=`.
- Los límites (1.000, 100.000, 64) están en `data/script-api.json`.
- **Ninguna excepción de Java sale del intérprete**: las excepciones del juego
  son un `ScriptFault` (tipo + línea) que el ejecutor devuelve como resultado,
  no como `throw` hacia la simulación. Como red de seguridad, el `Executor`
  envuelve la ejecución en un `catch (RuntimeException | StackOverflowError)`
  que lo convierte en un fallo interno con línea, lo registra en el log y
  cuelga la torreta como cualquier otra excepción.
- Excepciones del MVP: `SinMunicionException`, `SobrecalentamientoException`,
  `NullPointerException`, `BucleInfinitoException` y `ArithmeticException`
  (división o resto entre cero, como en Java real; D-08). Todas cuelgan la
  torreta 3 s y muestran su línea. Los `int` desbordan como en Java.
- Implementación: una única señal precreada y sin traza de pila desenrolla el
  script (excepción del juego o `recargar()`), así que lanzarla no reserva
  memoria. `CompiledScript.run()` devuelve `COMPLETED`, `RELOADING` o
  `FAULT` (tipo + línea) y nunca lanza. Si la simulación lanza algo, se
  devuelve `INTERNAL_ERROR` con la línea y la causa para el log.

**Sin hilo aparte** (D-19, aprobado por Yeray aunque el plan proponía un
hilo): con el presupuesto de pasos ningún script puede bloquear, y un hilo
rompería el determinismo y complicaría pausar, x3 y los tests.

### 4.6 La API cerrada

```java
public interface TurretApi {           // lo único que ve un script
  FireResult fire();                   // FIRED, NO_AMMO u OVERHEATED
  void reload();  int ammo();  int heat();  boolean hasEnemies();
  long nearestEnemy();  long target();  void aim(long enemy);
  void useAmmo(AmmoType type);
  boolean exists(long enemy);          // el intérprete lo consulta antes de cada uso
  int health(long enemy);  int speed(long enemy);
  boolean armored(long enemy);  int distanceToCore(long enemy);
}
```

- Los enemigos viajan como asas `long` (`EnemyHandle.NONE` = `null`) en vez
  de objetos `EnemyRef`: así leerlos no reserva memoria. La simulación decide
  la codificación (índice del pool + generación).
- `apuntar(null)` también lanza NullPointerException (es «usar un enemigo que
  no existe»). Comparar enemigos con `==` nunca falla, como en Java.

- **Objetivo (D-20)**: `apuntar(e)` fija el objetivo en la torreta y se
  mantiene entre ciclos. `objetivo()` devuelve ese enemigo, o `null` si no hay
  objetivo fijado, ha muerto o ha salido de rango (en esos dos casos además se
  suelta). Sin objetivo fijado, `disparar()` tira al enemigo más cercano.
- **Lecturas enteras (D-22, cerrada en la fase 1)**: como Variables no trae
  `double`, `e.vida()`, `e.velocidad()` y `e.distanciaAlNucleo()` devuelven
  `int`. `vida()` redondea hacia arriba (un enemigo vivo nunca da 0);
  `distanciaAlNucleo()`, hacia abajo.
- **Unidades finas para la velocidad** (pedido de Yeray): la unidad del mundo
  se elige para que la velocidad base sea **100 u/s** (antes 60). Las
  velocidades se escriben en `enemies.json` como **enteros en u/s** (Básico
  100, Regenerador 100, Blindado 80, Enjambre 120, Jefe 50), no como
  multiplicadores, así que `velocidad()` devuelve el valor exacto sin
  redondear: dos tipos con velocidad distinta nunca dan el mismo `int`. El
  validador de datos rechaza velocidades no enteras o ≤ 0, y cuando lleguen
  los modificadores (Rápido…) exigirá que velocidad × modificador siga siendo
  un entero. Con esta escala la distancia también gana resolución (camino de
  2.500 u).

- Si el enemigo murió o el hueco del pool se reutilizó, `exists()` da
  `false` y salta NullPointerException, como en Java con una referencia
  muerta.
- Los nombres que ve el jugador (`disparar`, `Enemigo`, `PERFORANTE`…) y qué
  desbloquea cada llamada salen de `data/script-api.json`, no del código, para
  poder cambiar el tema. `ScriptApiLoader` (en `core-sim`) lo valida: ids
  conocidos y sin repetir, nombres que sean identificadores Java válidos y no
  palabras reservadas, un solo requisito por llamada.

### 4.7 Java de verdad: javac como árbitro

`JavacAgreementTest` compila con el `javac` real (solo en los tests) una
colección de scripts. Todo lo que el juego acepta lo acepta javac, y el juego
rechaza lo que javac rechaza. Las desviaciones están listadas en el test:

- **Más estricto que Java** (con mensaje claro): asignaciones y `++`/`--`
  solo como instrucción propia; `break`, `continue`, `switch`, `?:`, `&`,
  `|`, `<<`, conversiones de tipo, `double`, literales hexadecimales,
  octales y con sufijo; variables de tipo `Municion`; `System` y cualquier
  clase fuera de la API. `return` se trata como parte de «métodos» y los
  arrays como parte de «for y listas».
- **Más permisivo que Java**: no se comprueba el código inalcanzable
  (`while (true) {}` seguido de otra instrucción); en el juego ese bucle lanza
  BucleInfinitoException, que enseña lo mismo.

### 4.8 Convención de datos: `notFromPlan`

Cada fichero de `assets/data/` lleva una lista `notFromPlan` con la ruta de
cada número que no sale del plan (por ejemplo `limits.maxStepsPerCycle`).
Así se ve en el propio dato qué es provisional.
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
- **Pools**: enemigos y balas en arrays de capacidad fija preasignados al
  empezar la oleada (tantos enemigos como salen en ella; 64 balas por
  torreta). Nada de `new` en el tick.
- **Dinero en punto fijo** (`long` en centésimas): la fórmula da 2,3; 2,645…
  y con `double` acumularíamos errores al repetir oleadas. La UI muestra
  enteros redondeando hacia abajo (PROVISIONAL D-11).
- **Datos**: cada JSON se valida al cargar (rangos, referencias cruzadas, que
  la composición de cada oleada sume `min(6 + 2n, 60)`). Un dato inválido da
  un error con fichero y campo, nunca un valor por defecto silencioso.

## 6. Juego (módulo `game`)

- `FitViewport` de 1280×720 para el mundo; `ScreenViewport` con escala de UI
  para scene2d. Ventana y pantalla completa, ajustes en `settings.json`.
- Render con `ShapeRenderer` (formas geométricas) y la fuente con freetype.
  El render solo lee el estado de la simulación.
- **Dibujo del mundo detrás de una sola interfaz** (`WorldRenderer`, D-40):
  todo lo que pinta el mundo (camino, núcleo, torretas, enemigos, balas)
  pasa por ella y solo lee el estado de la simulación. En el MVP la
  implementación es 2D; una futura 3D la sustituye sin tocar pantallas, UI
  ni `core-sim`. También tiene la cámara, dice qué hay bajo el puntero y
  proyecta un punto del mundo a pantalla, para que las pantallas y la UI
  pegada al mundo (error encima de la torreta, barras, botón de recarga) no
  dependan de si el mundo es 2D o 3D. La UI (editor, tienda, parte, informe)
  sigue en scene2d y no pasa por ella.
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

- Spotless desde la raíz (ver §1), `.editorconfig` coherente con él.
- Compilación con `-Xlint:all -Werror`: un aviso del compilador rompe el build.
- GitHub Actions (`.github/workflows/ci.yml`): `./gradlew build` (compila,
  tests y `spotlessCheck`) en `ubuntu-latest` **y** `windows-latest`, en cada
  pull request y en cada push a `main`. Las ramas de fase no disparan en push:
  ya las cubre su PR, y así nada se ejecuta dos veces por commit (repo privado,
  minutos de Actions limitados). Un push nuevo cancela la ejecución anterior
  del mismo PR. Windows primero porque es el objetivo; las rutas y el renombrado
  atómico se comportan distinto allí.
- `gradle/actions/setup-gradle` valida la suma del `gradle-wrapper.jar`. Ese
  JAR es el único binario del repo: es parte estándar del wrapper, no una
  salida de build ni una dependencia metida a mano.
- **Test de humo de `game`**: ejecuta `create → render ×10 → dispose` del
  juego real sobre el backend headless de libGDX. Ese backend ya no trae GL
  simulado, así que el test instala un `GL20` vacío: comprueba el ciclo de
  vida, no lo que se dibuja.
- `sim-cli` y `script` existen como módulos vacíos hasta sus fases (2 y 3).

## 10. Reglas del juego tal y como se implementarán

Columna «Origen»: **Plan** = literal del plan; **Yeray** = decisión de Yeray
en la fase 0; **PROVISIONAL** = propuesta mía aceptada para avanzar, por
revisar; **No viene del plan** = número provisional de D-12, marcado así en
los JSON y en `docs/BALANCE.md`.

### 10.1 Torreta

| Regla | Valor | Origen |
|---|---|---|
| Ciclos por segundo | 2 | Plan |
| Cargador | 12 balas (+4 por nivel) | Plan |
| Calor | +20 por disparo NORMAL, +40 PERFORANTE, −30 por ciclo, entre 0 y 100 | Plan |
| Memoria | 12 líneas (sentencias, ver 4.4); hardware de memoria fuera del MVP | Plan + Yeray (D-06) |
| Daño de bala | 5 (+25 % por nivel) | Plan |
| Orden en el ciclo | 1) disipar calor (mínimo 0); 2) ejecutar el script | Yeray (D-04) |
| Sobrecalentamiento | Salta solo si disparas con el calor ya en 100; si un disparo lo supera, queda en 100 | Yeray (D-04) |
| `recargar()` | Corta el script en ese punto; la torreta queda ocupada 3 ciclos (el calor sigue disipándose); al volver, cargador lleno. Con el cargador lleno cuesta lo mismo | Plan + Yeray (D-03) |
| Recarga a mano | Mismo efecto que `recargar()`; **también con la torreta colgada** (la recarga avanza durante el cuelgue); no si ya está recargando | PROVISIONAL (D-03b, cambiada en la fase 3) |
| Excepción sin capturar | Corta el script, torreta colgada 3 s, error con línea encima | Plan |
| `disparar()` sin enemigos en rango | Bala «al aire»: gasta bala y calor | PROVISIONAL (D-05) |
| Objetivo por defecto | Enemigo vivo más cercano **a la torreta** dentro del rango. Configurable en `turret.json` (`defaultTarget`: más cercano a la torreta, más cercano al núcleo o uno al azar en rango, con la semilla de la oleada) para medir; `enemigoMasCercano()` siempre es «a la torreta» | Plan + Yeray (D-05, D-36) |
| Objetivo fijado | `apuntar(e)` lo mantiene entre ciclos; `objetivo()` da `null` si no hay, murió o salió de rango | Yeray (D-20) |
| `usarMunicion(tipo)` | Disponible desde la oleada 6; el tipo elegido se mantiene entre ciclos | Plan |
| Perforante | Ignora la reducción del blindaje; un solo objetivo | PROVISIONAL (D-09) |
| Script inicial de cada torreta | `disparar();` | PROVISIONAL (D-14) |
| Rango | 330 u | No viene del plan (D-12) |
| Balas | Proyectil guiado a 1.000 u/s; si el objetivo muere antes, bala perdida y contada en el informe | No viene del plan (D-12) |
| Presupuesto de pasos | 100.000 nodos por ciclo, además del límite de 1.000 vueltas por `while` | No viene del plan (4.5) |

### 10.2 Enemigos y oleadas

| Regla | Valor | Origen |
|---|---|---|
| Vida | `10 · 1,18^(n−1)` | Plan |
| Dinero por enemigo | `2 · 1,15^(n−1)` | Plan |
| Nº de enemigos | `min(6 + 2n, 60)` | Plan |
| Núcleo | **10** vidas por oleada; cada enemigo que llega quita 1 | Yeray (D-35; el plan decía 20) |
| Regenerador | Vida ×2; se cura si pasa 1 s sin daño, a 50 % de vida máx./s; 100 u/s | 1 s Plan; resto No viene del plan (D-37) |
| Blindado | Vida ×2; −80 % de daño con NORMAL; 80 u/s | 80 % Plan; resto No viene del plan (D-37) |
| Enjambre | Grupos de 10, juntos; vida ×0,3, dinero ×0,3, 200 u/s; cada miembro cuenta como 1 enemigo | 10 Plan; resto No viene del plan |
| Jefe 1 | Vida = 20 × vida de la oleada 10; alterna blindaje cada 5 s; si llega al núcleo se pierde la oleada; 50 u/s | Plan; velocidad No viene del plan |
| Recompensa del jefe | 1.er derribo = 3 × dinero de la oleada 10 (549); los siguientes, como 20 enemigos normales de esa oleada | PROVISIONAL (D-10) |
| Básico | 100 u/s | No viene del plan (D-12, D-22) |
| Duración | ~45 s: camino de 2.500 u, velocidad base 100 u/s (25 s), salidas repartidas en 20 s | ~45 s Plan; números No viene del plan |

Composición (No viene del plan, D-12). B = Básico, R = Regenerador,
Bl = Blindado, E = Enjambre:

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
| Efecto por nivel | Aditivo sobre la base: daño 5 → 6,25 → 7,5 | Plan («cada nivel… da lo mismo») + PROVISIONAL (D-13) |
| Ámbito del hardware | Global, para todas las torretas | PROVISIONAL (D-13) |
| Segunda torreta | «Torreta extra» a 75; tope 2 en el MVP | Plan |
| Topes de hardware | Un nivel de hardware no se compra hasta tener cierta sintaxis (`shop.json`, `caps`): daño y cargador, nivel 1 con if, 2 con Variables, 4 con while; disipador y torreta extra, con `&&`. La tienda dice «Necesitas variables para seguir mejorando el daño» | Yeray (red de seguridad, D-34); topes concretos PROVISIONAL |
| Oleada perfecta | +25 % del pago de la oleada si no se cuela nadie | Plan |
| Avance automático | Interruptor; apagado repite la oleada. Encendido en partida nueva | Plan + PROVISIONAL (D-02) |
| Velocidad ×3 | Solo en oleadas superadas alguna vez sin fugas | Plan |
| Derrota | Cobras lo matado, conservas todo, vuelves a la oleada anterior (mín. 1) con avance automático apagado | Plan |
| Dinero | Exacto en centésimas; la UI muestra enteros hacia abajo | PROVISIONAL (D-11) |

### 10.4 Flujo entre oleadas (D-01, Yeray)

- Una oleada que **nunca has superado** se para en el parte de oleada hasta
  que pulsas «Desplegar».
- Con el avance automático **apagado**, las repeticiones se encadenan sin
  pausa; el informe aparece sin bloquear.
- Con el avance automático **encendido** y la siguiente oleada ya superada
  alguna vez (p. ej. tras volver por una derrota), también se encadena sin
  pausa. PROVISIONAL (D-01b).
- Editor y tienda se pueden usar mientras corre una oleada; los cambios de
  código entran en la siguiente pasada (la «edición en caliente» del plan).

## 11. Simulador (`sim-cli`)

`./gradlew :sim-cli:run --args="--write docs/BALANCE.md"` juega el MVP entero
en unos segundos y reescribe el informe. `SimCliTest` falla si el informe
del repo no coincide con lo que generan los datos actuales: cambiar un JSON
obliga a regenerarlo y a ver el efecto en el mismo PR.

- **Jugador de referencia**: un script por nivel de sintaxis
  (`resources/reference/scripts/`, todos caben en 12 líneas; los que llevan
  `-ammo` usan `usarMunicion`). Recarga a mano 1 s después de vaciarse una
  torreta (`player.json`, no viene del plan).
- **Política de compra**: la siguiente sintaxis en cuanto la puede pagar;
  atascado tras una derrota, lo que pueda tras cada oleada farmeada
  (sintaxis, si no torreta extra, si no el hardware más barato) y vuelve a
  intentarlo; con toda la sintaxis, hardware tras cada oleada. Usa ×3 donde
  se puede. El tiempo de escribir código no cuenta.
- **Pasadas por muro**: oleadas anteriores repetidas entre la primera
  derrota en una oleada nueva y su primera victoria. Regla del plan: ≤ 5.
- **Regla del doble** (PROVISIONAL D-17, métrica cambiada en la fase 3): para
  cada pieza, desde el estado justo antes de comprarla y en la oleada en que
  «llega» (`shop.json`), `k` = mayor multiplicador de vida enemiga con el que
  la oleada sale **sin fugas**. Se compara la pieza con su script frente al
  mejor hardware del mismo precio **dentro de los topes** (D-34). Variables es
  excepción aceptada (D-39); el informe
  da también la proporción sin topes como referencia. En cada lado se prueban
  todas las variantes del script que caben en memoria (`L3-ammo`,
  `L3-ammo-b`…) y se toma la mejor. Cumple si la proporción es ≥ 2. La
  propuesta original («la oleada se supera») no servía: con 20 vidas, las
  oleadas 1–6 no se pueden perder y `k` se saturaba en el máximo.
- **Caso D-20**: la fila VARIABLES es la oleada 4, la primera con
  regeneradores.
- **Barrido del Regenerador**: `--sweep-regenerator FILE` juega una partida
  entera y mide la regla del doble de Variables para cada combinación de
  espera, curación, vida y separación del Regenerador (rejilla en
  `RegeneratorSweep`), sin tocar los datos del disco. Tarda unos 7 min.
- **Conclusiones**: el informe separa «Contradicciones con el plan» (reglas
  del plan que no se cumplen) y «Avisos». Nada se ajusta por mi cuenta.

### 11.1 Simulación (`core-sim`) tal como ha quedado

- `Battle` juega una oleada: aparición → ciclos de torreta → balas →
  enemigos → fin. 60 ticks/s; ciclo de torreta cada 30 ticks.
- Aparición: los «huecos» de salida se reparten en la ventana de 20 s; tras
  cada hueco va un trozo de ventana proporcional al `spawnGapFactor` de su
  tipo (`enemies.json`; hoy 1 en todos = reparto por igual, D-38); un grupo de enjambre ocupa un hueco y sus miembros salen seguidos a la
  distancia de grupo. El jefe es el último hueco.
- Balas guiadas; si el objetivo muere antes de llegar, cuentan como «balas en
  enemigos ya muertos». Un enemigo muere con vida ≤ 1e-9 (si no, 5 × 0,2 =
  0,99999… y el blindado necesitaría una bala más).
- `hayEnemigos()` y `enemigoMasCercano()` miran solo enemigos en rango.
- Cada oleada lleva semilla (`BattleSetup.seed`; el simulador usa el número
  de oleada). Solo la usa el objetivo «uno al azar» (`SplitMix64`).
- Asas de enemigo: generación en los 32 bits altos, hueco + 1 en los bajos.
- Fugas: cada una guarda el tipo, la vida que le quedaba y la distancia del
  camino donde estuvo en rango por última vez («por qué punto se coló»).
- Cero reservas por tick (`BattleAllocationTest`) y mismo informe con los
  mismos datos (`BattleTest`).
- Funciones matemáticas con `StrictMath`, sin reloj ni `Random`:
  `DeterminismRulesTest` lo vigila en el código fuente. Motivo: `Math.pow` y
  compañía pueden diferir en el último bit entre máquinas.
- La lógica de partida (`Progress`, `Shop`, `Progression`) aplica el plan:
  +25 % por oleada perfecta, ×3 en oleadas superadas sin fugas, derrota =
  cobras lo matado, vuelves una oleada atrás con el avance automático
  apagado y conservas todo.

## 12. Decisiones

**CERRADA** = decidida por Yeray. **PROVISIONAL** = propuesta mía aplicada
para avanzar; se revisa más adelante (lista en `docs/PROGRESS.md`). Ninguna
provisional contradice el plan.

| Id | Tema | Estado | Decisión |
|---|---|---|---|
| D-01 | Flujo entre oleadas | CERRADA | Ver 10.4. |
| D-01b | Avance automático hacia una oleada ya superada | PROVISIONAL | Se encadena sin pausa. |
| D-02 | Avance automático por defecto | PROVISIONAL | Encendido en partida nueva. |
| D-03 | `recargar()` | CERRADA | Corta el script; 3 ciclos ocupada; el calor sigue disipándose; vuelve con el cargador lleno; con el cargador lleno cuesta lo mismo. La recarga a mano funciona igual (PROVISIONAL). |
| D-03b | Recarga a mano con la torreta colgada | PROVISIONAL (cambiada en la fase 3) | Se permite. Antes estaba prohibida, pero el cuelgue dura justo 6 ciclos: un script que lanza con el cargador vacío se despierta y vuelve a lanzar en el mismo tick, y el jugador nunca podía recargar (la oleada 1 se volvía imposible de jugar, contra el plan). |
| D-04 | Calor | CERRADA | Se disipa al inicio del ciclo, antes del script, mínimo 0. Excepción solo si disparas con 100; un disparo que lo supera lo deja en 100. |
| D-05 | Disparo sin objetivo y «más cercano» | PROVISIONAL | Bala al aire (gasta bala y calor). «Más cercano» = a la torreta. |
| D-06 | Línea de memoria | CERRADA | Una sentencia = una línea (4.4). Se explica en una frase y el editor muestra el contador en vivo. |
| D-07 | Qué trae cada pieza | CERRADA | Tabla de 4.3. |
| D-08 | División entre cero | CERRADA | `ArithmeticException` como en Java; cuelga 3 s y muestra la línea. |
| D-09 | Perforante | PROVISIONAL | Solo atraviesa blindaje, no enemigos. |
| D-10 | Recompensa del jefe | PROVISIONAL | 549 la primera vez; luego como 20 enemigos normales de la oleada 10. |
| D-11 | Decimales del dinero | PROVISIONAL | Centésimas por dentro; enteros hacia abajo en la UI. |
| D-12 | Números que el plan no fija | CERRADA | Se aceptan los valores de la sección 10, marcados «No viene del plan» en JSON y en `docs/BALANCE.md`. |
| D-13 | Hardware | PROVISIONAL | Aditivo sobre la base y global. |
| D-14 | Script inicial de las torretas | PROVISIONAL | `disparar();`. |
| D-15 | JDK | CERRADA | JDK 21 LTS; versión en un único sitio para subir a 25. |
| D-16 | JUnit | CERRADA | JUnit 6. |
| D-17 | Métrica de la regla del doble | PROVISIONAL (cambiada en la fase 3) | Factor de vida `k` con el que la oleada sale **sin fugas** (sección 11). |
| D-18 | Nombre interno | PROVISIONAL | `java-wave-defense` / `JavaWaveDefense`; paquete `io.github.yeray06.wavedefense`. El nombre visible va en `i18n/messages.properties`. |
| D-19 | Hilo aparte | CERRADA | Sin hilo; presupuesto de pasos. |
| D-20 | No soltar al objetivo | CERRADA | `apuntar(e)` persiste entre ciclos; nueva lectura `objetivo()`; sin `apuntar`, el más cercano. Comprobación en fase 3 con el simulador. |
| D-21 | Operadores simétricos | PROVISIONAL | Además de `++` y `+=`: `--`, `-=`, `*=`, `/=`, `%=`. Prefijo y sufijo, pero solo como instrucción propia (D-23). |
| D-22 | Lecturas de enemigo sin `double` | CERRADA | `vida()`, `velocidad()` y `distanciaAlNucleo()` devuelven `int`; velocidades enteras en u/s con base 100, validadas al cargar (4.6). |
| D-23 | Asignaciones dentro de expresiones | PROVISIONAL | `x = 3`, `x++` y compañía solo como instrucción propia; `if ((x = 3) > 2)` o `y = x++` dan error. Java lo permite, pero esconde lógica y confunde al que empieza. |
| D-24 | Palabras de Java que no se venden | PROVISIONAL | `break`, `continue`, `switch`, `do`, `throw`… dan «no está disponible en este juego». `return` cuenta como «métodos» y los arrays como «for y listas». |
| D-25 | Cuenta del `while` | PROVISIONAL | 1.000 vueltas por entrada en el bucle, no por ciclo; más un presupuesto de 100.000 pasos por ciclo (no viene del plan). |
| D-26 | `apuntar(null)` | PROVISIONAL | Lanza NullPointerException, como cualquier uso de un enemigo inexistente. |
| D-27 | Código inalcanzable | PROVISIONAL | No se comprueba (javac sí). El bucle infinito salta en ejecución. |
| D-28 | Un error por pieza y línea | PROVISIONAL | Si una línea usa varias cosas de la misma pieza bloqueada (`if`, `==`, `municion()`), sale un solo «Aún no has desbloqueado if/else». |
| D-29 | Calor y recarga durante el cuelgue | PROVISIONAL | El calor se sigue disipando y una recarga sigue avanzando mientras la torreta está colgada. |
| D-30 | El jefe | PROVISIONAL | Empieza sin blindaje y alterna cada 5 s. No cobra por baja (0): solo el pago de D-10. |
| D-31 | Lecturas de la torreta | PROVISIONAL | `hayEnemigos()` y `enemigoMasCercano()` solo ven enemigos en rango. |
| D-32 | Orden de aparición | PROVISIONAL | Grupos en el orden de `waves.json`; huecos repartidos por igual en 20 s; el enjambre ocupa un hueco. |
| D-33 | Jugador de referencia | PROVISIONAL | Política de compra y 1 s de reacción para recargar a mano (sección 11). Solo afecta al informe, no al juego. |
| D-34 | Topes de hardware según la sintaxis | CERRADA la idea (Yeray); topes PROVISIONAL | Red de seguridad de la regla del doble. Topes en 10.3, configurables en `shop.json`; solo pueden esperar a piezas del MVP (se valida al cargar). |
| D-35 | Vidas del núcleo | CERRADA (Yeray, cambia el plan) | 10 por oleada en vez de 20. |
| D-36 | Objetivo por defecto | CERRADA (Yeray) | El más cercano a la torreta, como dice el plan. Sigue siendo configurable en `turret.json` para medir. «Al azar» llevaba Variables a ×2 frente a no tenerla (2,03), pero contradice el plan. |
| D-38 | Separación al salir por tipo | CERRADA (Yeray) | `spawnGapFactor` por tipo, **1 en todos** (sin efecto). Hacía falta para barrer la separación del Regenerador sin tocar las oleadas 1–3. Barrido en `docs/BARRIDO-REGENERADOR.md`: ninguna combinación de los cuatro números lleva Variables a ×2 (máximo 1,20, con vida ×1); no se aplica porque gana poco ratio a cambio de acortar el camino al Jefe 1 y de bajar la ganancia de Variables. No se hacen más barridos sobre esto. |
| D-40 | Camino a un render 3D | CERRADA (Yeray) | A largo plazo el juego se verá en 3D; el MVP (fase 4) sigue en 2D. Para no cerrar la puerta: la simulación sigue en un plano 2D y no sabe nada del render; todo el dibujo del mundo pasa por una única interfaz (`WorldRenderer`, en `game`) que solo lee el estado de la simulación, para cambiar la implementación 2D por una 3D sin tocar pantallas, UI ni `core-sim`; la UI sigue en scene2d. No se añade ninguna otra abstracción por esto. Además: (1) `WorldRenderer` contiene la cámara y dice qué hay bajo el puntero (de pantalla a mundo); (2) proyecta un punto del mundo a pantalla, para colocar la UI de scene2d pegada al mundo; (3) la simulación guarda los sucesos de un instante (disparo, impacto, muerte, excepción…) en un búfer reutilizable sin reservas; acumula todo lo ocurrido desde la última lectura del render (a ×3 hay varios ticks por frame); el informe de oleada no depende de él (sus datos los acumula la simulación aparte), y el búfer no influye en la simulación ni en su determinismo (solo se escribe; la lectura del render lo vacía y nada de la simulación lo lee); (4) la simulación conserva la posición anterior de cada entidad para que el render interpole entre ticks. |
| D-39 | Variables y la regla del doble | CERRADA (Yeray) | **Excepción documentada** a la regla del doble: Variables rinde 1,07 veces lo mismo en hardware. Motivo: es compra obligatoria para llegar a `&&`, así que no compite con el hardware. `docs/BALANCE.md` la sigue listando entre las contradicciones porque el informe da datos, no decisiones. Se revisa con el playtest (`docs/PROGRESS.md`, «A observar en el playtest»). |
| D-37 | Números de enemigos de la revisión | PROVISIONAL | Regenerador vida ×2 y cura 50 %/s; Blindado vida ×2; Enjambre 200 u/s (menos tiempo en rango). Ninguno es del plan. |

Decisiones de stack también cerradas: Kotlin DSL sí; LWJGL el de libGDX
1.14.2 sin sobrescribir.
