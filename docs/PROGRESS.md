# Progreso

Estado actual y lo pendiente. La historia con problemas, soluciones y
capturas está en [`DIARIO.md`](DIARIO.md).

## Fases

| Fase | Estado | Rama |
|---|---|---|
| 0. Propuesta: árbol, decisiones y dudas | ✅ Fusionada | `phase-0-propuesta` |
| 1. Esqueleto Gradle, lanzador y CI | ✅ Fusionada | `phase-1-esqueleto` |
| 2. Intérprete con tests | ✅ Fusionada | `phase-2-interprete` |
| 3. core-sim y sim-cli, `docs/BALANCE.md` | ✅ Fusionada | `phase-3-simulacion` |
| 3b. Revisión de balance (decisiones A y B) | ✅ Fusionada | `phase-3b-rebalance` |
| 3c. Barrido del Regenerador | ✅ Fusionada | `phase-3c-regenerador` |
| 3d. Camino a un render 3D (D-40) | ✅ Fusionada | `phase-3d-render-3d` |
| 4. Juego: camino, torretas, enemigos, bucle | ✅ Fusionada | `phase-4-juego` |
| 5. Editor, tienda, parte de oleada, informe, mapa | ✅ Fusionada | `phase-5-interfaz` |
| 5b. Manual del jugador | ✅ Fusionada | `phase-5b-manual` |
| 6. Guardado, ajustes, robustez | ✅ Fusionada | `phase-6-guardado` |
| 6b. Manual para principiantes y nombres | ✅ Fusionada | `phase-6b-manual-sencillo` |
| 6c. Economía de estrellas y retos (D-57) | ✅ Fusionada | `phase-6c-estrellas` |
| 7. Empaquetado y documentación | ✅ Hecha, en PR | `phase-7-empaquetado` |

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
el área de 1280×720 con el color de fondo durante 20 s sin errores.

Probado en local por Yeray (Windows 11, GPU real): `gradlew.bat :lwjgl3:run`
abre la ventana con el título «Defensa por oleadas» y el fondo, se cierra con
la X sin errores y arranca desde IntelliJ IDEA.

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

## Revisión de balance (hecha)

Encargo de Yeray: A) intentar la regla del doble cambiando solo números y
reglas provisionales, con como mucho dos tandas de ajuste y con topes de
hardware como red de seguridad; B) 10 vidas en el núcleo.

- **Objetivo por defecto** configurable en datos (`defaultTarget`). Medido:
  «más cercano al núcleo» y «al azar» no hacen que el Regenerador castigue a
  quien no tiene Variables con la vida original; lo que limita es la cadencia,
  no la cura. Se queda «a la torreta» (el plan). Ver D-36.
- **`&&`**: el script de referencia sin `&&` ya usaba `if` anidados, que
  toman la misma decisión. Scripts nuevos que necesitan `||` de verdad dentro
  de 12 líneas (`L3-ammo-b`: recarga si está vacía **o** no hay enemigos) y
  variantes `-b`/`-c` en todos los niveles; la regla del doble prueba todas.
- **Enemigos**: Regenerador vida ×2 y cura 50 %/s, Blindado vida ×2,
  Enjambre 200 u/s (D-37).
- **Topes de hardware** según la sintaxis (D-34), en `shop.json`, con el
  mensaje de la tienda («Necesitas variables para seguir mejorando el daño»).
- **10 vidas** en el núcleo (D-35).
- 298 tests en verde.

### Resultado (detalle en `docs/BALANCE.md`)

Regla del doble, proporción pieza / mismo dinero en hardware (≥ 2 cumple):

| Pieza | Antes (fase 3) | Ahora, con topes | Ahora, sin topes |
|---|---|---|---|
| if | 1,07 | 1,33 | 1,07 |
| Variables | 1,00 | 1,07 | 0,80 |
| `&& \|\| !` | 0,40 | 0,83 | 0,75 |
| while | 0,75 | 1,67 | 1,46 |

- **Ninguna pieza llega a ×2**; los topes se quedan activos, como pidió
  Yeray. No se ha seguido iterando.
- Con 10 vidas: Jefe 1 derrotado en **25 oleadas jugadas** y **16,1 min**.
  Muros en las oleadas 6 (3 pasadas), 7 (1) y 9 (4): cumple la regla de 5.

## Barrido del Regenerador (hecho)

Encargo de Yeray: con el objetivo por defecto del plan (el más cercano a la
torreta, D-36 cerrada), buscar espera, curación, vida y separación al salir
del Regenerador con las que Variables llegue a ×2, sin pasar de 5 pasadas en
las oleadas 4–5 y sin cambiar las oleadas 1–3. Si ninguna llega, no aplicar
nada.

- Nuevo `spawnGapFactor` por tipo en `enemies.json` (1 en todos: sin efecto)
  para poder mover la separación solo del Regenerador. La ventana de salida
  global habría cambiado también las oleadas 1–3.
- `sim-cli --sweep-regenerator` y la tabla completa (209 combinaciones) en
  `docs/BARRIDO-REGENERADOR.md`.
- **Ninguna combinación llega a ×2**; no se ha aplicado ninguna. La más
  cercana: vida ×1 (como el Básico), espera 1 s, cualquier curación,
  separación 1 → **1,20** con topes (1,00 sin topes), frente a 1,07 ahora.
  Ninguna cambia las oleadas 1–3; solo 3 de 209 ponen muro en la oleada 5,
  con 2 pasadas como mucho.
- **Decisión de Yeray**: no se aplica nada. Variables queda como excepción
  documentada a la regla del doble (1,07; D-39): es compra obligatoria para
  llegar a `&&`, así que no compite con el hardware. El factor de separación
  se queda en 1 (D-38). No más barridos sobre esto: se decide con el
  playtest (ver «A observar en el playtest»).
- Por qué no llega: con «el más cercano a la torreta», la torreta sin Variables ya
  sigue disparando al mismo enemigo mientras se acerca, así que la cura casi
  nunca llega a empezar. Espera y curación apenas mueven nada; la vida escala
  a los dos lados por igual; juntar a los regeneradores al salir perjudica
  también al script con Variables (baja a 0,80).

## Fase 4 (hecha)

Se juega: `gradlew.bat :lwjgl3:run` abre la pantalla de combate.

- `core-sim`: búfer de sucesos y posición anterior de enemigos y balas (D-40),
  y `WaveLoop`, el bucle de oleadas con el flujo D-01 (espera a «Desplegar»
  en oleadas nuevas; encadena repeticiones y oleadas ya superadas).
- `game`:
  - `FixedStepClock`: ticks enteros, ×3 = más ticks por frame.
  - `WorldRenderer` y su versión 2D con `ShapeRenderer`: camino, núcleo,
    torretas con su alcance y barras de munición y calor, enemigos por tipo
    con vida y anillo de blindaje, balas normales y perforantes, y efectos
    (fogonazo, impacto, baja, fuga, excepción, recarga). Interpola entre
    ticks, tiene la cámara, dice qué torreta hay bajo el puntero y proyecta
    puntos del mundo a pantalla.
  - HUD en scene2d (D-41): oleada, núcleo, dinero, selector ×1/×2/×3 (D-43, D-47), avance
    automático, «Desplegar oleada N», resultado de la última oleada y, encima
    de cada torreta, la excepción con su línea, «Recargando…» o «¡Sin
    balas!».
  - Recarga a mano con clic en la torreta (D-42).
  - Decisión C: el juego escribe en la consola cuánto tardas en recargar a
    mano en la oleada 1 y un resumen al acabarla (D-45).
  - Fuente JetBrains Mono (OFL) con freetype; textos en
    `messages.properties`.
- 320 tests en verde. Nuevos: reloj, búfer de sucesos (orden, capacidad, que
  leerlo no cambia la batalla), posiciones anteriores, `WaveLoop` (flujo
  D-01, derrota, repetición idéntica), selección y proyección a varios
  tamaños de ventana, la pantalla convirtiendo un clic en recarga, textos en
  español y la medida de la decisión C. El juego entero arranca y pinta en el
  backend headless.

Comprobado aquí con Xvfb y GL por software, con capturas: la pantalla se ve
como se describe arriba, «Desplegar» arranca la oleada, la excepción sale
encima de la torreta, el clic recarga, la oleada 1 se supera y paga, la 2
espera a «Desplegar», y la consola muestra la línea de la decisión C. Eso no
sustituye la prueba en tu Windows (lista de abajo).

**Dato para la decisión C**: con `disparar();`, la torreta vacía el cargador
disparando al aire (D-05) antes de que entre el primer enemigo, y se cuelga
con SinMunicionException. Si el jugador recarga pronto, vuelve a vaciarlo al
aire. La primera medida de la oleada 1 incluye esa espera, así que conviene
mirar el resumen entero, no solo la primera línea.

**Probado en local por Yeray** (Windows 11, GPU real): `gradlew.bat
:lwjgl3:run` funciona. Se ve el campo y el HUD, «Desplegar» arranca la
oleada, la excepción sale encima de la torreta, el clic recarga, se ven los
efectos, la oleada 1 se supera y la consola da la medida de la decisión C:
3 recargas a mano en 1,43 s, 1,12 s y 0,12 s (media 0,89 s; sin la tercera,
que parece anticipada, 1,28 s). El simulador supone 1 s: se sostiene.
D-05 queda cerrada: los disparos al aire molestan, y es lo que se busca,
porque empujan a programar «dispara solo si hay enemigos».

## Fase 5 (hecha)

El bucle del plan ya está entero: parte de oleada → editar y comprar →
desplegar → informe → avanzar o repetir.

- `core-sim`: `WaveLoop.setScript` (edición en caliente: el código que
  compila entra en la siguiente oleada), `selectWave` (mapa, D-50) y
  `purchased` (rehace la oleada que espera tras una compra). Ejemplos de las
  tarjetas de la tienda en `shop.json`, validados al cargar (D-53).
- `game`:
  - **Editor** (`EditorDocument` + `CodeEditor`): pestaña por torreta,
    números de línea, colores con el lexer del juego, barra de memoria por
    instrucción, errores en rojo con su mensaje, cursor, selección,
    portapapeles, deshacer y sangría automática. Compila al escribir (D-51).
  - **Tienda**: árbol entero con precios, lo que te quita de encima cada
    pieza, por qué no se puede comprar algo, hardware con nivel y efecto,
    tarjeta con ejemplo al comprar.
  - **Parte de oleada**: enemigos por tipo, cuántos y qué los hace
    especiales; botón «Desplegar».
  - **Informe**: dinero desglosado, fugas por tipo y por tramo del camino,
    y por torreta disparos, recargas, segundos colgada y excepciones con su
    línea. Se abre solo al acabar la oleada (D-52).
  - **Mapa de oleadas** (D-49): candados, bandera roja en la actual, jefe
    destacado; elegir una alcanzada la deja esperando a «Desplegar» (D-50).
- Tests nuevos: editor (escritura, sangría, selección, deshacer), colores,
  textos del intérprete, flujo del editor con `WaveLoop`, compra con tarjeta,
  candados del mapa, parte, informe y la pantalla abriendo el informe sola.

Comprobado aquí con Xvfb y capturas: parte de oleada, editor escribiendo un
`if` (sangría, colores, «Aún no has desbloqueado if/else», memoria 3/12),
tienda, mapa con candados y bandera, y el informe al acabar la oleada 1.

## Fase 5b: manual del jugador (hecha)

Pedido por Yeray (no viene del plan, D-54): un libro dentro del juego con
todo lo que puede necesitar el jugador y código que se pueda copiar.

- Botón **Manual** en la barra. Índice con 8 capítulos: primeros pasos (el
  ciclo, el editor, la memoria, cómo se juega, la tienda), lo que hace la
  torreta (una página por llamada), lo que se le pregunta a un enemigo,
  sintaxis (lo básico, if/else, variables, `&& || !`, while), excepciones
  (qué las provoca y cómo evitarlas), errores al escribir, enemigos y recetas
  completas para copiar.
- Lo que aún no tienes sale con candado y dice qué lo trae: «llega con
  if/else, en la Tienda», «llega en la oleada 6», «aparece en la oleada 8».
- Cada ejemplo con los colores del editor, la memoria que ocupa y un botón
  «Copiar»; se pega en el editor con Ctrl+V.
- `data/manual.json` (estructura, desbloqueos y código) validado al cargar:
  una página por cada llamada, pieza del MVP, enemigo y excepción, y cada
  ejemplo compila con lo que pide su página. Textos en `manual.*`; los
  números salen de los datos por nombre (`ManualFacts`).
- La página de SinMunicionException explica lo que le pasó a Yeray: cada
  torreta tiene su código y el código nuevo entra en la siguiente oleada.
- Tests: rechazos del manual en `GameDataTest`, textos completos y sin números
  inventados (`ManualTextsTest`), índice, candados, copiar, anterior y
  siguiente, todas las páginas abren y su código cabe en el panel.

## Fase 6: guardado, ajustes y robustez (hecha)

Lo decidido por Yeray antes de empezar: una sola partida, guardado
automático, se guardan el borrador y la versión buena del código, y los
ajustes son pantalla completa, tamaño de la interfaz, tamaño del código y
nueva partida (D-55, D-56).

- **Guardado** (`save.json` en la carpeta de usuario, compatible con Steam
  Auto-Cloud):
  - Escritura atómica con copia `.bak` (`AtomicFileStore`, en `platform`).
  - Formato con versión y migraciones (`SaveGame`, `SaveCodec`). Se guardan
    el progreso, el código de cada torreta, los borradores del editor y la
    semilla.
  - Al cargar se comprueba todo contra los datos. Un script que ya no
    compila vuelve al inicial, pero su borrador se conserva.
  - Si el fichero está roto, se usa la copia; si también lo está, partida
    nueva. En los dos casos sale un aviso y el fichero roto se guarda aparte.
  - Se guarda solo: al acabar cada oleada, al comprar, al cambiar el código
    (como mucho una vez por segundo mientras escribes), al elegir oleada en el
    mapa, al cambiar el avance automático y al cerrar. La oleada en curso no
    se guarda: al volver espera a «Desplegar».
- **Ajustes** (`settings.json`, botón Ajustes):
  - Pantalla completa.
  - Tamaño de la interfaz (100–200 %): crece solo hasta donde cabe en la
    ventana, para no romper la disposición.
  - Tamaño del código del editor y del manual (12–22 px).
  - Nueva partida, con confirmación.
  - Los tamaños se aplican al momento sin perder el código. El panel dice
    dónde está guardada la partida.
- **Robustez**:
  - Log rotativo en `logs/` (5 × 1 MB); `Gdx.app.log` también va ahí.
  - Las excepciones sin capturar se registran.
  - Un frame que falla se registra y avisa, y el juego sigue.
- Tests: escritura atómica (copia, fichero roto, los dos rotos, `.tmp`
  sobrante), formato (ida y vuelta, migraciones, versión nueva, campos de
  más, reglas rotas), `WaveLoop` (borradores y revisión), almacén, guardado
  automático, ajustes, panel de ajustes, y el juego entero arrancando con
  una partida rota y cambiando tamaños.

Comprobado aquí con Xvfb y capturas: el panel de ajustes, el código del
editor a 20 px conservando lo escrito, la interfaz al 150 % y la partida
recuperada al volver a abrir el juego.

## Fase 6b: manual para principiantes y nombres (hecha)

Pedido por Yeray: que el manual lo entienda alguien que nunca ha programado y
que las piezas tengan nombres que se entiendan sin saber programación (D-58).

- Piezas: «Decidir (if/else)», «Recordar (variables)», «Combinar
  condiciones (&&, ||, !)» y «Repetir (while)». Los mensajes las ponen entre
  comillas: «Aún no has desbloqueado «Repetir (while)»».
- Manual reescrito (47 páginas en 9 capítulos):
  - Capítulo nuevo «Empieza aquí», con «Qué es programar» y «Tus primeros
    pasos».
  - Capítulo nuevo «Símbolos y palabras»: cada símbolo (incluido cómo
    escribir || en un teclado español), un glosario y cómo leer el código en
    voz alta.
  - Títulos que dicen qué hace cada orden («disparar() · dispara una
    bala») y ejemplos con comentarios.
- La tienda ya no vuelve arriba al comprar (arreglado en el PR #12).
- Decidido para la fase siguiente (D-57): economía de estrellas sin farmeo y
  moneda de jefes.

## Fase 6c: economía de estrellas y retos (hecha)

Pedido por Yeray (D-57): que no se pueda farmear y que avanzar pida
programar mejor.

- Cada oleada da 3 estrellas, cada una **una sola vez**: superarla,
  superarla sin fugas y cumplir su **reto de código**. Repetir no paga.
- Un reto fijo por oleada en `waves.json`: sin disparos al aire, sin recargar
  a mano, memoria máxima, ninguna bala a enemigos ya derribados, disparos
  máximos o ninguna excepción. Están escogidos para que el mejor script de
  referencia **no** los cumpla (salvo el del jefe): hay que cambiar el código.
- Los jefes dan un **trofeo** la primera vez. En el MVP no se gastan todavía:
  la tienda lo explica.
- Precios en estrellas (provisionales, calibrados con `sim-cli`): sintaxis
  1, 2, 3 y 4; cada nivel de hardware 1; torreta extra 2. Todo el MVP cuesta
  24 de las 30 estrellas.
- Guardado versión 2: una partida antigua cambia su dinero por las estrellas
  que habrían dado sus oleadas superadas y sin fugas.
- Juego: HUD «Estrellas · Trofeos», parte de oleada con las 3 estrellas y el
  reto, informe con lo ganado y, si el reto falla, cuánto se hizo, tienda en
  estrellas con la sección de trofeos, mapa con ●●○ bajo cada oleada y una
  página nueva del manual, «Estrellas, retos y trofeos».
- `sim-cli` rehecho: el jugador de referencia compra lo que más le ayuda,
  repite oleadas por su reto y, atascado, vuelve a por estrellas que le
  faltan. Avisa si una partida queda **bloqueada**. Mide también una partida
  sin hacer ningún reto.
- **Devolver mejoras** (D-59, decidido por Yeray tras ver el problema): daño,
  cargador y disipador se devuelven nivel a nivel por lo que costaron. Así
  una mala compra no deja la partida sin salida. La sintaxis y la torreta
  extra no se devuelven. El simulador, antes de dar una partida por
  bloqueada, prueba a cambiar una mejora por otra.

Resultado del simulador (`docs/BALANCE.md`):

- El jugador de referencia vence al jefe en 15 oleadas jugadas, sin muros,
  gastando 20 de las 27 estrellas que dan las oleadas 1–9: le sobran 7.
- Sin hacer ningún reto se queda **bloqueado en la oleada 7** con 13
  estrellas: los retos son necesarios, como pedía Yeray.
- 8,4 min de juego hasta el jefe sin contar el tiempo de escribir (antes,
  16 min con farmeo). El resto lo pone pensar los retos; hay que medirlo.
- La sintaxis llega antes que en el plan («Llega hacia la oleada»): if y
  Variables tras la oleada 1, `&&` tras la 2 y while tras la 4. Es el precio
  de que el jefe se pueda alcanzar con estrellas que no se repiten.
- Regla del doble: las 4 piezas siguen sin cumplirla, como antes. La de
  while sale peor (0,20) porque ahora se compra en la oleada 4 y se mide en
  la 8.

## Fase 7: empaquetado y documentación (hecha)

- Construo 2.2.2 en `lwjgl3`: un zip por sistema con el juego y su propio Java
  21 (Temurin recortado con jlink, unos 45 MB). Se genera desde cualquier
  sistema: `./gradlew :lwjgl3:packageWinX64` (y `LinuxX64`, `MacM1`, `MacX64`).
- Workflow `Package` en GitHub Actions, solo a mano o con una etiqueta `v*`:
  deja los cuatro zips para descargar 14 días.
- `docs/STEAM.md`: cómo publicar en Steam (depots, Auto-Cloud por sistema,
  steamworks4j a través de `PlatformServices`, SteamPipe y lo que falta antes
  de vender: firmas, icono, Steam Deck).
- README con empaquetar, jugar el zip y regenerar el balance.

Comprobado aquí: el zip de Linux, descomprimido en una carpeta limpia, arranca
con su propio Java, guarda la partida y juega una oleada. Los de Windows y
macOS se generan bien, pero no se pueden abrir en este contenedor.

## Siguiente

El MVP está completo. Lo siguiente lo decide Yeray tras jugar el zip de
Windows: ajustes de balance con lo que salga del playtest, o el acto 2.

## Decisiones por revisar

Propuestas mías aplicadas como PROVISIONAL (detalle en
`docs/ARCHITECTURE.md`, sección 12). Ninguna contradice el plan.

- **D-01b**: con avance automático encendido, una oleada ya superada se
  encadena sin pausa.
- **D-02**: avance automático encendido en partida nueva.
- **D-03b (cambiada en la fase 3)**: la recarga a mano se puede hacer también
  con la torreta colgada. Prohibirla dejaba la oleada 1 sin salida (el
  cuelgue dura justo 6 ciclos y la torreta vuelve a colgarse al despertar).
- **D-09**: perforante solo atraviesa blindaje.
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
- **D-30**: el jefe empieza sin blindaje.
- **D-31**: `hayEnemigos()` y `enemigoMasCercano()` solo ven enemigos en rango.
- **D-32**: orden de aparición de `waves.json`, huecos repartidos en 20 s.
- **D-33**: jugador de referencia del simulador (política de compra, 1 s de
  reacción). Solo afecta al informe.
- **D-34**: los topes concretos de hardware (la idea la aprobó Yeray).
- **D-37**: Regenerador vida ×2 y cura 50 %/s; Blindado vida ×2; Enjambre
  200 u/s.
- **D-41**: hasta la fase 5, «Desplegar oleada N» hace de parte, una línea de
  resultado hace de informe y cada torreta usa su script inicial (superada:
  ya hay parte, informe, editor y guardado).
- **D-42**: recarga a mano con clic izquierdo en la torreta; también con el
  cargador lleno, como D-03.
- **D-43**: el botón ×3 recuerda lo pedido; solo se aplica en oleadas
  superadas sin fugas.
- **D-44**: la semilla de cada batalla es un contador de la partida.
- **D-45**: la medida de la decisión C sale en el log (`logs/game-0.log`).
- **D-46**: fuentes a 18 y 14 px a 100 %; la escala la da ahora el ajuste de
  tamaño de la interfaz (D-56).
- **D-47**: ×2 sigue la misma regla que ×3 (solo en oleadas superadas sin
  fugas). La forma del selector la pidió Yeray.
- **D-48**: el juego avanza y se dibuja mientras se mueve o redimensiona la
  ventana, con cada aviso de GLFW; con el ratón quieto espera.
- **D-50**: elegir oleada en el mapa abandona la que está en curso sin cobrar
  ni contar como derrota; la elegida espera a «Desplegar».
- **D-51**: el código con errores se queda en el editor; la torreta sigue con
  su última versión buena.
- **D-52**: paneles a la derecha, uno a la vez, sin parar la oleada; el
  informe se abre solo si no hay otro panel abierto.
- **D-53**: los ejemplos de las tarjetas de la tienda los he escrito yo.
- **D-54**: el contenido del manual (textos, ejemplos y recetas) lo he
  escrito yo; se revisa leyéndolo en el juego.
- **D-55**: guardado automático (como mucho una vez por segundo mientras se
  escribe); la oleada en curso no se guarda.
- **D-57**: los números de la economía de estrellas: precios (sintaxis 1–4,
  hardware 1 por nivel, torreta extra 2), los 10 retos y 0 estrellas al
  empezar. Calibrados con `sim-cli`.
- **D-56**: tamaños de interfaz 100–200 % (limitados a lo que cabe en la
  ventana) y de código 12–22 px.

Números de balance que no vienen del plan (D-12): están en la lista
`notFromPlan` de cada JSON y en la sección 2 de `docs/BALANCE.md`.

## A observar en el playtest

Preguntas de diseño que solo responde alguien jugando. Se apuntan aquí y se
deciden con ese dato, no antes.

- [ ] **Variables contra el Regenerador** (D-39): ¿nota el jugador la
  diferencia al comprar Variables en las oleadas 4–5? Si no la nota, se
  rediseña el Regenerador con ese dato. El simulador da 1,07 frente al
  hardware del mismo precio (1,69 frente a no tenerla).
- [x] **Recarga a mano en la oleada 1** (decisión C): primera partida de
  Yeray, media 0,89 s (1,43 / 1,12 / 0,12 s). El simulador supone 1 s; se
  mantiene. Conviene repetir la medida con más partidas.
- [x] **Disparos al aire en la oleada 1** (D-05): molestan, y Yeray lo quiere
  así: empujan a programar «dispara solo si hay enemigos». D-05 cerrada.

## Pendiente de probar en local

Releases:

- [ ] Tras publicar la `v0.1.0`: la portada muestra la Release con los
  cuatro zips y las instrucciones; el de Windows se descarga y se juega.

Fase 7 (Windows, sin usar Gradle):

- [ ] Doble clic en `jugar.bat` (en la carpeta del repo): baja lo último y
  abre el juego. Probar también sin internet: avisa y juega igual.

- [ ] En GitHub: *Actions* → *Package* → *Run workflow*; al acabar, bajar
  `java-wave-defense-windows` de *Artifacts*.
- [ ] Descomprimir (dentro hay otro zip; descomprimirlo también) y abrir
  `java-wave-defense.exe`: arranca sin tener Java instalado.
- [ ] La partida de antes sigue ahí (`%APPDATA%\JavaWaveDefense`), porque el
  zip usa la misma carpeta que `gradlew.bat :lwjgl3:run`.
- [ ] SmartScreen puede avisar («Windows protegió su PC») porque el `.exe` no
  está firmado: «Más información» → «Ejecutar de todas formas».

Fase 6c (`gradlew.bat :lwjgl3:run`):

- [ ] Abrir con la partida de antes: el dinero se ha cambiado por estrellas
  (una por oleada superada y otra por cada sin fugas) y no se pierde nada.
- [ ] El parte de oleada enseña las 3 estrellas y el reto; el informe dice
  qué estrellas son nuevas y, si el reto falla, cuánto has hecho.
- [ ] Cumplir algún reto cambiando el código (por ejemplo, el de la oleada 3:
  código de 4 líneas de memoria como mucho).
- [ ] Repetir una oleada con sus 3 estrellas no da nada.
- [ ] Comprar una mejora y devolverla con «Devolver»: vuelven las estrellas
  y el nivel baja; la oleada en curso no cambia.
- [ ] Tienda en estrellas, con el botón debajo; el mapa con ●●○.
- [ ] ¿Cuánto se tarda de verdad hasta el jefe? ¿Algún reto es injusto o
  demasiado fácil?

Arreglo de los botones desplazados:

- [ ] Con la ventana maximizada o estirada a otra forma, cada botón responde
  justo donde se ve (Código, Tienda, Mapa…, la velocidad y los de los
  paneles).

Fase 6b:

- [ ] Leer el manual como alguien que no ha programado nunca: ¿se entiende
  «Empieza aquí» y «Símbolos y palabras»? ¿Falta algo?
- [ ] Los nombres nuevos de las piezas en la Tienda, en el manual y en los
  errores del editor se leen bien.

Fase 6 (`gradlew.bat :lwjgl3:run`):

- [ ] Jugar un poco, comprar, escribir código, cerrar con la X y volver a
  abrir: todo sigue igual y la oleada espera a «Desplegar».
- [ ] La partida está en `%APPDATA%\JavaWaveDefense\save.json` (y
  `save.json.bak`, `settings.json` y `logs\game-0.log`).
- [ ] Ajustes: pantalla completa entra y sale bien; tamaño de la interfaz y
  del código se aplican al momento sin perder el código; en pantalla
  completa el 150 % se ve nítido.
- [ ] Nueva partida pide confirmación y empieza de cero sin tocar los ajustes.
- [ ] Romper `save.json` a mano (borrar la mitad) y abrir: sale el aviso de
  copia recuperada.

Fase 5b (`gradlew.bat :lwjgl3:run`):

- [ ] El botón Manual abre el índice; las páginas que no tienes llevan
  candado y al comprar if/else se quitan las suyas.
- [ ] Una página se lee bien: título, texto, ejemplos con colores y memoria;
  anterior, siguiente e índice funcionan y vuelve arriba al cambiar.
- [ ] «Copiar» cambia a «¡Copiado!» y Ctrl+V en el editor pega el ejemplo.
- [ ] Leer el manual entero y decir qué falta, sobra o no se entiende.

Fase 5 (`gradlew.bat :lwjgl3:run`):

- [ ] Parte de oleada en el centro con los enemigos y «Desplegar».
- [ ] Editor: escribir, borrar, flechas (también mantenidas), Inicio/Fin,
  selección con Mayús y con el ratón, Ctrl+A/C/X/V, Ctrl+Z/Y, Tab, Intro con
  sangría, `}` que vuelve atrás. Pegar código desde fuera del juego.
- [ ] Al escribir algo no comprado sale el error en rojo con su línea; al
  arreglarlo pone «Compila» y la torreta usa el código nuevo en la siguiente
  oleada.
- [ ] Tienda: comprar if/else, ver la tarjeta con el ejemplo,
  copiarlo en el editor y ver que la torreta deja de disparar al aire.
- [ ] Comprar la torreta extra: aparece la segunda torreta y su pestaña en el
  editor.
- [ ] Informe al acabar cada oleada; al perder, dice por dónde se colaron.
- [ ] Mapa: candados, bandera roja, clic en una oleada superada para jugarla.
- [ ] Los paneles no tapan nada importante a 1280×720 ni con la ventana más
  grande.

Fase 4 (`gradlew.bat :lwjgl3:run`, Windows con GPU real):

- [x] Se ven el campo y el HUD, «Desplegar» arranca la oleada, la excepción
  sale encima de la torreta, el clic recarga, se ven los efectos, la oleada 1
  se supera y la consola da la medida de la decisión C (Yeray, Windows 11).
- [x] Al perder una oleada se vuelve a la anterior (Yeray).
- [x] Cambiar el tamaño de la ventana mantiene el mapa y el juego va bien
  (Yeray). Pedido: que no se congele al mover o redimensionar → D-48.
- [x] Selector de velocidad (D-47): con el ratón encima se abre en ×1, ×2 y
  ×3; un clic cambia la velocidad; ×2 y ×3 solo se pueden elegir en oleadas
  superadas sin fugas.
- [x] Mover la ventana o cambiar su tamaño con una oleada en marcha: los
  enemigos siguen moviéndose mientras arrastras (D-48).
- [ ] 60 fps estables sin tirones (también a ×3).

Anteriores:

- [ ] (Si hay un Mac a mano) el `StartupHelper` relanza con
  `-XstartOnFirstThread` y la ventana abre.
