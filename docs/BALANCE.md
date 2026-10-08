# Balance (oleadas 1–16)

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
| Máximo en las oleadas 1–16 | 48 |
| Toda la sintaxis del MVP | 20 |
| Todo el hardware del MVP | 44 |

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
| `data/enemies.json` | `types.RUNNER.healthMultiplier` | 0.5 |
| `data/enemies.json` | `types.RUNNER.spawnGapFactor` | 1 |
| `data/enemies.json` | `types.MUTANT.speed` | 100 |
| `data/enemies.json` | `types.MUTANT.healthMultiplier` | 1.5 |
| `data/enemies.json` | `types.MUTANT.spawnGapFactor` | 1 |
| `data/enemies.json` | `mutantDamageReduction` | 0.8 |
| `data/waves.json` | `spawnWindowSeconds` | 20 |
| `data/waves.json` | `waves` | [{"wave":1,"groups":[{"type":"BASIC","count":8}],"challenge":{"type":"NO_AIR_SHOTS","limit":0},"mutantWeakness":null}… |
| `data/waves.json` | `waves[].challenge` | (no encontrado) |
| `data/waves.json` | `waves[].mutantWeakness` | (no encontrado) |
| `data/shop.json` | `hardware[].prices` | (no encontrado) |
| `data/shop.json` | `syntax[].example` | (no encontrado) |
| `data/shop.json` | `syntax[].price` | (no encontrado) |
| `data/map.json` | `path` | [{"x":0,"y":560},{"x":1000,"y":560},{"x":1000,"y":300},{"x":240,"y":300},{"x":240,"y":80},{"x":500,"y":80}] |
| `data/map.json` | `turretSlots` | [{"x":620,"y":430},{"x":620,"y":190},{"x":380,"y":430},{"x":850,"y":190}] |
| `data/script-api.json` | `limits.maxStepsPerCycle` | 100000 |
| `data/script-api.json` | `limits.maxNestingDepth` | 64 |
| `data/script-api.json` | `limits.maxCallDepth` | 64 |
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
  Si no queda ninguna, prueba a devolver un nivel de mejora y comprar otro (D-59);
  si tampoco le hace más fuerte, la partida está **bloqueada**.
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
| `L5` | 10/12 | `if (municion() == 0 \|\| calor() >= 70) { recargar(); } else { Enemigo o = null; for (Enemigo e : enemigos()) { if (o == null \|\| e.distanciaAlNucleo() < o.distanciaAlNucleo()) { o = e; } } if (o != null) { apuntar(o); while (municion() > 0 && calor() < 60 && objetivo() != null) { disparar(); } } }` |
| `L5-ammo` | 14/12 | `if (municion() == 0 \|\| calor() >= 70) { recargar(); } else { Enemigo o = null; for (Enemigo e : enemigos()) { if (o == null \|\| e.distanciaAlNucleo() < o.distanciaAlNucleo()) { o = e; } } if (o != null) { apuntar(o); if ({PERFORANTE_SI}) usarMunicion(PERFORANTE); else usarMunicion(NORMAL); while (municion() > 0 && calor() < 60 && objetivo() != null) { disparar(); } } }` |
| `L5-ammo-b` | 13/12 | `if (municion() == 0 \|\| calor() >= 70) { recargar(); } else { Enemigo o = null; for (Enemigo e : enemigos()) { if (o == null \|\| e.distanciaAlNucleo() < o.distanciaAlNucleo()) { o = e; } } if (o != null) { apuntar(o); if ({PERFORANTE_SI}) usarMunicion(PERFORANTE); else usarMunicion(NORMAL); disparar(); } }` |

## 4. Partida simulada

El jugador de referencia derrota al Jefe 1 tras **17 oleadas jugadas** y supera la última, la 16, tras **48**.

Para: avance, reto (repite por el reto), muro (reintento) o repaso (atascado, a por
una estrella que falta).

| # | Oleada | Para | Resultado | Fugas | Gana | Estrellas | Script | Torretas | Compra | Excepciones |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 1 | avance | perfecta | 0 | superada, sin fugas | 0 | L0 | 1 | IF_ELSE, DAMAGE | 2 |
| 2 | 1 (×3) | reto | perfecta | 0 | reto | 1 | L1 | 1 |  | 0 |
| 3 | 2 | avance | perfecta | 0 | superada, sin fugas, reto | 1 | L1 | 1 | VARIABLES, MAGAZINE | 0 |
| 4 | 3 | avance | perfecta | 0 | superada, sin fugas | 0 | L2 | 1 | LOGIC_OPS | 0 |
| 5 | 3 (×3) | reto | perfecta | 0 | reto | 0 | L1 | 1 | DAMAGE | 0 |
| 6 | 4 | avance | perfecta | 0 | superada, sin fugas, reto | 0 | L2 | 2 | EXTRA_TURRET, MAGAZINE | 0 |
| 7 | 5 | avance | superada | 2 | superada | 0 | L2 | 2 | DAMAGE | 0 |
| 8 | 6 | avance | superada | 2 | superada | 1 | L3-ammo | 2 |  | 0 |
| 9 | 6 | reto | superada | 7 | reto | 2 | L2-ammo-b | 2 |  | 0 |
| 10 | 7 | avance | superada | 7 | superada, reto | 0 | L3-ammo | 2 | WHILE | 0 |
| 11 | 8 | avance | superada | 5 | superada | 0 | L4-ammo | 2 | HEAT_SINK | 0 |
| 12 | 8 | reto | superada | 6 | reto | 0 | L3-ammo | 2 | DAMAGE | 0 |
| 13 | 9 | avance | superada | 6 | superada | 1 | L4-ammo | 2 |  | 0 |
| 14 | 10 | avance | **derrota** | 10 |  | 0 | L4-ammo | 2 | HEAT_SINK | 0 |
| 15 | 10 | muro | **derrota** | 8 |  | 0 | L4-ammo | 2 |  | 0 |
| 16 | 5 | repaso | perfecta | 0 | sin fugas | 0 | L4-ammo | 2 | HEAT_SINK | 0 |
| 17 | 10 | muro | superada | 6 | superada, reto | 2 | L4-ammo | 2 |  | 0 |
| 18 | 11 | avance | superada | 5 | superada | 3 | L4-ammo | 2 |  | 0 |
| 19 | 12 | avance | **derrota** | 10 |  | 2 | L4-ammo | 2 | HEAT_SINK | 0 |
| 20 | 12 | muro | superada | 9 | superada | 3 | L4-ammo | 2 |  | 0 |
| 21 | 13 | avance | **derrota** | 10 |  | 2 | L4-ammo | 2 | MAGAZINE | 0 |
| 22 | 13 | muro | **derrota** | 10 |  | 1 | L4-ammo | 2 | MAGAZINE | 0 |
| 23 | 13 | muro | **derrota** | 10 |  | 1 | L4-ammo | 2 |  | 0 |
| 24 | 5 (×3) | repaso | perfecta | 0 | reto | 2 | L3-ammo | 2 |  | 0 |
| 25 | 13 | muro | **derrota** | 10 |  | 2 | L4-ammo | 2 |  | 0 |
| 26 | 6 | repaso | perfecta | 0 | sin fugas | 3 | L4-ammo | 2 |  | 0 |
| 27 | 13 | muro | **derrota** | 10 |  | 3 | L4-ammo | 2 |  | 0 |
| 28 | 7 | repaso | perfecta | 0 | sin fugas | 0 | L4-ammo | 2 | FOR | 0 |
| 29 | 13 | muro | **derrota** | 10 |  | 0 | L5-ammo-b | 2 |  | 0 |
| 30 | 8 | repaso | perfecta | 0 | sin fugas | 1 | L5-ammo-b | 2 |  | 0 |
| 31 | 13 | muro | **derrota** | 10 |  | 0 | L5-ammo-b | 2 | -DAMAGE +MEMORY | 0 |
| 32 | 13 | muro | **derrota** | 10 |  | 0 | L5-ammo | 2 |  | 0 |
| 33 | 11 | repaso | perfecta | 0 | sin fugas | 0 | L5-ammo | 2 | DAMAGE | 0 |
| 34 | 13 | muro | superada | 9 | superada, reto | 2 | L5-ammo | 2 |  | 0 |
| 35 | 14 | avance | **derrota** | 10 |  | 0 | L5-ammo | 2 | PROCESSOR | 0 |
| 36 | 14 | muro | **derrota** | 10 |  | 0 | L5-ammo | 2 |  | 0 |
| 37 | 9 | repaso | perfecta | 0 | sin fugas | 1 | L5-ammo | 2 |  | 0 |
| 38 | 14 | muro | **derrota** | 10 |  | 1 | L5-ammo | 2 |  | 0 |
| 39 | 9 (×3) | repaso | superada | 6 | reto | 0 | L4 | 2 | PROCESSOR | 0 |
| 40 | 14 | muro | superada | 7 | superada, reto | 2 | L5-ammo | 2 |  | 0 |
| 41 | 15 | avance | **derrota** | 10 |  | 2 | L5-ammo | 2 |  | 0 |
| 42 | 10 | repaso | perfecta | 0 | sin fugas | 0 | L5-ammo | 3 | EXTRA_TURRET | 0 |
| 43 | 15 | muro | superada | 2 | superada | 1 | L5-ammo | 3 |  | 0 |
| 44 | 16 | avance | **derrota** | 10 |  | 1 | L5-ammo | 3 |  | 0 |
| 45 | 12 | repaso | perfecta | 0 | sin fugas | 2 | L5-ammo | 3 |  | 0 |
| 46 | 16 | muro | **derrota** | 10 |  | 2 | L5-ammo | 3 |  | 0 |
| 47 | 12 (×3) | repaso | perfecta | 0 | reto | 0 | L2-ammo-b | 3 | PROCESSOR | 0 |
| 48 | 16 | muro | superada | 7 | superada, reto | 2 | L5-ammo | 3 |  | 0 |

## 4.1 Estrellas por oleada

Lo conseguido al acabar la partida simulada. «Script que cumple el reto» es el primero
que lo cumple con lo que tenía comprado al final (vacío: ninguno de referencia).

| Oleada | Reto | Superada | Sin fugas | Reto cumplido | Script que cumple el reto |
|---|---|---|---|---|---|
| 1 | ningún disparo al aire | ★ | ★ | ★ | `L5-ammo` |
| 2 | sin recargar a mano | ★ | ★ | ★ | `L5-ammo` |
| 3 | como mucho 4 líneas de memoria | ★ | ★ | ★ | `L1` |
| 4 | ningún disparo a enemigos ya muertos | ★ | ★ | ★ | `L5-ammo-b` |
| 5 | como mucho 52 disparos | ★ | ★ | ★ | `L5-ammo-b` |
| 6 | como mucho 10 líneas de memoria | ★ | ★ | ★ | `L2-ammo-b` |
| 7 | como mucho 100 disparos | ★ | ★ | ★ | `L3-ammo-b` |
| 8 | como mucho 90 disparos | ★ | ★ | ★ | `L3-ammo` |
| 9 | como mucho 9 líneas de memoria | ★ | ★ | ★ | `L4` |
| 10 | ninguna excepción | ★ | ★ | ★ | `L5-ammo` |
| 11 | ningún disparo a enemigos ya muertos | ★ | ★ | — | — |
| 12 | como mucho 10 líneas de memoria | ★ | ★ | ★ | `L2-ammo-b` |
| 13 | ninguna excepción | ★ | — | ★ | `L5-ammo` |
| 14 | ninguna excepción | ★ | — | ★ | `L5-ammo` |
| 15 | como mucho 8 líneas de memoria | ★ | — | — | — |
| 16 | ningún disparo al aire | ★ | — | ★ | `L5-ammo` |

Estrellas ganadas: **42 de 48**; sin gastar al final: 2. Trofeos: 1.

Antes de vencer al Jefe 1 gastó **20 estrellas** de las 27 que dan las oleadas 1–9: margen de 7 para quien no las consiga todas.

## 4.2 Sin hacer ningún reto

El mismo jugador, pero sin intentar nunca los retos de código: solo superar oleadas y
repetirlas sin fugas. Mide si los retos hacen falta para llegar al jefe.

Queda **bloqueado** en la oleada 9 con 18 estrellas ganadas: sin retos no se llega al jefe.

## 5. Pasadas por muro

Un muro es una oleada perdida antes de superarla por primera vez. «Pasadas» son las
oleadas anteriores repetidas mientras tanto para ganar estrellas que faltaban. Regla
del plan: como mucho 5.

| Muro (oleada) | Intentos | Pasadas | Superado | Regla de 5 |
|---|---|---|---|---|
| 10 | 3 | 1 | sí | cumple |
| 12 | 2 | 0 | sí | cumple |
| 13 | 9 | 5 | sí | cumple |
| 14 | 4 | 2 | sí | cumple |
| 15 | 2 | 1 | sí | cumple |
| 16 | 3 | 2 | sí | cumple |

## 6. Tiempo hasta el Jefe 1

Tiempo de juego simulado: **9,87 min** (con ×3 en las repeticiones permitidas, sin contar
el tiempo de escribir código). El plan apunta a 20–30 min «por comprobar con
jugadores», incluido el tiempo de escribir, así que este valor es un mínimo.

Hasta superar la oleada 16: **26,54 min**.

## 7. Ritmo de compras de sintaxis

Oleada en la que el jugador de referencia compra cada pieza frente a la oleada en la
que el plan dice que llega («Llega hacia la oleada»).

| Pieza | Precio (estrellas) | Llega (plan) | Comprada en la oleada | Nº de oleada jugada |
|---|---|---|---|---|
| IF_ELSE | 1 | 2–3 | 1 | 1 |
| VARIABLES | 2 | 4–5 | 2 | 3 |
| LOGIC_OPS | 3 | 6–7 | 3 | 4 |
| WHILE | 4 | 8–9 | 7 | 10 |
| FOR | 4 | 11–12 | 7 | 28 |
| METHODS | 6 | 15–16 | nunca | — |

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
| VARIABLES | 4 | `L2` | 0,76 | `L1` | 0,45 | `L1` + MAGAZINE +1 | 0,57 | 1,33 | **no cumple** |
| LOGIC_OPS | 6 | `L3-ammo` | 0,27 | `L2-ammo` | 0,41 | `L2-ammo` + DAMAGE +2 | 0,57 | 0,48 | **no cumple** |
| WHILE | 8 | `L4-ammo-b` | 0,55 | `L3-ammo` | 0,55 | `L3-ammo` + nada | 0,55 | 1 | **no cumple** |
| FOR | 11 | `L5-ammo-b` | 0,57 | `L4-ammo-b` | 0,67 | `L4-ammo-b` + nada | 0,67 | 0,86 | **no cumple** |
| METHODS | 15 | `L6-ammo` | 0,15 | `L5-ammo` | 0,15 | `L5-ammo` + EXTRA_TURRET +1, PROCESSOR +1 | 0,79 | 0,19 | **no cumple** |

### 8.2 Sin topes de hardware (referencia)

Lo que pasaría si el hardware no tuviera topes: mide cuánto aporta la pieza por sí
sola frente al hardware libre.

| Pieza | Oleada | Con la pieza | k | Sin ella ni hardware | k | Sin ella: mejor hardware | k | Proporción | Regla |
|---|---|---|---|---|---|---|---|---|---|
| IF_ELSE | 2 | `L1` | 1,69 | `L0` | 1,27 | `L0` + DAMAGE +1 | 1,59 | 1,07 | **no cumple** |
| VARIABLES | 4 | `L2` | 0,76 | `L1` | 0,45 | `L1` + EXTRA_TURRET +1 | 0,76 | 1 | **no cumple** |
| LOGIC_OPS | 6 | `L3-ammo` | 0,27 | `L2-ammo` | 0,41 | `L2-ammo` + EXTRA_TURRET +1, DAMAGE +1 | 0,66 | 0,42 | **no cumple** |
| WHILE | 8 | `L4-ammo-b` | 0,55 | `L3-ammo` | 0,55 | `L3-ammo` + EXTRA_TURRET +1, DAMAGE +1 | 1,05 | 0,53 | **no cumple** |
| FOR | 11 | `L5-ammo-b` | 0,57 | `L4-ammo` | 0,67 | `L4-ammo` + EXTRA_TURRET +1 | 1,91 | 0,30 | **no cumple** |
| METHODS | 15 | `L6-ammo` | 0,15 | `L5-ammo` | 0,15 | `L5-ammo` + EXTRA_TURRET +1, PROCESSOR +1 | 0,79 | 0,19 | **no cumple** |

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
| MEMORY | 0 | FOR |
| HEAT_SINK | 0 | LOGIC_OPS |
| PROCESSOR | 0 | FOR |
| EXTRA_TURRET | 0 | LOGIC_OPS |
| EXTRA_TURRET | 1 | FOR |

## 10. Contradicciones con el plan

Datos, no ajustes: decidir qué cambiar es de Yeray.

- Regla del doble (con topes): IF_ELSE rinde 1,33 veces lo mismo en hardware en la oleada 2 (el plan pide al menos 2).
- Regla del doble (con topes): VARIABLES rinde 1,33 veces lo mismo en hardware en la oleada 4 (el plan pide al menos 2).
- Regla del doble (con topes): LOGIC_OPS rinde 0,48 veces lo mismo en hardware en la oleada 6 (el plan pide al menos 2).
- Regla del doble (con topes): WHILE rinde 1 veces lo mismo en hardware en la oleada 8 (el plan pide al menos 2).
- Regla del doble (con topes): FOR rinde 0,86 veces lo mismo en hardware en la oleada 11 (el plan pide al menos 2).
- Regla del doble (con topes): METHODS rinde 0,19 veces lo mismo en hardware en la oleada 15 (el plan pide al menos 2).

## 11. Avisos

No contradicen el plan, pero conviene saberlos al decidir.

- El reto de la oleada 11 («ningún disparo a enemigos ya muertos») no lo cumple ningún script de referencia: o es muy difícil o el jugador de referencia no sabe hacerlo.
- El reto de la oleada 15 («como mucho 8 líneas de memoria») no lo cumple ningún script de referencia: o es muy difícil o el jugador de referencia no sabe hacerlo.
- Llegar al Jefe 1 lleva 9,87 min de juego sin contar el tiempo de escribir código; el plan apunta a 20–30 min en total. Sin farmeo (D-57), el resto lo pone escribir y probar el código de los retos: hay que medirlo con jugadores.
- Con 10 vidas en el núcleo, la oleada 1 trae menos enemigos que vidas y no se pueden perder aunque ninguna torreta dispare: ahí no puede haber muros y solo está en juego la estrella sin fugas (y el reto).
- Oleada 1 con `disparar();` y recarga a mano tras 1 s: se cuelan 0 de 8 enemigos y salta 2 veces SinMunicionException. El plan quiere una recarga a mano que «agobie lo justo» y que «no des abasto»; con estos datos la oleada sale perfecta. Lo deciden el tiempo de reacción real, el rango y las velocidades.
