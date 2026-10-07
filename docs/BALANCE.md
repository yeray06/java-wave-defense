# Balance del MVP (oleadas 1–10)

Generado por `sim-cli` con `./gradlew :sim-cli:run --args="--write docs/BALANCE.md"`.
No editar a mano: se regenera con los datos de `assets/data/`. Los números que **no
vienen del plan** están marcados; el plan sigue siendo la fuente de verdad y aquí no se
ajusta nada: si algo no cuadra, sale en «Contradicciones con el plan».

## 1. Enemigos y estrellas

Enemigos y vida, calculados con las fórmulas de `balance.json`. Coinciden con la tabla
del plan (lo comprueba `FormulasTest`).

| Oleada | Enemigos | Vida por enemigo |
|---|---|---|
| 1 | 8 | 10 |
| 5 | 16 | 19,39 |
| 10 | 26 | 44,35 |
| 20 | 46 | 232,14 |
| 30 | 60 | 1215,01 |

Economía de estrellas (D-57, sustituye al «Farmeo» del plan): cada oleada da hasta 3
estrellas y cada una se cobra una sola vez (superarla, sin fugas y su reto). Repetir no
paga. Cada jefe da un trofeo la primera vez que cae.

| Concepto | Estrellas |
|---|---|
| Al empezar | 0 |
| Máximo en las oleadas 1–10 | 30 |
| Toda la sintaxis del MVP | 10 |
| Todo el hardware del MVP | 14 |

## 2. Números que no vienen del plan

Valores provisionales (D-12 y siguientes) que el plan no fija. Salen de la lista
`notFromPlan` de cada fichero.

| Fichero | Campo | Valor |
|---|---|---|
| `data/balance.json` | `ticksPerSecond` | 60 |
| `data/balance.json` | `speeds[1]` | (no encontrado) |
| `data/balance.json` | `startingStars` | 0 |
| `data/turret.json` | `rangeUnits` | 330 |
| `data/turret.json` | `bulletSpeedUnitsPerSecond` | 1000 |
| `data/turret.json` | `defaultTarget` | "NEAREST_TO_TURRET" |
| `data/turret.json` | `initialScript` | "disparar();" |
| `data/enemies.json` | `types.BASIC.speed` | 100 |
| `data/enemies.json` | `types.REGENERATOR.speed` | 100 |
| `data/enemies.json` | `types.REGENERATOR.regenFractionPerSecond` | 0.5 |
| `data/enemies.json` | `types.ARMORED.speed` | 80 |
| `data/enemies.json` | `types.SWARM.speed` | 200 |
| `data/enemies.json` | `types.SWARM.healthMultiplier` | 0.3 |
| `data/enemies.json` | `types.SWARM.groupSpacingUnits` | 12 |
| `data/enemies.json` | `types.BOSS.speed` | 50 |
| `data/enemies.json` | `types.BOSS.startsArmored` | false |
| `data/enemies.json` | `types.REGENERATOR.healthMultiplier` | 2 |
| `data/enemies.json` | `types.ARMORED.healthMultiplier` | 2 |
| `data/enemies.json` | `types.BASIC.spawnGapFactor` | 1 |
| `data/enemies.json` | `types.REGENERATOR.spawnGapFactor` | 1 |
| `data/enemies.json` | `types.ARMORED.spawnGapFactor` | 1 |
| `data/enemies.json` | `types.SWARM.spawnGapFactor` | 1 |
| `data/enemies.json` | `types.BOSS.spawnGapFactor` | 1 |
| `data/waves.json` | `spawnWindowSeconds` | 20 |
| `data/waves.json` | `waves` | [{"wave":1,"groups":[{"type":"BASIC","count":8}],"challenge":{"type":"NO_AIR_SHOTS","limit":0}},{"wave":2,"groups":[{… |
| `data/waves.json` | `waves[].challenge` | (no encontrado) |
| `data/shop.json` | `hardware[].prices` | (no encontrado) |
| `data/shop.json` | `syntax[].example` | (no encontrado) |
| `data/shop.json` | `syntax[].price` | (no encontrado) |
| `data/map.json` | `path` | [{"x":0,"y":560},{"x":1000,"y":560},{"x":1000,"y":300},{"x":240,"y":300},{"x":240,"y":80},{"x":500,"y":80}] |
| `data/map.json` | `turretSlots` | [{"x":620,"y":430},{"x":620,"y":190}] |
| `data/script-api.json` | `limits.maxStepsPerCycle` | 100000 |
| `data/script-api.json` | `limits.maxNestingDepth` | 64 |
| `sim-cli` (jugador) | `manualReloadReactionSeconds` | 1 |

## 3. Jugador de referencia

- Escribe el mejor script de referencia que le permite su sintaxis
  (`sim-cli/src/main/resources/reference/scripts/`). Con la oleada 6 alcanzada usa
  `usarMunicion` si su sintaxis cabe en la memoria.
- Las variantes `-b` y `-c` dejan fuera otra cosa para caber en 12 líneas. En la
  partida usa la primera; en la regla del doble prueba todas y se queda la mejor.
- Recarga a mano una torreta vacía 1 s después de que se quede sin balas, también si
  está colgada (D-03b).
- Si supera una oleada sin cumplir su reto y alguno de los scripts que sabe escribir
  la supera cumpliéndolo, la repite con ese script. Sabe de antemano qué script cumple:
  es la cota alta de alguien que lee el reto y lo intenta.
- Compra la siguiente pieza de sintaxis en cuanto puede pagarla; con toda la sintaxis,
  gasta en hardware. Atascado tras una derrota: sintaxis si llega; si no, la torreta
  extra; si no, el hardware más barato, y vuelve a intentarlo. Sin nada que comprar,
  repite la primera oleada anterior que aún le dé una estrella (sin fugas o su reto).
  Si no queda ninguna, la partida está **bloqueada**.
- Usa ×3 en las oleadas que ya superó sin fugas. El tiempo de escribir código no cuenta.

| Script | Memoria | Código |
|---|---|---|
| `L0` | 1/12 | `disparar();` |
| `L1` | 4/12 | `if (municion() == 0) { recargar(); } else if (hayEnemigos()) { disparar(); }` |
| `L2` | 9/12 | `if (municion() == 0) { recargar(); } else { Enemigo o = objetivo(); if (o == null) { o = enemigoMasCercano(); if (o != null) { apuntar(o); } } if (o != null) { disparar(); } }` |
| `L2-ammo` | 11/12 | `if (municion() == 0) { recargar(); } else { Enemigo o = objetivo(); if (o == null) { o = enemigoMasCercano(); } if (o != null) { apuntar(o); if (o.blindado()) { usarMunicion(PERFORANTE); } else { usarMunicion(NORMAL); } disparar(); } }` |
| `L2-ammo-b` | 10/12 | `if (municion() == 0) { recargar(); } else if (calor() >= 70) { recargar(); } else { Enemigo o = enemigoMasCercano(); if (o != null) { if (o.blindado()) { usarMunicion(PERFORANTE); } else { usarMunicion(NORMAL); } disparar(); } }` |
| `L2-ammo-c` | 10/12 | `if (municion() == 0) { recargar(); } else if (hayEnemigos() == false) { recargar(); } else { Enemigo o = enemigoMasCercano(); if (o != null) { if (o.blindado()) { usarMunicion(PERFORANTE); } else { usarMunicion(NORMAL); } disparar(); } }` |
| `L3-ammo` | 11/12 | `if (municion() == 0 \|\| calor() >= 70) { recargar(); } else { Enemigo o = objetivo(); if (o == null) { o = enemigoMasCercano(); } if (o != null) { apuntar(o); if (o.blindado()) { usarMunicion(PERFORANTE); } else { usarMunicion(NORMAL); } disparar(); } }` |
| `L3-ammo-b` | 11/12 | `if (municion() == 0 \|\| !hayEnemigos()) { recargar(); } else { Enemigo o = objetivo(); if (o == null) { o = enemigoMasCercano(); } if (o != null) { apuntar(o); if (o.blindado()) { usarMunicion(PERFORANTE); } else { usarMunicion(NORMAL); } disparar(); } }` |
| `L4` | 9/12 | `if (municion() == 0) { recargar(); } else { Enemigo o = objetivo(); if (o == null) { o = enemigoMasCercano(); } if (o != null) { apuntar(o); while (municion() > 0 && calor() < 80 && objetivo() != null) { disparar(); } } }` |
| `L4-ammo` | 12/12 | `if (municion() == 0 \|\| calor() >= 70) { recargar(); } else { Enemigo o = objetivo(); if (o == null) { o = enemigoMasCercano(); } if (o != null) { apuntar(o); if (o.blindado()) usarMunicion(PERFORANTE); else usarMunicion(NORMAL); while (municion() > 0 && calor() < 60 && objetivo() != null) { disparar(); } } }` |
| `L4-ammo-b` | 12/12 | `if (municion() == 0 \|\| !hayEnemigos()) { recargar(); } else { Enemigo o = objetivo(); if (o == null) { o = enemigoMasCercano(); } if (o != null) { apuntar(o); if (o.blindado()) usarMunicion(PERFORANTE); else usarMunicion(NORMAL); while (municion() > 0 && calor() < 60 && objetivo() != null) { disparar(); } } }` |

## 4. Partida simulada

El jugador de referencia derrota al Jefe 1 tras **15 oleadas jugadas**.

Para: avance, reto (repite por el reto), muro (reintento) o repaso (atascado, a por
una estrella que falta).

| # | Oleada | Para | Resultado | Fugas | Gana | Estrellas | Script | Torretas | Compra | Excepciones |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 1 | avance | perfecta | 0 | superada, sin fugas | 1 | L0 | 1 | IF_ELSE | 2 |
| 2 | 1 (×3) | reto | perfecta | 0 | reto | 0 | L1 | 1 | VARIABLES | 0 |
| 3 | 2 | avance | perfecta | 0 | superada, sin fugas, reto | 0 | L2 | 1 | LOGIC_OPS | 0 |
| 4 | 3 | avance | perfecta | 0 | superada, sin fugas | 2 | L2 | 1 |  | 0 |
| 5 | 3 (×3) | reto | perfecta | 0 | reto | 3 | L1 | 1 |  | 0 |
| 6 | 4 | avance | superada | 5 | superada, reto | 0 | L2 | 1 | WHILE, DAMAGE | 0 |
| 7 | 5 | avance | superada | 3 | superada | 0 | L4 | 1 | DAMAGE | 0 |
| 8 | 5 | reto | superada | 5 | reto | 0 | L3-ammo | 1 | DAMAGE | 0 |
| 9 | 6 | avance | superada | 4 | superada | 0 | L4-ammo | 1 | MAGAZINE | 0 |
| 10 | 6 | reto | superada | 7 | reto | 0 | L4 | 1 | DAMAGE | 0 |
| 11 | 7 | avance | superada | 7 | superada, reto | 0 | L4-ammo | 2 | EXTRA_TURRET | 0 |
| 12 | 8 | avance | superada | 1 | superada | 0 | L4-ammo | 2 | HEAT_SINK | 0 |
| 13 | 8 | reto | superada | 6 | reto | 0 | L3-ammo | 2 | HEAT_SINK | 0 |
| 14 | 9 | avance | superada | 5 | superada | 0 | L4-ammo | 2 | HEAT_SINK | 0 |
| 15 | 10 | avance | superada | 9 | superada, reto | 0 | L4-ammo | 2 | HEAT_SINK, MAGAZINE | 0 |

## 4.1 Estrellas por oleada

Lo conseguido al acabar la partida simulada. «Script que cumple el reto» es el primero
que lo cumple con lo que tenía comprado al final (vacío: ninguno de referencia).

| Oleada | Reto | Superada | Sin fugas | Reto cumplido | Script que cumple el reto |
|---|---|---|---|---|---|
| 1 | ningún disparo al aire | ★ | ★ | ★ | `L4-ammo` |
| 2 | sin recargar a mano | ★ | ★ | ★ | `L4-ammo` |
| 3 | como mucho 4 líneas de memoria | ★ | ★ | ★ | `L1` |
| 4 | ningún disparo a enemigos ya muertos | ★ | — | ★ | `L3-ammo` |
| 5 | como mucho 52 disparos | ★ | — | ★ | `L3-ammo` |
| 6 | como mucho 10 líneas de memoria | ★ | — | ★ | `L2-ammo-b` |
| 7 | como mucho 100 disparos | ★ | — | ★ | `L3-ammo` |
| 8 | como mucho 90 disparos | ★ | — | ★ | `L3-ammo` |
| 9 | como mucho 9 líneas de memoria | ★ | — | — | `L4` |
| 10 | ninguna excepción | ★ | — | ★ | `L4-ammo` |

Estrellas ganadas: **22 de 30**; sin gastar al final: 0. Trofeos: 1.

Antes de vencer al Jefe 1 gastó **20 estrellas** de las 27 que dan las oleadas 1–9: margen de 7 para quien no las consiga todas.

## 4.2 Sin hacer ningún reto

El mismo jugador, pero sin intentar nunca los retos de código: solo superar oleadas y
repetirlas sin fugas. Mide si los retos hacen falta para llegar al jefe.

Queda **bloqueado** en la oleada 7 con 13 estrellas ganadas: sin retos no se llega al jefe.

## 5. Pasadas por muro

Un muro es una oleada perdida antes de superarla por primera vez. «Pasadas» son las
oleadas anteriores repetidas mientras tanto para ganar estrellas que faltaban. Regla
del plan: como mucho 5.

No hubo muros: el jugador de referencia no perdió ninguna oleada nueva.

## 6. Tiempo hasta el Jefe 1

Tiempo de juego simulado: **8,38 min** (con ×3 en las repeticiones permitidas, sin contar
el tiempo de escribir código). El plan apunta a 20–30 min «por comprobar con
jugadores», incluido el tiempo de escribir, así que este valor es un mínimo.

## 7. Ritmo de compras de sintaxis

Oleada en la que el jugador de referencia compra cada pieza frente a la oleada en la
que el plan dice que llega («Llega hacia la oleada»).

| Pieza | Precio (estrellas) | Llega (plan) | Comprada en la oleada | Nº de oleada jugada |
|---|---|---|---|---|
| IF_ELSE | 1 | 2–3 | 1 | 1 |
| VARIABLES | 2 | 4–5 | 1 | 2 |
| LOGIC_OPS | 3 | 6–7 | 2 | 3 |
| WHILE | 4 | 8–9 | 4 | 6 |

## 8. Regla del doble

`k` = mayor multiplicador de vida de los enemigos con el que el jugador de referencia
aún supera **sin fugas** la oleada en la que llega la pieza (búsqueda binaria entre 0,05 y 64).
Sin fugas y no solo «superada»: si una oleada trae menos enemigos que vidas tiene el
núcleo, no se puede perder y la medida se saturaría.
Se parte del estado justo antes de comprar la pieza y se compara la pieza con su
script frente al mejor hardware que se compra con las mismas estrellas. En cada lado se
prueban todas las variantes de script que caben en memoria y se toma la mejor.
Cumple si la proporción es ≥ 2.

### 8.1 Con los topes de hardware del juego (sección 9)

| Pieza | Oleada | Con la pieza | k | Sin ella ni hardware | k | Sin ella: mejor hardware | k | Proporción | Regla |
|---|---|---|---|---|---|---|---|---|---|
| IF_ELSE | 2 | `L1` | 1,69 | `L0` | 1,27 | `L0` + nada | 1,27 | 1,33 | **no cumple** |
| VARIABLES | 4 | `L2` | 0,61 | `L1` | 0,36 | `L1` + DAMAGE +1, MAGAZINE +1 | 0,57 | 1,07 | **no cumple** |
| LOGIC_OPS | 6 | `L3-ammo-b` | 0,33 | `L2-ammo` | 0,22 | `L2-ammo` + DAMAGE +2, MAGAZINE +1 | 0,49 | 0,67 | **no cumple** |
| WHILE | 8 | `L4-ammo` | 0,08 | `L3-ammo` | 0 | `L3-ammo` + EXTRA_TURRET +1, DAMAGE +1, MAGAZINE +1 | 0,39 | 0,20 | **no cumple** |

### 8.2 Sin topes de hardware (referencia)

Lo que pasaría si el hardware no tuviera topes: mide cuánto aporta la pieza por sí
sola frente al hardware libre.

| Pieza | Oleada | Con la pieza | k | Sin ella ni hardware | k | Sin ella: mejor hardware | k | Proporción | Regla |
|---|---|---|---|---|---|---|---|---|---|
| IF_ELSE | 2 | `L1` | 1,69 | `L0` | 1,27 | `L0` + DAMAGE +1 | 1,59 | 1,07 | **no cumple** |
| VARIABLES | 4 | `L2` | 0,61 | `L1` | 0,36 | `L1` + EXTRA_TURRET +1 | 0,61 | 1 | **no cumple** |
| LOGIC_OPS | 6 | `L3-ammo-b` | 0,33 | `L2-ammo` | 0,22 | `L2-ammo` + EXTRA_TURRET +1, DAMAGE +1 | 0,55 | 0,60 | **no cumple** |
| WHILE | 8 | `L4-ammo` | 0,08 | `L3-ammo` | 0 | `L3-ammo` + EXTRA_TURRET +1, DAMAGE +1, MAGAZINE +1 | 0,39 | 0,20 | **no cumple** |

D-20 pide comprobar Variables contra el Regenerador: es la fila VARIABLES (oleada 4,
la primera con regeneradores).

## 9. Topes de hardware según la sintaxis

Red de seguridad aprobada como añadido al plan: un nivel de hardware no se puede
comprar hasta tener la pieza indicada. En la tienda: «Necesitas variables para
seguir mejorando el daño». Están en `shop.json` (`caps`).

| Hardware | Nivel máximo | Hasta comprar |
|---|---|---|
| DAMAGE | 0 | IF_ELSE |
| DAMAGE | 1 | VARIABLES |
| DAMAGE | 3 | WHILE |
| MAGAZINE | 0 | IF_ELSE |
| MAGAZINE | 1 | VARIABLES |
| MAGAZINE | 3 | WHILE |
| HEAT_SINK | 0 | LOGIC_OPS |
| EXTRA_TURRET | 0 | LOGIC_OPS |

## 10. Contradicciones con el plan

Datos, no ajustes: decidir qué cambiar es de Yeray.

- Regla del doble (con topes): IF_ELSE rinde 1,33 veces lo mismo en hardware en la oleada 2 (el plan pide al menos 2).
- Regla del doble (con topes): VARIABLES rinde 1,07 veces lo mismo en hardware en la oleada 4 (el plan pide al menos 2).
- Regla del doble (con topes): LOGIC_OPS rinde 0,67 veces lo mismo en hardware en la oleada 6 (el plan pide al menos 2).
- Regla del doble (con topes): WHILE rinde 0,20 veces lo mismo en hardware en la oleada 8 (el plan pide al menos 2).

## 11. Avisos

No contradicen el plan, pero conviene saberlos al decidir.

- Llegar al Jefe 1 lleva 8,38 min de juego sin contar el tiempo de escribir código; el plan apunta a 20–30 min en total. Sin farmeo (D-57), el resto lo pone escribir y probar el código de los retos: hay que medirlo con jugadores.
- Con 10 vidas en el núcleo, la oleada 1 trae menos enemigos que vidas y no se pueden perder aunque ninguna torreta dispare: ahí no puede haber muros y solo está en juego la estrella sin fugas (y el reto).
- Oleada 1 con `disparar();` y recarga a mano tras 1 s: se cuelan 0 de 8 enemigos y salta 2 veces SinMunicionException. El plan quiere una recarga a mano que «agobie lo justo» y que «no des abasto»; con estos datos la oleada sale perfecta. Lo deciden el tiempo de reacción real, el rango y las velocidades.
