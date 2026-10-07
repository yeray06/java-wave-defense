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
