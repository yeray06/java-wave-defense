# Progreso

## Fases

| Fase | Estado | Rama |
|---|---|---|
| 0. Propuesta: árbol, decisiones y dudas | ✅ Hecha, dudas respondidas | `phase-0-propuesta` |
| 1. Esqueleto Gradle, lanzador y CI | ⏳ Siguiente | — |
| 2. Intérprete con tests | Pendiente | — |
| 3. core-sim y sim-cli, `docs/BALANCE.md` | Pendiente | — |
| 4. Juego: camino, torretas, enemigos, bucle | Solo con Yeray en casa | — |
| 5. Editor, tienda, parte de oleada, informe | Solo con Yeray en casa | — |
| 6. Guardado, ajustes, robustez | Solo con Yeray en casa | — |
| 7. Empaquetado y documentación | Solo con Yeray en casa | — |

## Fase 0 (hecha)

- Plan movido a `docs/plan-diseno.pdf` y leído entero.
- `docs/ARCHITECTURE.md`: stack, árbol de carpetas, módulos, diseño del
  intérprete, simulación, guardado, CI, reglas del juego con su origen y las
  decisiones D-01 … D-22 (sección 12).
- `CLAUDE.md` con las reglas permanentes.
- `.gitignore`.

## Siguiente: fase 1

Esqueleto Gradle (Kotlin DSL, JDK 21, versión de Java en un único sitio),
lanzador LWJGL3 y CI. Nada bloquea: Maven Central y el portal de plugins
responden desde la sesión.

## Decisiones por revisar

Propuestas mías aplicadas como PROVISIONAL (detalle en
`docs/ARCHITECTURE.md`, sección 12). Ninguna contradice el plan.

- **D-01b**: con avance automático encendido, una oleada ya superada se
  encadena sin pausa.
- **D-02**: avance automático encendido en partida nueva.
- **D-03 (recarga a mano)**: igual que `recargar()`; no se puede con la
  torreta colgada.
- **D-05**: `disparar()` sin enemigos en rango gasta bala y calor; «más
  cercano» = a la torreta.
- **D-09**: perforante solo atraviesa blindaje.
- **D-10**: jefe paga 549 el primer derribo; después, como 20 enemigos
  normales de la oleada 10.
- **D-11**: dinero en centésimas; la UI muestra enteros hacia abajo.
- **D-13**: hardware aditivo y global.
- **D-14**: cada torreta empieza con `disparar();`.
- **D-17**: regla del doble medida con el factor de vida `k`.
- **D-18**: id interno `java-wave-defense` y paquete
  `io.github.yeray06.wavedefense`.
- **D-21**: con Variables también `--`, `-=`, `*=`, `/=`, `%=`.
- **D-22**: como Variables no trae `double`, `e.vida()`, `e.velocidad()` y
  `e.distanciaAlNucleo()` devuelven `int` (vida redondeada hacia arriba).

Números de balance que no vienen del plan (D-12): ver
`docs/ARCHITECTURE.md` 10.1 y 10.2; irán marcados en JSON y en
`docs/BALANCE.md`.

## Pendiente de probar en local

Nada todavía (la fase 0 no tiene código).
