# Balance del MVP (oleadas 1–10)

Generado por `sim-cli` con `./gradlew :sim-cli:run --args="--write docs/BALANCE.md"`.
No editar a mano: se regenera con los datos de `assets/data/`. Los números que **no
vienen del plan** están marcados; el plan sigue siendo la fuente de verdad y aquí no se
ajusta nada: si algo no cuadra, sale en «Contradicciones con el plan».

## 1. Economía: tabla del plan

Calculada con las fórmulas de `balance.json`. Coincide con la tabla del plan
(lo comprueba `FormulasTest`).

| Oleada | Enemigos | Vida por enemigo | Dinero por enemigo | Dinero por oleada |
|---|---|---|---|---|
| 1 | 8 | 10 | 2 | 16 |
| 5 | 16 | 19,39 | 3,50 | 56 |
| 10 | 26 | 44,35 | 7,04 | 183,04 |
| 20 | 46 | 232,14 | 28,46 | 1309,16 |
| 30 | 60 | 1215,01 | 115,15 | 6909 |

## 2. Números que no vienen del plan

Valores provisionales (D-12 y siguientes) que el plan no fija. Salen de la lista
`notFromPlan` de cada fichero.

| Fichero | Campo | Valor |
|---|---|---|
| `data/balance.json` | `ticksPerSecond` | 60 |
| `data/balance.json` | `startingMoney` | 0 |
| `data/balance.json` | `boss.repeatKillRewardInEnemies` | 20 |
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
| `data/enemies.json` | `types.SWARM.rewardMultiplier` | 0.3 |
| `data/enemies.json` | `types.SWARM.groupSpacingUnits` | 12 |
| `data/enemies.json` | `types.BOSS.speed` | 50 |
| `data/enemies.json` | `types.BOSS.rewardMultiplier` | 0 |
| `data/enemies.json` | `types.BOSS.startsArmored` | false |
| `data/enemies.json` | `types.REGENERATOR.healthMultiplier` | 2 |
| `data/enemies.json` | `types.ARMORED.healthMultiplier` | 2 |
| `data/waves.json` | `spawnWindowSeconds` | 20 |
| `data/waves.json` | `waves` | [{"wave":1,"groups":[{"type":"BASIC","count":8}]},{"wave":2,"groups":[{"type":"BASIC","count":10}]},{"wave":3,"groups… |
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
- Compra la siguiente pieza de sintaxis en cuanto puede pagarla. Atascado tras una
  derrota, compra lo que pueda tras cada oleada farmeada: sintaxis; si no, la torreta
  extra; si no, el hardware más barato. Con toda la sintaxis, gasta en hardware igual.
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

El jugador de referencia derrota al Jefe 1 tras **25 oleadas jugadas**.

| # | Oleada | Resultado | Fugas | Cobrado | Dinero | Script | Torretas | Compra | Excepciones |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 1 | perfecta | 0 | 20 | 20 | L0 | 1 |  | 2 |
| 2 | 2 | perfecta | 0 | 28,75 | 23,75 | L0 | 1 | IF_ELSE | 3 |
| 3 | 3 | perfecta | 0 | 39,60 | 3,35 | L1 | 1 | VARIABLES | 0 |
| 4 | 4 | superada | 5 | 27,36 | 30,71 | L2 | 1 |  | 0 |
| 5 | 5 | superada | 7 | 31,50 | 62,21 | L2 | 1 |  | 0 |
| 6 | 6 | **derrota** | 10 | 16,08 | 63,29 | L2-ammo | 1 | MAGAZINE | 0 |
| 7 | 5 | superada | 9 | 24,50 | 67,79 | L2-ammo | 1 | DAMAGE | 0 |
| 8 | 6 | **derrota** | 10 | 32,16 | 77,45 | L2-ammo | 1 | MAGAZINE | 0 |
| 9 | 5 | superada | 6 | 35 | 12,45 | L2-ammo | 1 | LOGIC_OPS | 0 |
| 10 | 6 | **derrota** | 10 | 32,16 | 12,61 | L3-ammo | 1 | DAMAGE | 0 |
| 11 | 5 | superada | 5 | 38,50 | 17,36 | L3-ammo | 1 | MAGAZINE | 0 |
| 12 | 6 | superada | 8 | 40,20 | 57,56 | L3-ammo | 1 |  | 0 |
| 13 | 7 | **derrota** | 10 | 32,41 | 14,97 | L3-ammo | 2 | EXTRA_TURRET | 0 |
| 14 | 6 | superada | 5 | 52,26 | 27,23 | L3-ammo | 2 | HEAT_SINK | 0 |
| 15 | 7 | superada | 9 | 50,93 | 78,16 | L3-ammo | 2 |  | 0 |
| 16 | 8 | superada | 9 | 54,28 | 132,44 | L3-ammo | 2 |  | 0 |
| 17 | 9 | **derrota** | 10 | 48,36 | 129,60 | L3-ammo | 2 | DAMAGE | 0 |
| 18 | 8 | superada | 6 | 70,24 | 135,84 | L3-ammo | 2 | HEAT_SINK | 0 |
| 19 | 9 | **derrota** | 10 | 60,60 | 94,04 | L3-ammo | 2 | HEAT_SINK | 0 |
| 20 | 8 | superada | 6 | 70,24 | 0,44 | L3-ammo | 2 | HEAT_SINK | 0 |
| 21 | 9 | **derrota** | 10 | 60,60 | 61,04 | L3-ammo | 2 |  | 0 |
| 22 | 8 | superada | 6 | 70,24 | 131,28 | L3-ammo | 2 |  | 0 |
| 23 | 8 | superada | 6 | 70,24 | 1,52 | L3-ammo | 2 | WHILE | 0 |
| 24 | 9 | superada | 4 | 92,44 | 43,33 | L4-ammo | 2 | MAGAZINE | 0 |
| 25 | 10 | superada | 7 | 653,30 | 122,93 | L4-ammo | 2 | MAGAZINE, DAMAGE, MAGAZINE, DAMAGE, MAGAZINE | 0 |

## 5. Pasadas por muro

Un muro es una oleada perdida antes de superarla por primera vez. «Pasadas» son las
oleadas anteriores repetidas mientras tanto. Regla del plan: como mucho 5.

| Muro (oleada) | Intentos | Pasadas | Superado | Regla de 5 |
|---|---|---|---|---|
| 6 | 4 | 3 | sí | cumple |
| 7 | 2 | 1 | sí | cumple |
| 9 | 4 | 4 | sí | cumple |

## 6. Tiempo hasta el Jefe 1

Tiempo de juego simulado: **16,09 min** (con ×3 en las repeticiones permitidas, sin contar
el tiempo de escribir código). El plan apunta a 20–30 min «por comprobar con
jugadores», incluido el tiempo de escribir, así que este valor es un mínimo.

## 7. Ritmo de compras de sintaxis

Oleada en la que el jugador de referencia compra cada pieza frente a la oleada en la
que el plan dice que llega («Llega hacia la oleada»).

| Pieza | Precio | Llega (plan) | Comprada en la oleada | Nº de oleada jugada |
|---|---|---|---|---|
| IF_ELSE | 25 | 2–3 | 2 | 2 |
| VARIABLES | 60 | 4–5 | 3 | 3 |
| LOGIC_OPS | 100 | 6–7 | 5 | 9 |
| WHILE | 200 | 8–9 | 8 | 23 |

## 8. Regla del doble

`k` = mayor multiplicador de vida de los enemigos con el que el jugador de referencia
aún supera **sin fugas** la oleada en la que llega la pieza (búsqueda binaria entre 0,05 y 64).
Sin fugas y no solo «superada»: si una oleada trae menos enemigos que vidas tiene el
núcleo, no se puede perder y la medida se saturaría.
Se parte del estado justo antes de comprar la pieza y se compara la pieza con su
script frente al mejor hardware que se compra con el mismo dinero. En cada lado se
prueban todas las variantes de script que caben en memoria y se toma la mejor.
Cumple si la proporción es ≥ 2.

### 8.1 Con los topes de hardware del juego (sección 9)

| Pieza | Oleada | Con la pieza | k | Sin ella ni hardware | k | Sin ella: mejor hardware | k | Proporción | Regla |
|---|---|---|---|---|---|---|---|---|---|
| IF_ELSE | 2 | `L1` | 1,69 | `L0` | 1,27 | `L0` + nada | 1,27 | 1,33 | **no cumple** |
| VARIABLES | 4 | `L2` | 0,61 | `L1` | 0,36 | `L1` + DAMAGE +1, MAGAZINE +1 | 0,57 | 1,07 | **no cumple** |
| LOGIC_OPS | 6 | `L3-ammo-b` | 0,41 | `L2-ammo` | 0,30 | `L2-ammo` + DAMAGE +1, MAGAZINE +1 | 0,49 | 0,83 | **no cumple** |
| WHILE | 8 | `L4-ammo-b` | 0,92 | `L3-ammo` | 0,55 | `L3-ammo` + nada | 0,55 | 1,67 | **no cumple** |

### 8.2 Sin topes de hardware (referencia)

Lo que pasaría si el hardware no tuviera topes: mide cuánto aporta la pieza por sí
sola frente al hardware libre.

| Pieza | Oleada | Con la pieza | k | Sin ella ni hardware | k | Sin ella: mejor hardware | k | Proporción | Regla |
|---|---|---|---|---|---|---|---|---|---|
| IF_ELSE | 2 | `L1` | 1,69 | `L0` | 1,27 | `L0` + DAMAGE +1 | 1,59 | 1,07 | **no cumple** |
| VARIABLES | 4 | `L2` | 0,61 | `L1` | 0,36 | `L1` + DAMAGE +1, MAGAZINE +2 | 0,76 | 0,80 | **no cumple** |
| LOGIC_OPS | 6 | `L3-ammo-b` | 0,41 | `L2-ammo` | 0,30 | `L2-ammo` + EXTRA_TURRET +1 | 0,55 | 0,75 | **no cumple** |
| WHILE | 8 | `L4-ammo-b` | 0,92 | `L3-ammo` | 0,55 | `L3-ammo` + DAMAGE +1 | 0,63 | 1,46 | **no cumple** |

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
- Regla del doble (con topes): LOGIC_OPS rinde 0,83 veces lo mismo en hardware en la oleada 6 (el plan pide al menos 2).
- Regla del doble (con topes): WHILE rinde 1,67 veces lo mismo en hardware en la oleada 8 (el plan pide al menos 2).

## 11. Avisos

No contradicen el plan, pero conviene saberlos al decidir.

- Con 10 vidas en el núcleo, la oleada 1 trae menos enemigos que vidas y no se pueden perder aunque ninguna torreta dispare: ahí no puede haber muros y la presión es solo de dinero (menos cobro y sin oleada perfecta ni ×3).
- Oleada 1 con `disparar();` y recarga a mano tras 1 s: se cuelan 0 de 8 enemigos y salta 2 veces SinMunicionException. El plan quiere una recarga a mano que «agobie lo justo» y que «no des abasto»; con estos datos la oleada sale perfecta. Lo deciden el tiempo de reacción real, el rango y las velocidades.
