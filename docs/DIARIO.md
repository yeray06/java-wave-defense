# Diario del proyecto

Cómo ha ido avanzando el juego, fase a fase: qué se hizo, qué problemas
surgieron y cómo se resolvieron. Las capturas están en
[`docs/capturas/`](capturas/). El estado actual está en
[`PROGRESS.md`](PROGRESS.md) y el porqué de cada decisión en
[`ARCHITECTURE.md`](ARCHITECTURE.md) §12.

Las capturas las hago yo en un servidor sin pantalla (Xvfb, con GL por
software), así que se ven algo distintas que en Windows. No sustituyen las
pruebas en local.

---

## Fase 0 · 2 de octubre · Propuesta (PR #1)

- Leído el plan entero y escrita la propuesta de arquitectura: stack,
  módulos, intérprete propio, simulación determinista, guardado y CI.
- Decisiones D-01 a D-22, cada una con su motivo.

**Problemas**: ninguno técnico. Hubo puntos del plan ambiguos (qué cuenta como
línea de memoria, qué hace `recargar()` a mitad de script, el flujo entre
oleadas…), y Yeray los decidió antes de programar (D-01 a D-06).

## Fase 1 · 2 de octubre · Esqueleto (PR #2)

- Gradle con los seis módulos, JDK 21, Spotless, CI en Ubuntu y Windows.
- El lanzador abre una ventana de 1280×720 vacía.

![Primera ventana](capturas/fase1-01-primera-ventana.png)

| Problema | Solución |
|---|---|
| google-java-format 1.37.0 no funciona con Spotless 8.10.3. | Se usa la versión que fija Spotless. |
| El backend sin pantalla de libGDX 1.14.2 ya no trae un GL simulado, y los tests del juego fallaban. | El test instala un GL vacío propio. |
| CI se ejecutaba dos veces por commit (push y PR) y gasta minutos. | Push solo en `main`; las ramas se prueban con su PR. |

Probado por Yeray en Windows 11: abre, se cierra con la X y arranca desde
IntelliJ.

## Fase 2 · 2–5 de octubre · Intérprete (PR #3)

- Intérprete propio de Java: lexer → parser → árbol → comprobador → árbol
  ejecutable. Nada de `javac` ni reflexión: el código del jugador no puede
  tocar nada fuera de la torreta ni colgar el juego.
- `if/else`, variables, `&& || !`, `while` y las 13 llamadas de la torreta.
  `for`, métodos y clases dicen «Aún no has desbloqueado …».
- 248 tests, comparando con el `javac` real: lo que el juego acepta, Java
  también.

| Problema | Solución |
|---|---|
| Un nodo nulo en scripts con errores (lo encontró un test con entradas aleatorias). | El árbol solo se construye si no hay errores. |
| `x = x = x = …` muy largo desbordaba la pila. | Límite de 64 niveles de anidamiento en el parser, probado con 100.000. |
| ¿Hilo aparte para el script del jugador? El plan lo proponía. | No (D-19): con un presupuesto de pasos nada puede colgarse y un hilo rompería el determinismo. Lo aprobó Yeray. |

## Fase 3 · 5 de octubre · Simulación y balance (PR #4)

- Todos los números del juego en `assets/data/*.json`, validados al cargar.
- La simulación completa de una oleada y `sim-cli`, que juega el MVP con un
  jugador de referencia y escribe `docs/BALANCE.md`.

| Problema | Solución |
|---|---|
| **La regla del doble del plan no se cumplía para ninguna pieza**: el hardware del mismo precio rendía más que la sintaxis. | No se ajustó por mi cuenta: se informó con datos y Yeray decidió la revisión de balance. |
| Con 20 vidas, las oleadas 1–6 no se podían perder ni sin disparar. | 10 vidas por oleada (D-35, Yeray). |

## Revisión de balance y barrido · 5–6 de octubre (PR #5 y #6)

- Topes de hardware según la sintaxis comprada (D-34) y enemigos retocados
  (D-37). La regla del doble mejora, pero ninguna pieza llega a ×2.
- Barrido de 209 combinaciones del Regenerador
  ([`BARRIDO-REGENERADOR.md`](BARRIDO-REGENERADOR.md)): ninguna lleva
  Variables a ×2.

**Decisión de Yeray**: Variables queda como excepción documentada (D-39) y se
revisa en el playtest. No más barridos.

## Camino al 3D · 6 de octubre (PR #7)

Yeray quiere que a largo plazo el juego se vea en 3D. Se apuntó como D-40:
todo el dibujo del mundo pasa por una sola interfaz (`WorldRenderer`), para
cambiar el 2D por un 3D sin tocar la simulación ni la UI.

## Fase 4 · 6–7 de octubre · Se juega (PR #8)

- Pantalla de combate: camino, torretas, enemigos, balas, efectos, HUD,
  «Desplegar», recarga a mano con clic y velocidad ×3.

![Antes de desplegar](capturas/fase4-01-campo-antes-de-desplegar.png)
![SinMunicionException encima de la torreta](capturas/fase4-02-sinmunicion-encima-de-la-torreta.png)
![Enemigos entrando](capturas/fase4-03-enemigos-entrando.png)
![Oleada en marcha](capturas/fase4-04-oleada-en-marcha.png)
![Recargada a mano](capturas/fase4-05-recargada-a-mano-y-disparando.png)
![Oleada 1 superada](capturas/fase4-06-oleada-1-superada.png)

| Problema | Solución |
|---|---|
| Con `disparar();` la torreta vacía el cargador al aire antes de que llegue nadie. | Es a propósito (D-05). Yeray lo probó: «me molestó, pero así luego tienes que programar que solo dispare cuando hay enemigos». |
| Yeray quería el ×3 como un desplegable que se abre a lo ancho en ×1, ×2 y ×3. | Selector que se abre al pasar el ratón (D-47). |
| En Windows el juego se congelaba al mover o redimensionar la ventana. | El lanzador dibuja un frame en cada aviso de movimiento de la ventana, y el juego mide su propio tiempo (D-48). |
| Yeray quería poder volver a oleadas anteriores. | Mapa de oleadas con candados y bandera (D-49), hecho en la fase 5. |

![Selector plegado](capturas/fase4-07-selector-velocidad-plegado.png)
![Selector abierto](capturas/fase4-08-selector-velocidad-abierto.png)

Probado por Yeray en Windows 11: todo bien. Recargas a mano en 1,43, 1,12 y
0,12 s (media 0,89 s); el simulador suponía 1 s, así que se sostiene.

## Fase 5 · 7 de octubre · Editor, tienda, parte, informe y mapa (PR #9)

- Editor de código propio, con colores, memoria, errores por línea,
  deshacer y portapapeles. La tienda, con tarjeta de ejemplo al comprar. El
  parte de oleada, el informe al acabar y el mapa de oleadas.

![Parte de oleada](capturas/fase5-01-parte-de-oleada.png)
![Editor con el código inicial](capturas/fase5-02-editor-codigo-inicial.png)
![Editor con un if sin comprar](capturas/fase5-03-editor-if-sin-comprar.png)
![Tienda](capturas/fase5-04-tienda.png)
![Mapa de oleadas](capturas/fase5-05-mapa-de-oleadas.png)
![Informe de una oleada perfecta](capturas/fase5-06-informe-oleada-perfecta.png)

| Problema | Solución |
|---|---|
| Los ejemplos de la tienda rompían los textos: las llaves `{ }` del código confunden al formateador de mensajes. | Los ejemplos van en `shop.json` y se compilan al cargar. |
| Los textos que empezaban con espacios los perdían. | La sangría la pone el panel, no el texto. |
| El panel lateral cerrado seguía ocupando sitio. | Ancho 0 cuando está cerrado. |
| El contador de memoria junto al número de línea se leía como «11». | Barras azules en lugar de números. |
| Los errores decían «EOF». | Ahora dicen «el final del código». |

## Fase 5b · 7 de octubre · Manual del jugador (PR #10)

**Origen**: Yeray escribió el `if` que recarga y la torreta seguía lanzando
SinMunicionException. El código era correcto. La causa estaba en cómo
funciona el editor, que no se explicaba en ningún sitio:
- cada torreta tiene su propio código;
- el código solo entra en la siguiente oleada;
- y si no compila, la torreta sigue con el anterior.

De ahí salió la idea de un manual dentro del juego (D-54).

- 45 páginas en 8 capítulos: cada método, cada pieza de sintaxis, cada
  excepción, cada enemigo, errores al escribir y recetas.
- Candado en lo que aún no tienes, con lo que lo trae. Botón «Copiar» en cada
  ejemplo.
- Al arrancar, el juego compila todos los ejemplos y comprueba que no falta
  ninguna página. Los números salen de los datos.

![Índice del manual](capturas/fase5b-01-manual-indice.png)
![Página bloqueada](capturas/fase5b-02-manual-pagina-bloqueada.png)
![Copiado](capturas/fase5b-03-manual-copiado.png)
![Ejemplo pegado en el editor](capturas/fase5b-04-ejemplo-pegado-en-el-editor.png)
![Cómo se juega](capturas/fase5b-05-manual-como-se-juega.png)

| Problema | Solución |
|---|---|
| Los textos del manual citan muchos números y los ejemplos llevan llaves, que el formateador de mensajes no admite. | Los números se citan por nombre (`{cargador}`) y se rellenan sin el formateador; el código va en `manual.json`. |
| Algunas líneas de código no cabían a lo ancho del panel. | Partidas en dos líneas, y un test comprueba que todo cabe. |
| En los tests no hay portapapeles. | El panel recibe el portapapeles por constructor. |

## Fase 6 · 7 de octubre · Guardado, ajustes y robustez

**Decidido por Yeray antes de empezar**: una sola partida, guardado
automático sin botón, se guardan el borrador y la versión buena del código, y
los ajustes son pantalla completa, tamaño de la interfaz, tamaño del código y
nueva partida.

- Se guarda solo (al acabar oleada, comprar, cambiar código, elegir oleada y
  cerrar) en `save.json`, con escritura atómica y copia `.bak`.
- Si la partida está rota, se usa la copia; si también lo está, partida nueva
  con aviso. El fichero roto se guarda aparte.
- Panel de Ajustes y log en `logs/`. Un error en un frame se apunta en el log
  y el juego sigue.

![Ajustes](capturas/fase6-01-ajustes.png)
![Código a 20 px sin perder lo escrito](capturas/fase6-02-codigo-a-20-px-sin-perder-lo-escrito.png)
![Partida recuperada al volver a abrir](capturas/fase6-04-partida-recuperada-al-volver.png)

| Problema | Solución |
|---|---|
| Con la interfaz al 150 % en una ventana de 1280×720 no cabía nada: el parte de oleada se salía de la pantalla (captura de abajo). | La interfaz crece solo hasta donde cabe en la ventana: el 150 % necesita 1920×1080 o pantalla completa. El panel lo explica. |
| Al arrastrar el borde de la ventana, la escala cambiaría con cada píxel y regeneraría las fuentes sin parar. | La escala salta solo entre los tamaños de la lista. |
| Cambiar un tamaño rehace toda la pantalla, y el editor guardaba el texto dentro de la pantalla. | Los borradores viven en `WaveLoop` (también se guardan), así que el código sobrevive. |
| Rehacer la pantalla desde un clic de esa misma pantalla la destruía mientras se usaba. | El cambio se hace en el frame siguiente. |
| En los tests no hay portapapeles ni ventana. | Las dependencias entran por constructor (`GameControls`, carpeta y reloj). |
| En Windows (CI) fallaban los tests del juego entero: el log se quedaba abierto y Windows no deja borrar un fichero en uso. | El log se cierra al cerrar el juego; un test comprueba que no queda su `.lck`. |
| **En el Windows de Yeray el juego se abría y se cerraba** al momento: al mostrarse la ventana, Windows avisa de que se ha movido y el lanzador dibuja un frame en cada movimiento (D-48), antes de que el juego exista. El guardado automático aún no estaba creado. En Linux no pasa, por eso ni los tests ni mis pruebas lo vieron. | Un frame antes de crear el juego no hace nada; un test lo reproduce. |
| La tienda volvía arriba sola al comprar (Yeray): se rehace con cada compra y con cada moneda ganada, y perdía la posición. | Conserva el desplazamiento al rehacerse; un test lo comprueba. |
| Mi primera prueba seguía al 150 %: un reemplazo de texto en el código no se había aplicado. | Lo detectó la línea nueva del log («Interface at 100 % (asked 150 %)»); arreglado y comprobado. |

![Interfaz al 150 % sin límite (el problema)](capturas/fase6-03-interfaz-al-150-sin-limite-problema.png)

## Fase 6b · 7 de octubre · Manual para principiantes y economía decidida

**Lo que pidió Yeray después de probar la fase 6**:
- la tienda se iba arriba al comprar;
- el manual debía entenderlo alguien que nunca ha programado, y las piezas
  necesitaban nombres que se entendieran;
- y la economía se rompía repitiendo una oleada fácil a ×3.

- Piezas renombradas: «Decidir (if/else)», «Recordar (variables)»,
  «Combinar condiciones (&&, ||, !)», «Repetir (while)».
- Manual reescrito con «Empieza aquí» y «Símbolos y palabras».
- Economía nueva decidida (D-57), para la fase 6c: estrellas que se cobran una
  sola vez (superar, sin fugas, reto de código) y una moneda de jefes para lo
  más grande.

![Manual: Empieza aquí](capturas/fase6b-01-manual-empieza-aqui.png)
![Los símbolos, uno a uno](capturas/fase6b-02-simbolos-uno-a-uno.png)

| Problema | Solución |
|---|---|
| «Cómo leer el código en voz alta» salía con candado porque su ejemplo usa `&&`: una página para principiantes bloqueada al principio. | El requisito pasa al ejemplo, no a la página. |
| Farmear rompía la economía: la oleada 3 perfecta a ×3 da 159 por minuto y toda la sintaxis cuesta 385, así que en unos 2,5 minutos se compraba todo sin programar mejor. | Lo medí con las fórmulas del plan y Yeray eligió el modelo de estrellas (como en Human Resource Machine u Opus Magnum: retos de menos líneas, menos pasos). Cambia el plan; queda apuntado como D-57. |
| Cambiar los nombres rompía textos del plan y de D-34 que tienen test («Aún no has desbloqueado while»). | Los tests se actualizan a propósito, con el nombre entre comillas: «Aún no has desbloqueado «Repetir (while)»». |
| El símbolo || no es fácil de encontrar en el teclado. | El manual dice cómo escribirlo en un teclado español (AltGr + 1). |

## Arreglo · 7 de octubre · Botones desplazados al cambiar la ventana

| Problema | Solución |
|---|---|
| Yeray: con la ventana grande o con otra forma, al hacer clic en la parte izquierda de un botón se pulsaba el de su izquierda. El mapa se dibuja en un recuadro 16:9 con bandas a los lados, y la interfaz se dibujaba después dentro de ese mismo recuadro, encogida, mientras los clics se calculaban sobre la ventana entera. En 1280×720 no se notaba porque no hay bandas. | La interfaz recupera la ventana entera antes de dibujarse (una línea). Un test con una ventana de 1920×1009 lo reproducía (dibujaba desplazada 63 píxeles) y ahora pasa. |


## Fase 6c · 7 de octubre · Estrellas, retos y trofeos

**Lo que pidió Yeray**: que farmear no sirva y que para avanzar haya que
romperse un poco más el coco programando. Eligió estrellas que se cobran una
sola vez, un reto fijo por oleada y una moneda que solo dan los jefes.

- Cada oleada tiene 3 estrellas: superarla, sin fugas y su reto. Repetir no
  paga nada.
- Diez retos: sin disparos al aire, sin recargar a mano, código corto, ningún
  disparo a enemigos ya derribados, pocos disparos, ninguna excepción…
- El jefe da un trofeo la primera vez; se gastará en los próximos actos.
- Todo el juego enseña las estrellas: HUD, parte de oleada, informe, tienda,
  mapa y una página nueva del manual.
- Las partidas guardadas con dinero se convierten solas en estrellas.
- El simulador se ha rehecho para calibrarlo: dice si una partida queda sin
  salida y cuánto margen hay.

![Parte de oleada con las 3 estrellas y el reto](capturas/fase6c-01-parte-con-estrellas-y-reto.png)
![Informe: una estrella nueva y el reto fallado por 3 disparos al aire](capturas/fase6c-02-informe-estrellas-y-reto-fallado.png)
![Tienda en estrellas](capturas/fase6c-03-tienda-en-estrellas.png)
![Tienda: trofeos de jefe](capturas/fase6c-04-tienda-trofeos.png)
![Mapa con las estrellas de cada oleada](capturas/fase6c-05-mapa-con-estrellas.png)
![Manual: Estrellas, retos y trofeos](capturas/fase6c-06-manual-estrellas-retos-trofeos.png)
![Tienda: devolver una mejora](capturas/fase6c-07-tienda-devolver.png)

| Problema | Solución |
|---|---|
| Con los primeros precios (sintaxis 2, 4, 6 y 8) el simulador se quedaba **bloqueado en la oleada 6**: sin estrellas para comprar y sin oleadas que dieran más. Antes, farmear lo arreglaba todo. | Medí oleada por oleada lo mínimo para ganarla (una sonda temporal). La sintaxis es lo que más pesa: con las 4 piezas la oleada 6 sale sin hardware. Precios bajados a 1–4 y 1 por nivel de hardware. Ahora sobran 7 estrellas. |
| El jugador de referencia gastaba en cargador, que no sirve contra el jefe, y luego le faltaban estrellas para el disipador: **una mala compra te deja sin salida para siempre**. | El jugador de referencia ahora compra lo que más le ayuda contra la oleada que tiene delante. Para las personas, Yeray aprobó **devolver mejoras** (D-59): daño, cargador y disipador se devuelven nivel a nivel por lo que costaron. La sintaxis y la torreta extra no, porque se perdería código. |
| «Devolver: +1 estrella» y «Comprar: 1 estrella» juntos no cabían en la fila. | El botón de devolver dice solo «Devolver (+1)». |
| Los primeros retos los cumplía sin querer el mejor script (7 de 10): no obligaban a pensar. | Medí cada script en cada oleada (memoria, disparos, al aire, balas perdidas) y escogí retos que el mejor script no cumple pero otro sí. Ahora hay que cambiar el código en 9 de 10. |
| Sin retos, ¿se llega al jefe? | El simulador juega también sin intentarlos: se bloquea en la oleada 7. Los retos son necesarios, como quería Yeray. |
| La fuente no tiene ★. | Las estrellas se marcan con ✓ y ○ (y ●○ en el mapa), que sí tiene; los añadí a los caracteres de la fuente. |
| «Comprar: 1 estrella» es más largo que «Comprar: 25» y la tienda se salía del panel por la izquierda. | El botón va debajo del texto de cada fila. |
| El parte de oleada movía la fila del enemigo a la derecha al añadir la línea larga de las estrellas. | La columna del texto se lleva el espacio sobrante. |
