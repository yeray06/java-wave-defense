# Progreso

## Fases

| Fase | Estado | Rama |
|---|---|---|
| 0. Propuesta: árbol, decisiones y dudas | ✅ Hecha, pendiente de revisión | `phase-0-propuesta` |
| 1. Esqueleto Gradle, lanzador y CI | ⏳ Siguiente (bloqueada por D-15) | — |
| 2. Intérprete con tests | Pendiente (necesita D-06, D-07, D-08, D-20) | — |
| 3. core-sim y sim-cli, `docs/BALANCE.md` | Pendiente (necesita D-01, D-03, D-04, D-12) | — |
| 4. Juego: camino, torretas, enemigos, bucle | Solo con Yeray en casa | — |
| 5. Editor, tienda, parte de oleada, informe | Solo con Yeray en casa | — |
| 6. Guardado, ajustes, robustez | Solo con Yeray en casa | — |
| 7. Empaquetado y documentación | Solo con Yeray en casa | — |

## Fase 0 (hecha)

- Plan movido a `docs/plan-diseno.pdf` y leído entero.
- `docs/ARCHITECTURE.md`: stack verificado, árbol de carpetas, módulos,
  diseño del intérprete, simulación, guardado, CI, reglas del juego con su
  origen y 20 dudas abiertas (D-01 … D-20).
- `CLAUDE.md` con las reglas permanentes.
- `.gitignore`.

## Siguiente: fase 1

Antes de empezar necesito:
1. **D-15**: permitir en el acceso de red del entorno `api.foojay.io`,
   `api.adoptium.net`, `github.com`, `objects.githubusercontent.com` y
   `release-assets.githubusercontent.com` (descarga del JDK 25). Maven Central
   y el portal de plugins de Gradle ya funcionan.
2. **D-16** (JUnit 5 o 6) y **D-18** (nombre interno).

## Pendiente de probar en local

Nada todavía (la fase 0 no tiene código).
