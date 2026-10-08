# Arquitectura

Documento vivo. Cada decisión lleva su motivo en una línea. La fuente de verdad
del diseño es [`plan-diseno.pdf`](plan-diseno.pdf); si algo de aquí lo
contradice, manda el plan y hay que avisar.

Estado: **fase 6b hecha** (manual para principiantes y nombres de las piezas). Antes, fase 6: guardado, ajustes y robustez. Antes, fase 5b: manual del jugador. Antes, fase 5: editor, tienda, parte de oleada, informe y mapa de oleadas. Antes, fase 4: pantalla de combate jugable. Antes: simulación, `sim-cli`, `docs/BALANCE.md` y la revisión de balance (D-34 … D-39). Las decisiones están en la sección 12. Las que
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
| Empaquetado | Construo 2.2.2 (plugin Gradle), Java Temurin 21 recortado con jlink | Genera Windows/Linux/macOS **desde un solo sistema**; jpackage obliga a ejecutar en cada SO. Detalle en §9.1. |
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
├── .github/workflows/package.yml  # zips con Java incluido, solo a mano o con etiqueta v*
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
│   │   ├── balance.json           # fórmulas: vida, nº enemigos, núcleo, estrellas al empezar
│   │   ├── enemies.json           # tipos de enemigo y sus multiplicadores
│   │   ├── waves.json             # composición de las oleadas 1–10 y jefe
│   │   ├── turret.json            # cargador, calor, ciclos, memoria, recarga, cuelgue, rango
│   │   ├── shop.json              # árbol de sintaxis y hardware (precios, requisitos)
│   │   ├── script-api.json        # nombre visible y desbloqueo de cada llamada
│   │   ├── map.json               # camino y huecos de torreta
│   │   └── manual.json            # capítulos y páginas del manual, qué desbloquea cada una y sus ejemplos de código
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
│       ├── world/                 # Battle (una oleada), Turret, Enemy, Bullet, pools, PathTrack, WaveReport, BattleEvents
│       ├── progress/              # Progress, Shop, Progression (pago, avance, derrota, ×3), WaveLoop (D-01)
│       ├── save/                  # SaveGame (progreso, código, borradores), SaveCodec (versión y migraciones)
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
│       ├── screen/                # BattleScreen; GameControls (lo que el panel de ajustes pide al juego)
│       ├── render/                # WorldRenderer (D-40) y ShapeWorldRenderer (2D): mundo, efectos, cámara
│       ├── ui/                    # scene2d.ui, skin generado por código
│       ├── editor/                # EditorDocument (texto, cursor, deshacer), Highlighter (con el lexer del juego), CodeEditor (widget), CodeView (código de solo lectura)
│       ├── panel/                 # BriefingPanel, EditorPanel, ShopPanel, MapPanel, ReportPanel, ReportLines, ManualPanel, SettingsPanel
│       ├── save/                  # GameStore (save.json y settings.json en la carpeta de usuario), AutoSaver
│       ├── audio/                 # sonidos sintetizados por código propio
│       ├── input/
│       ├── i18n/                  # Texts: envoltorio de I18NBundle; ScriptTexts; ManualFacts (números del manual)
│       ├── settings/              # Settings: pantalla completa, tamaños (settings.json)
│       ├── playtest/              # ReloadReactionLog: tiempo de recarga a mano (decisión C)
│       └── log/                   # GameLog: log rotativo y handler global
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
    ├── DIARIO.md                  # diario: qué se hizo, problemas y soluciones, con capturas
    ├── capturas/                  # capturas de cada fase (faseN-NN-que-se-ve.png)
    ├── BALANCE.md                 # generado por sim-cli; un test exige que esté al día
    └── STEAM.md                   # publicar en Steam (fase 7)
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
  Si encuentra una construcción conocida pero aún no implementada (`try`,
  `class`) emite `FEATURE_LOCKED` y no la interpreta a medias. Tiene dos
  entradas: `parse` para el script de una torreta (el cuerpo de un método) y
  `parseLibrary` para la biblioteca (una lista de métodos, como el cuerpo de
  una clase; fase 9).
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
- **Fachada**: `ScriptCompiler.compile(texto, biblioteca, desbloqueos,
  memoria)` devuelve `CompileResult` (errores ordenados, script ejecutable si
  no hay errores, memoria usada por línea para el editor);
  `compileLibrary(texto, desbloqueos)` devuelve `LibraryResult` con la
  `Library` (§4.9). Nunca lanzan: un fallo del propio compilador se convierte
  en `INTERNAL_ERROR`.
- **Errores**: cada uno es un `ErrorCode` con línea, columna y argumentos. El
  texto en español está en `i18n/messages.properties` (`script.error.*`); el
  módulo no contiene ningún texto para el jugador. Máximo 50 errores por
  compilación.

### 4.3 Desbloqueos (`Feature`)

`enum Feature { IF_ELSE, VARIABLES, LOGIC_OPS, WHILE, FOR, METHODS, TRY_CATCH, CLASSES }`.
El checker recibe el conjunto comprado; desde la fase 9 se venden los seis
primeros. El parser lee siempre lo que el juego sabe ejecutar (`if`, `while`,
`for`, métodos…) y es el checker quien dice si está comprado; lo que aún no se
ejecuta (`try`, clases) se marca como bloqueado y se salta entero.

| Pieza | Qué habilita (D-07) |
|---|---|
| Gratis | Sentencias `disparar();` y `recargar();`, comentarios. |
| if/else y comparaciones | `if`, `else`, `else if`, `== != < > <= >=`, paréntesis, literales `int` y `true`/`false`; lecturas de la torreta `municion()`, `calor()`, `hayEnemigos()`. |
| Variables | Tipos `int`, `boolean` y `Enemigo`; declaración (también `int a = 1, b;`), asignación, `null`; aritmética `+ - * / %` y `-` unario, `++`, `+=` (y por simetría `--`, `-=`, `*=`, `/=`, `%=`, PROVISIONAL D-21); llamadas `enemigoMasCercano()`, `objetivo()`, `apuntar(e)`, `e.vida()`, `e.velocidad()`, `e.blindado()`, `e.distanciaAlNucleo()`. |
| `&&`, `||`, `!` | Solo esos tres operadores. |
| while | Solo el bucle `while`. |
| for («Recorrer (for)», fase 8) | Las dos formas: contar, `for (int i = 0; i < n; i++)` (varias inicializaciones y actualizaciones separadas por comas, condición opcional), y recorrer, `for (Enemigo e : lista)`. El tipo `List<Enemigo>` con `size()`, `get(i)` e `isEmpty()` (nombres de Java, no del tema). La llamada `enemigos()`. |
| Métodos («Reutilizar código (métodos)», fase 9) | Declarar métodos en la biblioteca, con parámetros (`int`, `boolean`, `Enemigo`, `List<Enemigo>`) y lo que devuelven (esos tipos o `void`); `return` con o sin valor, también en el script de una torreta (acaba el ciclo); llamarlos desde cualquier torreta y desde otros métodos, también a sí mismos. |
| Oleada 6 (no se compra) | `usarMunicion(NORMAL)` / `usarMunicion(PERFORANTE)`. |

Usar algo no desbloqueado da error de compilación con el texto del plan:
«Aún no has desbloqueado while». Para llamadas: «Aún no has desbloqueado
`municion()`: llega con if/else».

### 4.4 Qué cuenta como línea de memoria (D-06)

**Una línea = una sentencia**, no una línea física. Frase para el jugador:
«Cada instrucción ocupa una línea de memoria; las llaves, `else`, los
comentarios y las líneas en blanco no ocupan».

- Cuentan 1: cada declaración, asignación, llamada, cabecera de `if`
  (un `else if` es otro `if`), cabecera de `while`, cabecera de `for` (con lo
  que lleve dentro de los paréntesis) y `return`.
- La biblioteca no cuenta (el plan: «no cuentan para el límite de 12
  líneas»); llamar a uno de sus métodos desde una torreta es 1 línea.
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
- Los límites (1.000, 100.000, 64 de anidamiento y 64 de llamadas) están en
  `data/script-api.json`.
- **Ninguna excepción de Java sale del intérprete**: las excepciones del juego
  son un `ScriptFault` (tipo + línea) que el ejecutor devuelve como resultado,
  no como `throw` hacia la simulación. Como red de seguridad, el `Executor`
  envuelve la ejecución en un `catch (RuntimeException | StackOverflowError)`
  que lo convierte en un fallo interno con línea, lo registra en el log y
  cuelga la torreta como cualquier otra excepción.
- Excepciones: `SinMunicionException`, `SobrecalentamientoException`,
  `NullPointerException`, `BucleInfinitoException`, `ArithmeticException`
  (división o resto entre cero, como en Java real; D-08),
  `IndexOutOfBoundsException` (`get(i)` fuera de `0..size()-1`, fase 8) y
  `StackOverflowError` (más de 64 llamadas a la biblioteca en marcha a la
  vez, casi siempre un método que se llama a sí mismo sin parar; fase 9). Un
  fallo dentro de un método dice la línea de la biblioteca.
  `for` tiene el mismo límite de vueltas que `while`. Todas cuelgan la
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
  boolean armored(long enemy);  boolean mutant(long enemy);   // e.mutante() (fase 9)
  int distanceToCore(long enemy);
  int enemyCount();  long enemyAt(int index);   // enemigos() (fase 8)
}
```

- **Listas sin objetos** (fase 8): una lista es una referencia más (0 =
  `null`). `size()`, `get(i)` e `isEmpty()` llaman a `enemyCount()` y
  `enemyAt(i)` en el momento: como el mundo no se mueve mientras corre un
  script, es la misma lista. `Battle` la construye una vez por tick y torreta
  en un array preparado de antemano (los enemigos a tiro, en el orden en que
  salieron; ordenación por inserción, sin reservar memoria).

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
  clase fuera de la API. Arrays
  (`int[]`, `a[0]`): no existen; las listas del juego son `List<Enemigo>` y
  salen de `enemigos()` (fase 8). Un `List` sin `<Enemigo>`, comparar una lista
  con un enemigo (javac lo deja: interfaz frente a clase no final) y los
  métodos de `List` que no sean `size()`, `get(i)` e `isEmpty()`. En la
  biblioteca (fase 9): dos métodos con el mismo nombre (javac admite
  sobrecargas), un método con el nombre de una orden del juego (javac lo toma
  como sobrescribirla), modificadores (`static`, `public`, `final`…, también
  en variables), variables fuera de un método (serían de todas las torretas a
  la vez) y bucles infinitos cuya condición no es el literal `true` (javac
  pliega `1 == 1` y no pide `return` detrás).
- **Más permisivo que Java**: no se comprueba el código inalcanzable
  (`while (true) {}` seguido de otra instrucción, o algo detrás de un
  `return`); en el juego ese bucle lanza BucleInfinitoException, que enseña lo
  mismo.

### 4.9 Métodos y biblioteca (fase 9)

El plan: «Los métodos se escriben en una biblioteca compartida por todas las
torretas y no cuentan para el límite de 12 líneas».

- **Una biblioteca para todas**: `compileLibrary` comprueba primero todas las
  cabeceras (así un método puede llamar a otro escrito más abajo, o a sí
  mismo) y luego los cuerpos. El resultado, `Library`, es inmutable y sus
  nodos no guardan estado: todas las torretas lo ejecutan a la vez. Los
  scripts de las torretas se compilan contra ella; si tiene errores, llamar a
  uno de sus métodos da «La biblioteca tiene errores: arréglalos para poder
  usar rafaga()».
- **Ranuras en pila, sin reservar memoria**: cada función (el script o un
  método) numera sus variables desde 0. En ejecución, `Frame` tiene un único
  array de ranuras dimensionado para el script más 64 llamadas del método más
  grande; cada llamada usa una región por encima de la de quien llama
  (`intBase`/`refBase`). Los argumentos se evalúan, de izquierda a derecha, en
  ranuras de quien llama, para que una llamada dentro de un argumento no
  pise los ya evaluados; luego se copian a los parámetros. `return` desenrolla
  con otra señal precreada y sin traza. `AllocationTest` lo comprueba con
  recursión, listas y `return`: 0 bytes.
- **Fallos en la biblioteca**: la señal de fallo sabe si saltó dentro de un
  método (`faultInLibrary`), y la torreta, el informe y el HUD dicen
  «biblioteca, línea N».
- **Comprobaciones como javac**: tipos de parámetros y de lo que se devuelve,
  número de argumentos, «tiene que devolver algo en todos los caminos»
  (`MISSING_RETURN`: un `if` sin `else` puede acabar; un `while (true)` o un
  `for (;;)` no), y la asignación definitiva sabe que una rama que acaba en
  `return` no llega al final.

### 4.10 Salida, depurador y sugerencias (fase 9b)

- **`imprimir(x)`** escribe un `int`, un `boolean`, un enemigo, una lista o
  `null` en la ventana de salida; el tipo `PRINTABLE` solo existe para su
  parámetro. No gasta memoria (como los comentarios): sirve para mirar dentro
  y quitarlo después. La torreta lo pasa a `TurretApi.print(kind, value,
  línea, enBiblioteca)` y la batalla lo guarda en `OutputLog`, un anillo de
  512 entradas en columnas primitivas (sin reservar memoria). `saludar()` no
  hace nada más que contar y lanzar un suceso para el render (un anillo
  dorado) y `oleada()` da el número de la oleada (D-68).
- **«¿Quisiste decir…?»**: `Suggestions.closest` (Levenshtein con
  transposiciones, sin mayúsculas; distancia 1 hasta 4 letras, 2 hasta 8 y 3
  más) añade el nombre parecido como último argumento de los errores de
  nombre, tipo, llamada o llamada de enemigo desconocidos; `ScriptTexts` usa
  entonces la variante `.suggest` del texto.
- **Depurador** (D-62): cada función compilada (`Function`) lleva su nombre y
  el checker apunta cada variable (`DebugVar`: función, nombre, tipo, ranura
  y línea donde se declara). `CompiledScript.setBreakpoints` crea un
  `Tracer` la primera vez (fuera del bucle caliente): 4.096 pasos con línea,
  si es de la biblioteca, la función y una copia de sus ranuras. Mientras
  hay puntos de parada (o «pausar en el siguiente ciclo»), cada paso del
  ciclo se graba sin reservar memoria (`AllocationTest`); si alguno toca un
  punto, `stopped()`. El mundo no se mueve mientras corre un script, así que
  recorrer la grabación hacia atrás y hacia delante enseña exactamente lo
  que pasó, sin volver a ejecutar nada. La batalla avisa
  (`takeDebugStop`) y `WaveLoop` deja de avanzar hasta `resumeDebug()`;
  depurar no cambia la partida (`WaveLoopTest`).

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
- **Estrellas y trofeos en enteros** (D-57): no hay decimales que acumular.
  `Progression` decide qué metas de la oleada son nuevas (superarla, sin
  fugas, el reto con `ChallengeJudge`) y suma una estrella por cada una; el
  primer derribo de un jefe suma un trofeo. Cada meta es un bit por oleada en
  `Progress`, así que repetir no paga.
- **Carrera** (fase 9b, D-63): `Career` guarda lo que no es economía:
  récords por oleada (`WaveRecord`: la más rápida, la de menos disparos y la
  de menos memoria), la racha y su puntuación, saludos, excepciones vistas,
  logros, y el nombre y el sombrero de cada torreta. `Career.apply` corre
  después de `Progression.apply` y devuelve un `CareerOutcome` (marcas
  nuevas, racha, puntos y logros nuevos). Los logros salen de
  `data/career.json` (tipo, objetivo, sombrero); `GameData` comprueba que
  cada objetivo sea alcanzable. Al cargar una partida se desbloquean los ya
  alcanzados.
- **«Probar»** (D-65): `WaveLoop.trial(oleada)` juega una oleada ya
  alcanzada de principio a fin al momento, con el código actual y recargando
  a mano en cuanto una torreta se vacía, con semilla negativa (nunca la de
  una batalla de verdad). No toca el progreso, la carrera, la revisión ni el
  contador de batallas.
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
- **Pantalla de combate (fase 4)**: `BattleScreen` mueve el `WaveLoop` de
  `core-sim` con un `FixedStepClock` (ticks enteros; ×3 = tres veces más
  ticks por frame; máximo 5 ticks por frame y unidad de velocidad), pinta el
  mundo con `WorldRenderer` y pone encima un HUD de scene2d: oleada, núcleo,
  estrellas y trofeos, selector de velocidad ×1/×2/×3 (D-47), avance automático, «Desplegar oleada N» (D-41) y una línea
  con el resultado de la última oleada. Encima de cada torreta, la excepción
  que la colgó con su línea, «Recargando…» o «¡Sin balas!». Un clic izquierdo
  en una torreta la recarga a mano (D-42). Los textos solo se rehacen cuando
  cambia su valor, para no reservar memoria en cada frame.
- **Conversión pantalla ↔ mundo** en `ShapeWorldRenderer` a mano con los
  límites del viewport: la cámara 2D no gira ni hace zoom, y
  `Camera.unproject` invierte la y con la altura global de la ventana.
- **Paneles (fase 5)**: barra con Código, Tienda, Mapa e Informe; cada botón
  abre su panel a la derecha (o lo cierra) sin parar la oleada (D-52). El
  **parte de oleada** sale en el centro mientras una oleada espera: enemigos
  por tipo con su número y qué los hace especiales (textos con los números de
  `enemies.json`), las tres estrellas de la oleada con su reto y cuáles ya
  tiene, y el botón «Desplegar». El **informe** se abre solo al
  acabar cada oleada si no hay otro panel abierto: estrellas ganadas, las tres
  metas de la oleada con ✓/○ (si el reto falla, cuánto se hizo: «esta vez:
  59»),
  fugas por tipo y por qué tramo del camino, y por torreta disparos, al aire,
  balas en enemigos ya muertos, recargas, segundos colgada y cada excepción
  con su línea (`ReportLines`, texto sin libGDX para poder probarlo).
- **Editor**: widget scene2d propio (`CodeEditor`; el `TextArea` de libGDX no
  sirve para código). El texto vive en `EditorDocument` (Java puro, con
  tests): cursor, selección con Mayús, Ctrl+A/C/X/V, Ctrl+Z/Y, sangría
  automática tras `{` y al cerrar `}`, Tab a 2 espacios. Colores con el
  **lexer del propio intérprete** (`Highlighter`): lo que se pinta como
  palabra clave es lo que el juego lee como tal. Números de línea, una barra
  azul por instrucción que ocupa memoria (D-06), línea roja y subrayado en
  cada error, y debajo los errores con su línea (primero los de la línea bajo
  el ratón). Contador «Memoria: N/12». Una pestaña por torreta construida y,
  con los métodos comprados, la pestaña «Biblioteca» (sin contador de
  memoria): cambiarla vuelve a compilar todas las torretas contra ella.
  Compila con cada cambio: el código que compila pasa a la torreta y entra
  en la siguiente oleada (o en la que espera); el que no, se queda en el
  editor y la torreta sigue con su última versión buena (D-51).
- **Tienda**: el árbol entero con precios desde el principio (plan), cada
  pieza con lo que «te quita de encima» y, si no se puede comprar, por qué
  (estrellas, requisito, fuera del MVP, tope de sintaxis D-34). Precios en
  estrellas; el botón va debajo del texto para que quepa. Las mejoras con
  algún nivel llevan «Devolver (+N)» (D-59). Al final, los
  trofeos de jefe, que se gastarán en los actos siguientes. Al comprar una
  pieza se abre su tarjeta con el ejemplo de `shop.json`, que se valida al
  cargar: compila con esa pieza y cabe en `exampleLines` (5, plan). El de
  métodos es una biblioteca (`exampleInLibrary`) y la tarjeta lo dice. El
  hardware muestra nivel, efecto y precio; la torreta extra aparece en el
  siguiente hueco.
- **Mapa de oleadas** (D-49): un acto cada vez (tantas oleadas como el ritmo de
  los jefes, 10), con botones «Acto 1», «Acto 2» y el de la oleada actual
  abierto; las oleadas se nombran acto-oleada (la 11 es «2-1»). Cada acto, en un camino de
  puntos; candado en las no alcanzadas, bandera roja en la actual, el jefe
  más grande; bajo cada oleada alcanzada, sus estrellas (●●○). Clic en una alcanzada: se abandona la oleada en curso sin cobrar
  ni contar como derrota y la elegida espera a «Desplegar» (D-50).
- **Manual** (fase 5b, D-54): botón Manual en la barra. Índice por capítulos
  (primeros pasos, lo que hace la torreta, lo que se le pregunta a un
  enemigo, sintaxis, excepciones, errores al escribir, enemigos y recetas) y
  una página por llamada, pieza, excepción y enemigo, con anterior/siguiente.
  Lo que el jugador aún no tiene sale con candado y qué lo trae (pieza de la
  tienda, oleada), como la tienda y el mapa. Cada ejemplo se pinta con los
  colores del editor (`CodeView`, mismo `Highlighter`), dice cuánta memoria
  ocupa y tiene un botón «Copiar» que lo deja en el portapapeles para
  pegarlo en el editor con Ctrl+V. Estructura, desbloqueos y código en
  `data/manual.json`; `GameData` exige una página por cada llamada, pieza del
  MVP, enemigo y excepción, y compila cada ejemplo con lo que pide su página.
  Los textos van en `manual.*` y citan los números de los datos por nombre
  (`{cargador}`, `ManualFacts`), sin `MessageFormat`.
- **Fase 9b**:
  - **Ventanas** (D-67): `FloatingWindow` (se mueve por el título, cambia de
    tamaño por los bordes, se pliega con – y se cierra con ×) y `Windows`
    (una por clave; abrir una que ya está la trae delante). En ventana van el
    código separado de una pestaña («Separar en ventana»: otro `CodeEditor`
    sobre el mismo `EditorDocument`, con los mismos errores y puntos de
    parada; cerrarla no borra nada), una página del manual, la salida y el
    depurador. Los paneles de la derecha siguen como en D-52; el de código
    y el parte se desplazan si no caben (a 720 px tapaban la barra).
  - **Salida** (`OutputPanel`): lee el `OutputLog` de la batalla después de
    cada tick (también acaba de leer la batalla que termina) y apunta cada
    excepción que cuelga una torreta. Guarda las últimas 200 líneas y
    separa las oleadas.
  - **Depurador** (`DebuggerPanel`): se abre solo cuando una torreta se para;
    «< Paso» y «Paso >» recorren la grabación, la línea se marca en amarillo
    en el editor (abre su pestaña), lista las variables de la función de ese
    paso declaradas hasta su línea y, con el ratón sobre un nombre en el
    código, dice cuánto vale. «Continuar», «Siguiente ciclo» y cerrar la
    ventana siguen la oleada. Los puntos de parada se ponen con un clic en
    el número de línea (punto rojo) y no se guardan.
  - **Autocompletado** (D-66): `Completer` (Java puro, con el lexer del
    intérprete para sacar las variables y métodos del propio código) y
    `Vocabulary` (llamadas, palabras y constantes que el jugador ya tiene, y
    los métodos de la biblioteca). Tab acepta, flechas eligen, Esc cierra,
    Ctrl+Espacio abre; tras un punto, las llamadas del enemigo y de la lista.
  - **Logros y Taller** (`CareerPanel`): racha, cada logro con lo que falta y
    su sombrero; en el Taller, nombre y sombrero de cada torreta. Un logro
    nuevo sale como aviso y va a `PlatformServices.unlockAchievement`; al
    arrancar se le mandan todos (por si la tienda se perdió alguno).
  - **Mundo**: `WorldRenderer` gana `enemyAt` (para el aviso al pasar el
    ratón por un enemigo o una torreta) y `setHat` (el sombrero de cada
    hueco, cosmético; D-40 sigue: el render no lee la carrera, se lo dan).
  - **Parte e informe**: el parte enseña los récords de la oleada y el botón
    «Probar», cuyo informe (`ReportLines.trial`) dice que es una prueba; el
    informe de una oleada añade los récords batidos, la racha y los logros.
  - **Manual**: capítulo «Herramientas» (ventanas, salida, depurador, probar
    y logros); una página puede abrir con una historia (`.story`), esconder
    una pista tras «Mostrar pista» (`.hint`) y acabar con «Ver también»
    (`seeAlso` en `manual.json`, comprobado al cargar); «Abrir en ventana».
  - **Ajustes**: «Nueva partida experta» (D-64).
- **Textos**: `I18NBundle` con `messages.properties` en español. El código
  solo usa claves. Los módulos puros devuelven códigos + argumentos; quien
  pinta el texto es `game` (o `sim-cli`, que lee el mismo fichero).
- **Audio**: sonidos sintetizados por código propio, sin ficheros de terceros.

## 7. Guardado, ajustes y robustez

- **Ruta** (compatible con Steam Auto-Cloud), `UserDataDirectory`:
  - Windows: `%APPDATA%\JavaWaveDefense\`
  - Linux: `$XDG_DATA_HOME/java-wave-defense/` (o `~/.local/share/…`)
  - macOS: `~/Library/Application Support/JavaWaveDefense/`
  - Dentro: `save.json` (y `.bak`), `settings.json` (y `.bak`) y `logs/`.
  - No uso las `Preferences` de libGDX: van a `~/.prefs/` y no son atómicas.
- **Escritura atómica** (`AtomicFileStore`, en `platform`): escribir
  `save.json.tmp`, `force` a disco, copiar el actual a `save.json.bak` y
  renombrar con `ATOMIC_MOVE` (si el sistema no lo soporta,
  `REPLACE_EXISTING`). Un `.tmp` que quede de un corte se ignora.
- **Formato** (`SaveGame` + `SaveCodec`, en `core-sim/save`): JSON con
  `"version"`, el progreso (`Progress.Snapshot`: estrellas, trofeos, sintaxis,
  hardware, oleada actual y máxima, avance automático, oleadas superadas, sin
  fugas, con el reto cumplido y jefes derribados), el código que corre cada
  torreta, el borrador de cada editor, la biblioteca (la que corre y su
  borrador), el contador de batallas (semilla) y la carrera
  (`Career.Snapshot`: récords, racha, saludos, excepciones vistas, logros,
  nombres, sombreros y si es una partida experta). Versión 4 (fase 9b): la
  migración `AddCareer` añade una carrera vacía. Versión 3 (fase 9): la
  migración `AddLibrary` añade una biblioteca vacía. Versión 2 (fase 6c): la migración `MoneyToStars` cambia el dinero de la
  versión 1 por las estrellas que habrían dado sus metas (superadas + sin
  fugas) y un trofeo por jefe derribado. Un nivel de hardware por encima del
  último que vende `shop.json` se queda en el último. Al cargar se aplican las
  migraciones `vN → vN+1` en orden; un fichero de una versión más nueva o sin
  migración se rechaza. Lectura estricta: campos desconocidos o que faltan
  son error.
- **Carga comprobada**: `Progress.restore` valida contra los datos (oleadas
  en rango, cada pieza con la que exige, hardware que se vende, torretas que
  caben). Un script guardado que ya no compila vuelve al inicial y se
  conserva su borrador; una biblioteca que ya no compila se vacía y también
  conserva el suyo. La oleada actual espera a «Desplegar» aunque esté
  superada: cargar nunca arranca una oleada sola (D-55).
- **Carga tolerante**: si `save.json` no se puede leer se prueba `.bak` y se
  avisa («se ha recuperado la copia anterior»); si tampoco, partida nueva y
  aviso con la ruta del fichero roto. Los ficheros rotos se conservan como
  `save.json.corrupt-<fecha>`. El juego nunca se cierra por eso.
- **Guardado automático** (`AutoSaver`, D-55): `WaveLoop.revision()` crece con
  todo lo que merece guardarse (código, compras, oleada acabada, oleada
  elegida en el mapa, avance automático). Tras un rato sin cambios, el primer
  cambio se guarda al momento; mientras se escribe, como mucho uno por
  segundo. Al cerrar o pasar a segundo plano, al momento. Si falla la
  escritura, aviso y se reintenta. Una partida nueva se guarda al empezar.
- **Ajustes** (`Settings`, `settings.json`, D-56): pantalla completa, tamaño
  de la interfaz (100–200 %) y tamaño del código (12–22 px). Las fuentes se
  generan a su tamaño en pantalla y se escalan de vuelta, y el `ScreenViewport`
  usa `unitsPerPixel = 1 / escala`: la interfaz mantiene sus medidas y el
  texto sale nítido. Cambiar un tamaño rehace la piel y la pantalla en el
  frame siguiente; el código no se pierde porque los borradores viven en
  `WaveLoop`. «Nueva partida» pide confirmación y no toca los ajustes.
- **Logs** (`GameLog`): `java.util.logging` con `FileHandler` rotativo (5
  ficheros × 1 MB) en `logs/`. `Gdx.app.log` va ahí (también la medida de la
  decisión C, D-45).
- **Handler global**: `Thread.setDefaultUncaughtExceptionHandler` registra
  cualquier excepción sin capturar. Un frame que falla se registra (una vez
  por error distinto), sale un aviso y el juego sigue.

## 8. Steam (sin integrar)

Todo lo de Steam pasará por `PlatformServices` (`unlockAchievement`,
`isCloudEnabled`…); en el MVP, `NoOpPlatformServices`. La nube se hará con
Auto-Cloud (por rutas), así que el guardado no necesita API. Los pasos para
publicar (depots, Auto-Cloud por sistema, steamworks4j, SteamPipe) están en
`docs/STEAM.md`.

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

### 9.1 Empaquetado (fase 7)

- `lwjgl3/build.gradle.kts` aplica Construo. `distJar` hace un único jar con
  el juego, sus librerías, los nativos de los tres sistemas y `assets/`
  (`*-all.jar`); Construo lo junta con un Java recortado y su lanzador nativo
  (roast) en un zip por sistema, en `build/dist/`:
  `packageWinX64`, `packageLinuxX64`, `packageMacM1` y `packageMacX64`.
- **Java que va dentro**: Temurin `runtimeRelease` (hoy 21.0.8+9), bajado de las
  *releases* de GitHub de Adoptium con su SHA-256 (api.adoptium.net no siempre
  es accesible). El build falla si su versión mayor no es la de
  `.java-version`. Subir de versión = cambiar `runtimeRelease`, las cuatro
  sumas y, si cambia la mayor, `.java-version`.
- **Módulos de jlink**: `java.base`, `java.desktop`, `java.logging` y
  `jdk.unsupported` (lo que usan el juego, libGDX, LWJGL y Jackson). El zip
  pesa unos 45 MB. Si una versión nueva de una librería pide otro módulo, el
  juego empaquetado falla al arrancar: se ve en el log y se añade aquí.
- **macOS**: el lanzador ya arranca en el primer hilo (`runOnFirstThread`) y
  pasa `-Dwavedefense.relaunched=true` para que `StartupHelper` no relance la
  JVM (lo haría con `java.management`, que no va en el runtime). La app no
  está firmada.
- **Workflow `Package`** (`.github/workflows/package.yml`): solo a mano o con
  una etiqueta `v*`. Pasa `./gradlew build`, genera los cuatro zips con nombres
  para jugadores (`JavaWaveDefense-Windows.zip`…) y los deja como *artifacts* 14
  días. Con una etiqueta, o a mano con el campo «version» relleno, además
  publica una **Release** con los zips y las instrucciones de
  `.github/release-notes.md`; a mano crea también la etiqueta `v<versión>` en el
  commit empaquetado (única vez que el workflow escribe en el repo:
  `contents: write`). La versión del juego sale de la etiqueta o del campo
  (`-PgameVersion`); sin ninguno es `0.0.0-dev`. El CI normal no empaqueta: gasta minutos y no
  añade nada en cada commit.
- Publicar en Steam: `docs/STEAM.md`.

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
| `disparar()` sin enemigos en rango | Bala «al aire»: gasta bala y calor | Yeray (D-05) |
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
| Dinero por enemigo | Ya no existe: lo sustituyen las estrellas (D-57, §10.3) | Plan, cambiado por Yeray |
| Nº de enemigos | `min(6 + 2n, 60)` | Plan |
| Núcleo | **10** vidas por oleada; cada enemigo que llega quita 1 | Yeray (D-35; el plan decía 20) |
| Regenerador | Vida ×2; se cura si pasa 1 s sin daño, a 50 % de vida máx./s; 100 u/s | 1 s Plan; resto No viene del plan (D-37) |
| Blindado | Vida ×2; −80 % de daño con NORMAL; 80 u/s | 80 % Plan; resto No viene del plan (D-37) |
| Enjambre | Grupos de 10, juntos; vida ×0,3, 200 u/s; cada miembro cuenta como 1 enemigo | 10 Plan; resto No viene del plan |
| Jefe 1 | Vida = 20 × vida de la oleada 10; alterna blindaje cada 5 s; si llega al núcleo se pierde la oleada; 50 u/s | Plan; velocidad No viene del plan |
| Recompensa del jefe | 1.er derribo = 1 trofeo; los siguientes, nada (D-57) | Yeray (D-57; sustituye a D-10) |
| Básico | 100 u/s | No viene del plan (D-12, D-22) |
| Corredor (acto 2) | 300 u/s (el triple); vida ×0,5 | Triple Plan; vida PROVISIONAL (D-60) |
| Mutante (acto 2, desde la 14) | Débil a una munición que cambia cada oleada (`mutantWeakness` en `waves.json`, la dice el parte); la otra le hace un 80 % menos; vida ×1,5; 100 u/s; `e.mutante()` lo reconoce (con Variables) | «Su punto débil cambia cada oleada» Plan; la munición como punto débil, Yeray (D-61); números PROVISIONAL |
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
| 11 | 28 | 6 B + 8 C + 4 R + 10 E (C = Corredor) |
| 12 | 30 | 4 B + 4 Bl + 10 C + 10 E + 2 R |
| 13 | 32 | 12 C + 4 Bl + 10 E + 4 R + 2 B |
| 14 | 34 | 6 B + 12 M + 6 C + 10 E (M = Mutante, débil a PERFORANTE) |
| 15 | 36 | 4 Bl + 12 M + 8 C + 10 E + 2 R (débil a NORMAL) |
| 16 | 38 | 14 M + 4 Bl + 10 E + 8 C + 2 B (débil a PERFORANTE) |

### 10.3 Economía, tienda y progreso

Economía de estrellas (D-57, cambia el «Farmeo» del plan). Los precios y los
retos son PROVISIONAL: salen de calibrar con `sim-cli` (ver
`docs/BALANCE.md`).

| Regla | Valor | Origen |
|---|---|---|
| Estrellas por oleada | 3, cada una **una sola vez**: superarla, superarla sin fugas y cumplir su reto. Repetir no paga | Yeray (D-57) |
| Retos | Uno fijo por oleada en `waves.json`: 1 ningún disparo al aire · 2 no recargar a mano · 3 memoria ≤ 4 · 4 ninguna bala a enemigos ya derribados · 5 ≤ 52 disparos · 6 memoria ≤ 10 · 7 ≤ 100 disparos · 8 ≤ 90 disparos · 9 memoria ≤ 9 · 10 ninguna excepción · 11 ninguna bala a enemigos ya derribados · 12 memoria ≤ 10 · 13 ninguna excepción · 14 ninguna excepción · 15 memoria ≤ 8 (pensado para la biblioteca) · 16 ningún disparo al aire. Cuentan todas las torretas juntas; memoria = la torreta que más ocupa | Yeray (uno fijo por oleada); retos PROVISIONAL |
| Trofeos | 1 por jefe, la primera vez que cae. Se gastarán en mejoras especiales en los actos siguientes | Yeray (D-57) |
| Estrellas al empezar | 0 | PROVISIONAL |
| Sintaxis | if 1 · Variables 2 (req. if) · `&& || !` 3 (req. Variables) · while 4 (req. `&&`) · for 4 (req. while, fase 8) · métodos 6 (req. for, fase 9); aún sin vender: try/catch 7, clases 8 | Orden y requisitos Plan; precios PROVISIONAL (D-57, D-60) |
| Árbol visible | Las 8 piezas con precio desde el inicio; las 4 de fuera del MVP, bloqueadas | Plan |
| Hardware | Daño, cargador y disipador: 4 niveles a 1 estrella cada uno. Con for: memoria (+4 líneas por nivel) 2 y 3; procesador (+0,5 ciclos/s, máximo 5) 2, 2, 3, 3, 4, 4. Torretas extra 2, 3 y 4 | PROVISIONAL (D-57, D-60) |
| Todo lo que se vende | 20 en sintaxis + 44 en hardware; las oleadas 1–16 dan 48 estrellas | PROVISIONAL (D-60, D-61) |
| Efecto por nivel | Aditivo sobre la base: daño 5 → 6,25 → 7,5 | Plan («cada nivel… da lo mismo») + PROVISIONAL (D-13) |
| Ámbito del hardware | Global, para todas las torretas | PROVISIONAL (D-13) |
| Torretas | «Torreta extra»: la 2.ª con `&&`, la 3.ª y la 4.ª con for; 4 huecos (los dos nuevos junto a los tramos del medio) | Plan (3 o 4 en el acto 2) + PROVISIONAL (D-60) |
| Topes de hardware | Un nivel de hardware no se compra hasta tener cierta sintaxis (`shop.json`, `caps`): daño y cargador, nivel 1 con if, 2 con Variables, 4 con while; disipador y torreta extra, con `&&`. La tienda dice «Necesitas variables para seguir mejorando el daño» | Yeray (red de seguridad, D-34); topes concretos PROVISIONAL |
| Devolver | Daño, cargador y disipador se devuelven nivel a nivel por lo que costaron; la sintaxis y la torreta extra, no | Yeray (D-59) |
| Avance automático | Interruptor; apagado repite la oleada. Encendido en partida nueva | Plan + PROVISIONAL (D-02) |
| Velocidad ×3 | Solo en oleadas superadas alguna vez sin fugas | Plan |
| Derrota | Conservas todo, vuelves a la oleada anterior (mín. 1) con avance automático apagado | Plan |

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
- **Política** (fase 6c, estrellas): juega con su mejor script; si supera
  una oleada sin su reto y alguno de sus scripts lo cumple, la repite con
  ese (sabe de antemano cuál: es una cota alta). Compra la siguiente
  sintaxis en cuanto puede; con toda, el hardware que más sube su `k`
  contra la oleada siguiente por estrella, y si ninguno ayuda se guarda las
  estrellas. Atascado tras una derrota: sintaxis si llega; si no, el
  hardware que más ayuda contra ese muro (aunque no ayude nada), y vuelve a
  intentarlo. Sin nada que comprar, repite la primera oleada anterior que aún
  le dé una estrella (sin fugas o su reto). Si no queda ninguna, prueba a
  devolver un nivel de mejora y comprar otro (D-59) mientras eso le haga más
  fuerte. Si tampoco, la partida está **bloqueada** y sale en
  «Contradicciones». Usa ×3 donde se puede. El
  tiempo de escribir código no cuenta.
- **Hasta la última oleada** (fase 8): la partida acaba al superar la última
  (`lastWave`, hoy 13); el informe da también cuándo cae el Jefe 1. El
  jugador ahorra para una pieza solo desde la oleada anterior a la que «llega»
  (`shop.json`); antes, sus estrellas van al hardware que más le ayuda.
  Juega con la memoria que tiene: los scripts de nivel 5 (`L5…`, con for)
  eligen al enemigo más cerca del núcleo, y el mejor ocupa 13 líneas, así que
  necesita la mejora de memoria.
- **Sin retos**: la misma partida con un jugador que nunca intenta los
  retos (solo los cumple si le salen solos). Mide si hacen falta para llegar
  al jefe; hoy sí (se bloquea en la oleada 7).
- **Pasadas por muro**: oleadas anteriores repetidas entre la primera
  derrota en una oleada nueva y su primera victoria, ahora para sacar
  estrellas que faltaban. Regla del plan: ≤ 5.
- **Regla del doble** (PROVISIONAL D-17, métrica cambiada en la fase 3): para
  cada pieza, desde el estado justo antes de comprarla (o, si nunca la
  compra, desde que empezó a ahorrar para ella) y en la oleada en que
  «llega» (`shop.json`), `k` = mayor multiplicador de vida enemiga con el que
  la oleada sale **sin fugas**. Se compara la pieza con su script frente al
  mejor hardware del mismo precio en estrellas **dentro de los topes** (D-34). Variables es
  excepción aceptada (D-39); el informe
  da también la proporción sin topes como referencia. En cada lado se prueban
  todas las variantes del script que caben en memoria (`L3-ammo`,
  `L3-ammo-b`…) y se toma la mejor. Cumple si la proporción es ≥ 2. La
  propuesta original («la oleada se supera») no servía: con 20 vidas, las
  oleadas 1–6 no se pueden perder y `k` se saturaba en el máximo.
- **Caso D-20**: la fila VARIABLES es la oleada 4, la primera con
  regeneradores.
- **Biblioteca y Mutante** (fase 9): un script de referencia puede llevar su
  biblioteca al lado (`L6-ammo.lib.txt`). El jugador lee el parte: donde pone
  `{PERFORANTE_SI}` escribe cuándo usar perforante con el objetivo `o`
  (blindado, o un Mutante si esa oleada es débil a perforante). Sin métodos
  esa línea está en cada torreta; con métodos, una vez en la biblioteca. El
  simulador no mide lo que cuesta cambiar cuatro scripts: por eso los métodos
  no le dan fuerza.
- **Barrido del Regenerador**: `--sweep-regenerator FILE` juega una partida
  entera y mide la regla del doble de Variables para cada combinación de
  espera, curación, vida y separación del Regenerador (rejilla en
  `RegeneratorSweep`), sin tocar los datos del disco. Tarda unos 7 min.
- **Conclusiones**: el informe separa «Contradicciones con el plan» (reglas
  del plan que no se cumplen) y «Avisos». Nada se ajusta por mi cuenta.

### 11.1 Simulación (`core-sim`) tal como ha quedado

- `Battle` juega una oleada: aparición → ciclos de torreta → balas →
  enemigos → fin. 60 ticks/s; ciclo de torreta cada 30 ticks (con el
  procesador, 60 / ciclos por segundo, redondeado).
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
- `BattleEvents` (D-40): anillo de capacidad fija (4.096) con columnas
  primitivas; la batalla escribe disparos, impactos, bajas, fugas,
  excepciones, recargas y cargadores vacíos, y nunca lee. Enemigos y balas
  guardan su posición del tick anterior para interpolar.
- `WaveLoop` (D-01): una batalla tras otra. Una oleada nunca superada espera
  a `deploy()`; las demás empiezan en cuanto acaba la anterior. Mientras
  espera, la batalla de esa oleada ya está construida para poder pintarla.
  Cada batalla lleva como semilla un contador (D-44).
- La lógica de partida (`Progress`, `Shop`, `Progression`): estrellas por
  metas nuevas (D-57), ×3 en oleadas superadas sin fugas y derrota = vuelves
  una oleada atrás con el avance automático apagado y conservas todo (plan).

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
| D-05 | Disparo sin objetivo y «más cercano» | CERRADA (Yeray, tras jugar la fase 4) | Bala al aire (gasta bala y calor): con `disparar();` la torreta vacía el cargador antes de que llegue nadie, y eso empuja a programar «dispara solo si hay enemigos». «Más cercano» = a la torreta. |
| D-06 | Línea de memoria | CERRADA | Una sentencia = una línea (4.4). Se explica en una frase y el editor muestra el contador en vivo. |
| D-07 | Qué trae cada pieza | CERRADA | Tabla de 4.3. |
| D-08 | División entre cero | CERRADA | `ArithmeticException` como en Java; cuelga 3 s y muestra la línea. |
| D-09 | Perforante | PROVISIONAL | Solo atraviesa blindaje, no enemigos. |
| D-10 | Recompensa del jefe | SUSTITUIDA por D-57 | Era 549 la primera vez y luego como 20 enemigos de la oleada 10; ahora, un trofeo la primera vez. |
| D-11 | Decimales del dinero | SUSTITUIDA por D-57 | Ya no hay dinero: estrellas y trofeos son enteros. |
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
| D-30 | El jefe | PROVISIONAL | Empieza sin blindaje y alterna cada 5 s. Da un trofeo la primera vez (D-57). |
| D-31 | Lecturas de la torreta | PROVISIONAL | `hayEnemigos()` y `enemigoMasCercano()` solo ven enemigos en rango. |
| D-32 | Orden de aparición | PROVISIONAL | Grupos en el orden de `waves.json`; huecos repartidos por igual en 20 s; el enjambre ocupa un hueco. |
| D-33 | Jugador de referencia | PROVISIONAL | Política de compra y 1 s de reacción para recargar a mano (sección 11). Solo afecta al informe, no al juego. |
| D-34 | Topes de hardware según la sintaxis | CERRADA la idea (Yeray); topes PROVISIONAL | Red de seguridad de la regla del doble. Topes en 10.3, configurables en `shop.json`; solo pueden esperar a piezas del MVP (se valida al cargar). |
| D-35 | Vidas del núcleo | CERRADA (Yeray, cambia el plan) | 10 por oleada en vez de 20. |
| D-36 | Objetivo por defecto | CERRADA (Yeray) | El más cercano a la torreta, como dice el plan. Sigue siendo configurable en `turret.json` para medir. «Al azar» llevaba Variables a ×2 frente a no tenerla (2,03), pero contradice el plan. |
| D-38 | Separación al salir por tipo | CERRADA (Yeray) | `spawnGapFactor` por tipo, **1 en todos** (sin efecto). Hacía falta para barrer la separación del Regenerador sin tocar las oleadas 1–3. Barrido en `docs/BARRIDO-REGENERADOR.md`: ninguna combinación de los cuatro números lleva Variables a ×2 (máximo 1,20, con vida ×1); no se aplica porque gana poco ratio a cambio de acortar el camino al Jefe 1 y de bajar la ganancia de Variables. No se hacen más barridos sobre esto. |
| D-40 | Camino a un render 3D | CERRADA (Yeray) | A largo plazo el juego se verá en 3D; el MVP (fase 4) sigue en 2D. Para no cerrar la puerta: la simulación sigue en un plano 2D y no sabe nada del render; todo el dibujo del mundo pasa por una única interfaz (`WorldRenderer`, en `game`) que solo lee el estado de la simulación, para cambiar la implementación 2D por una 3D sin tocar pantallas, UI ni `core-sim`; la UI sigue en scene2d. No se añade ninguna otra abstracción por esto. Además: (1) `WorldRenderer` contiene la cámara y dice qué hay bajo el puntero (de pantalla a mundo); (2) proyecta un punto del mundo a pantalla, para colocar la UI de scene2d pegada al mundo; (3) la simulación guarda los sucesos de un instante (disparo, impacto, muerte, excepción…) en un búfer reutilizable sin reservas; acumula todo lo ocurrido desde la última lectura del render (a ×3 hay varios ticks por frame); el informe de oleada no depende de él (sus datos los acumula la simulación aparte), y el búfer no influye en la simulación ni en su determinismo (la simulación solo escribe en él, nada de la simulación lo lee y la lectura del render lo vacía); tiene capacidad fija y, si nadie lo lee (`sim-cli`, tests) o se llena, se descartan los sucesos más antiguos, lo que solo afecta a efectos visuales; (4) la simulación conserva la posición anterior de cada entidad para que el render interpole entre ticks. |
| D-41 | Pantalla de combate antes de la fase 5 | PROVISIONAL | Mientras no existan el parte, el informe ni la tienda: un botón «Desplegar oleada N» hace de parte, una línea con el resultado hace de informe y cada torreta ejecuta su script inicial (D-14). Cada arranque es una partida nueva (el guardado es de la fase 6). |
| D-42 | Recarga a mano | PROVISIONAL | Clic izquierdo sobre la torreta (su cuadrado más 8 px de margen). Funciona como D-03: también con el cargador lleno y con la torreta colgada (D-03b); no mientras ya recarga. |
| D-43 | Botón ×3 | PROVISIONAL | El botón guarda lo que pide el jugador; solo se aplica en oleadas superadas sin fugas y en las demás va a ×1 (y el botón se ve desactivado). |
| D-47 | Selector de velocidad | CERRADA la forma (Yeray); regla de ×2 PROVISIONAL | Plegado muestra la velocidad en uso; con el ratón encima se abre a lo ancho en ×1, ×2 y ×3 y un clic elige. Las velocidades están en `balance.json` (`speeds`; el ×2 no viene del plan). ×2 sigue la misma regla que ×3: solo en oleadas superadas sin fugas. |
| D-48 | El juego sigue al mover o redimensionar la ventana | PROVISIONAL | En Windows, arrastrar o redimensionar la ventana bloquea el bucle del juego hasta soltar. El lanzador dibuja (y avanza) un frame en cada cambio de posición que GLFW avisa desde ese bucle, como libGDX ya hace al redimensionar, y el juego mide su propio tiempo entre frames (`FrameTimer`) porque el del backend no se actualiza ahí. Con el ratón quieto en mitad del arrastre Windows no avisa y el juego espera. |
| D-49 | Mapa de oleadas (fase 5) | CERRADA la idea (Yeray, no viene del plan); detalles PROVISIONAL | Pantalla de mapa al estilo de un mapa de niveles: las oleadas como puntos unidos por un camino; las ya alcanzadas se pueden elegir; las demás llevan candado y no se pueden elegir; la actual lleva una banderita roja; el jefe (oleada 10) se distingue. Un mapa por acto: el MVP tiene uno (oleadas 1–10); las pestañas de actos quedan para cuando haya más oleadas. Tras jugar una oleada elegida, el avance automático sigue desde ella como hoy (encendido: a la siguiente; apagado: la repite). Sin selector de dificultad: no está en el plan. Amplía el farmeo del plan («Farmeo»: repetir oleadas superadas); las oleadas antiguas pagan menos, así que no da ventaja. |
| D-50 | Elegir oleada en el mapa con una en curso | PROVISIONAL | La oleada en curso se abandona sin cobrar y sin contar como derrota; la elegida espera a «Desplegar» aunque ya esté superada (es una elección del jugador, que quiere ver el parte). |
| D-51 | Código con errores en el editor | PROVISIONAL | Se queda en el editor marcado en rojo; la torreta sigue con la última versión que compilaba. El código que compila se aplica al momento: en la oleada siguiente, o en la que espera a «Desplegar» (plan, «Edición en caliente»). |
| D-52 | Dónde van los paneles | PROVISIONAL | A la derecha, uno a la vez, encima del mundo (500 px); el mundo sigue corriendo y se puede recargar a mano en la parte visible. El informe se abre solo al acabar una oleada si no hay otro panel abierto. |
| D-53 | Ejemplos de las tarjetas de la tienda | PROVISIONAL | Escritos por mí (el plan solo pide «cinco líneas que funcionen tal cual»), en `shop.json`, validados al cargar. |
| D-55 | Guardado (fase 6) | CERRADA (Yeray) lo que se guarda; detalles PROVISIONAL | Una sola partida, automática, sin botón de guardar: al acabar una oleada, al comprar, al cambiar el código (como mucho una vez por segundo mientras se escribe) y al cerrar. Se guardan el borrador de cada editor y la versión que compila. La oleada en curso no se guarda: al volver, la oleada actual espera a «Desplegar». |
| D-56 | Ajustes (fase 6) | CERRADA (Yeray) qué ajustes; valores PROVISIONAL | Pantalla completa, tamaño de la interfaz (100, 125, 150, 175 y 200 %), tamaño del código del editor y del manual (12 a 22 px, 14 por defecto) y «Nueva partida» con confirmación. Se aplican al momento y se guardan aparte de la partida. |
| D-57 | Economía sin farmeo | CERRADA la idea (Yeray); números PROVISIONAL | **Cambia el plan** («Farmeo»: repetir paga, oleada perfecta +25 %, regla de las 5 pasadas). Repitiendo una oleada a ×3 se compraba toda la sintaxis en minutos sin programar mejor (oleada 3 perfecta a ×3: 159 por minuto; la sintaxis cuesta 385). Nuevo modelo: cada oleada da 3 estrellas que se cobran **una sola vez**: superarla, superarla sin fugas y un reto de código fijo de esa oleada (menos memoria, sin disparos al aire, sin excepciones…; son los «contratos» del plan, que entran ya). Las estrellas compran sintaxis y hardware. Además, una segunda moneda que solo dan los jefes, la primera vez, para lo más grande; en el MVP solo hay un jefe, así que queda preparada para los actos siguientes. Repetir una oleada solo sirve para sacar las estrellas que falten mejorando el código. Implementada en la fase 6c: precios, retos y calibración en §10.3 y `docs/BALANCE.md`; el simulador comprueba que no haya partidas bloqueadas y mide una partida sin retos. |
| D-58 | Nombres y manual para principiantes | CERRADA (Yeray) | Las piezas se llaman «Decidir (if/else)», «Recordar (variables)», «Combinar condiciones (&&, ||, !)» y «Repetir (while)» (y «Contar (for)», «Reutilizar código (métodos)», «Atrapar errores (try/catch)», «Tipos de torreta (clases)»). Cambia el texto del plan: «Aún no has desbloqueado «Repetir (while)»». El manual se escribe para quien nunca ha programado: capítulo «Empieza aquí», «Símbolos y palabras» (cada símbolo, glosario y cómo leer el código en voz alta), títulos que dicen qué hace cada orden y ejemplos comentados. |
| D-59 | Devolver mejoras | CERRADA (Yeray) | Con estrellas que no se repiten, una mejora inútil podía dejar la partida sin salida. Daño, cargador y disipador se devuelven nivel a nivel (el último primero) por lo que costaron, en cualquier momento; la oleada en curso sigue con lo que empezó. La sintaxis no (el código que la usa dejaría de compilar) ni la torreta extra (se perdería su código y su pestaña). No cambia el guardado: los niveles ya se guardan. |
| D-60 | Fase 8: for y el inicio del acto 2 | PROVISIONAL (las piezas las pidió Yeray; los números son míos) | La pieza se llama «Recorrer (for)» y trae las dos formas. `enemigos()` da los enemigos a tiro en el orden en que salieron. De `List` solo `size()`, `get(i)` e `isEmpty()`; sin arrays. Corredor a 300 u/s (plan) con vida ×0,5 y 8/10/12 en las oleadas 11–13, para que elegir a quién disparar importe. for a 4 estrellas; memoria y procesador esperan a for. Huecos 3 y 4 en (380, 430) y (850, 190). El juego acaba por ahora en la oleada 13. El mapa enseña un acto cada vez. |
| D-61 | Fase 9: métodos y el Mutante | CERRADA la mecánica del Mutante y la recursión (Yeray); números PROVISIONAL | Los métodos van en una biblioteca compartida que no gasta memoria (plan), con parámetros, `return` y recursión hasta 64 llamadas (`StackOverflowError`, como Java). Sin sobrecargas, modificadores ni variables fuera de un método. El Mutante es débil a NORMAL o a PERFORANTE según la oleada (lo dice el parte) y la otra munición le hace un 80 % menos; `e.mutante()` lo reconoce. Oleadas 14–16; métodos a 6 estrellas. El juego acaba por ahora en la oleada 16. Guardado versión 3. |
| D-62 | Depurador (fase 9b) | PROVISIONAL (lo pidió Yeray en la lista de ideas; la forma es mía) | Puntos de parada con un clic en el número de línea, en el código de cada torreta y en la biblioteca; no se guardan. Cuando una torreta llega a uno, la oleada se pausa al acabar ese tick y se graba su ciclo entero (hasta 4.096 pasos): se recorre hacia atrás y hacia delante con las variables de cada paso, sin volver a ejecutar. «Continuar», «Siguiente ciclo» y cerrar la ventana siguen la oleada. Depurar no cambia la partida. |
| D-63 | Carrera: récords, racha, logros, sombreros y nombres (fase 9b) | PROVISIONAL (no viene del plan) | Nada de esto da ni cuesta estrellas o trofeos (D-57 no cambia). Récords por oleada ganada: más rápida, menos disparos, menos memoria. Racha: oleadas ganadas seguidas, cada una más alta que la anterior; cada una suma su número × la longitud de la racha; repetir una oleada empieza otra y perder la corta. 16 logros en `career.json` (no vienen del plan), 8 con sombrero; el último, «Coleccionista», pide todos los demás. Nombre de torreta de hasta 14 letras, números, espacios y `- _ .`. Guardado versión 4. |
| D-64 | Partida experta (fase 9b) | PROVISIONAL (no viene del plan) | Opción de «Nueva partida» en Ajustes para quien ya sabe programar: empieza con toda la sintaxis del MVP; estrellas, hardware y oleadas como siempre. La partida normal no cambia. |
| D-65 | «Probar» (fase 9b) | PROVISIONAL (no viene del plan) | En el parte, juega la oleada al momento con el código actual y recargando a mano en cuanto hace falta, y enseña su informe. No paga, no cuenta récords ni racha y no cambia la semilla de las batallas de verdad; cada prueba usa la suya, así que es una muestra, no una predicción. |
| D-66 | Autocompletado (fase 9b) | PROVISIONAL | Solo ofrece lo que el jugador ya tiene (piezas compradas, llamadas desbloqueadas) y lo que su código declara; como mucho 8; primero lo que empieza igual. No sugiere sintaxis sin comprar para no enseñarla antes de tiempo. |
| D-67 | Ventanas (fase 9b) | PROVISIONAL (amplía D-52) | Código separado, páginas del manual, salida y depurador en ventanas flotantes, varias a la vez. Tienda, mapa, informe, logros y ajustes siguen en el panel de la derecha, uno cada vez. |
| D-68 | `imprimir`, `saludar` y `oleada` (fase 9b) | PROVISIONAL (no vienen del plan) | `imprimir(x)` y `saludar()` gratis desde el principio; `imprimir` no gasta memoria. `oleada()` llega con Variables (para guardarla). **No** hay `comprar()` ni `estrellas()`: comprar desde el código dejaría que un bucle gastara las estrellas sin pensar y se saltaría las decisiones de la tienda, que son parte del diseño (D-57). |
| D-54 | Manual del jugador (fase 5b) | CERRADA la idea (Yeray, no viene del plan); contenido PROVISIONAL | Un libro dentro del juego con cada llamada, pieza de sintaxis, excepción y enemigo, ejemplos que se copian con un botón y recetas. Todo visible desde el principio; lo no desbloqueado, con candado y qué lo trae, como el árbol de la tienda. Textos y ejemplos escritos por mí; los ejemplos se compilan al cargar y los números salen de los datos. |
| D-44 | Semilla de cada batalla | PROVISIONAL | Un contador de batallas de la partida: las mismas acciones repiten la misma partida. Hoy solo la usa el objetivo «al azar», que no está activo. |
| D-45 | Medida de la decisión C | PROVISIONAL | En la oleada 1, segundos reales desde que una torreta se queda sin balas hasta el clic que la recarga, más un resumen al acabar la oleada. Se escribe en la consola (`Gdx.app.log`, etiqueta `wave-defense`); el log a fichero llega en la fase 6. |
| D-46 | Escala de la UI | PROVISIONAL | `ScreenViewport` a 1 píxel por unidad y fuentes a tamaño fijo (18 y 14 px): nítidas a 1280×720. La escala de UI configurable va con los ajustes (fase 6). |
| D-39 | Variables y la regla del doble | CERRADA (Yeray) | **Excepción documentada** a la regla del doble: Variables rinde 1,07 veces lo mismo en hardware. Motivo: es compra obligatoria para llegar a `&&`, así que no compite con el hardware. `docs/BALANCE.md` la sigue listando entre las contradicciones porque el informe da datos, no decisiones. Se revisa con el playtest (`docs/PROGRESS.md`, «A observar en el playtest»). |
| D-37 | Números de enemigos de la revisión | PROVISIONAL | Regenerador vida ×2 y cura 50 %/s; Blindado vida ×2; Enjambre 200 u/s (menos tiempo en rango). Ninguno es del plan. |

Decisiones de stack también cerradas: Kotlin DSL sí; LWJGL el de libGDX
1.14.2 sin sobrescribir.
