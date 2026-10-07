package io.github.yeray06.wavedefense.simcli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.data.Challenge;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.ShopConfig;
import io.github.yeray06.wavedefense.sim.data.WaveTable;
import io.github.yeray06.wavedefense.sim.economy.Formulas;
import io.github.yeray06.wavedefense.sim.progress.Goal;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.Shop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes {@code docs/BALANCE.md}. A developer document, so its text is Spanish prose in code; no
 * player ever sees it. Output is deterministic: no dates, no timings of this machine.
 */
final class BalanceReport {
  /** The plan's calibration rule: "ningún muro debe pedir más de 5 pasadas". */
  static final int MAX_PASSES = 5;

  /** Plan, "Duración objetivo": Jefe 1 in 20 to 30 minutes. */
  static final double TARGET_MIN_MINUTES = 20;

  static final double TARGET_MAX_MINUTES = 30;

  private static final String[] DATA_FILES = {
    GameData.BALANCE,
    GameData.TURRET,
    GameData.ENEMIES,
    GameData.WAVES,
    GameData.SHOP,
    GameData.MAP,
    "data/script-api.json"
  };

  private final GameData data;
  private final Path assets;
  private final PlayerConfig config;
  private final ReferencePlayer player;
  private final StringBuilder out = new StringBuilder();
  private final List<String> contradictions = new ArrayList<>();
  private final List<String> warnings = new ArrayList<>();

  BalanceReport(GameData data, Path assets, PlayerConfig config, ReferencePlayer player) {
    this.data = data;
    this.assets = assets;
    this.config = config;
    this.player = player;
  }

  /** Problems found against the plan, for the summary and the exit code. */
  List<String> contradictions() {
    return contradictions;
  }

  String render(
      ProgressionRun.Result run,
      ProgressionRun.Result withoutChallenges,
      List<DoubleRule.Entry> doubleRule,
      List<DoubleRule.Entry> doubleRuleWithoutCaps) {
    line("# Balance del MVP (oleadas 1–10)");
    line("");
    line("Generado por `sim-cli` con `./gradlew :sim-cli:run --args=\"--write docs/BALANCE.md\"`.");
    line("No editar a mano: se regenera con los datos de `assets/data/`. Los números que **no");
    line("vienen del plan** están marcados; el plan sigue siendo la fuente de verdad y aquí no se");
    line("ajusta nada: si algo no cuadra, sale en «Contradicciones con el plan».");
    line("");
    economy();
    notFromPlan();
    referencePlayer(run);
    progression(run);
    stars(run);
    withoutChallenges(withoutChallenges);
    walls(run);
    playTime(run);
    rhythm(run);
    doubleRule(doubleRule, doubleRuleWithoutCaps);
    hardwareCaps();
    unlosableWaves();
    waveOne(run);
    summary();
    return out.toString();
  }

  private void economy() {
    Formulas formulas = new Formulas(data.balance());
    line("## 1. Enemigos y estrellas");
    line("");
    line("Enemigos y vida, calculados con las fórmulas de `balance.json`. Coinciden con la tabla");
    line("del plan (lo comprueba `FormulasTest`).");
    line("");
    line("| Oleada | Enemigos | Vida por enemigo |");
    line("|---|---|---|");
    for (int wave : new int[] {1, 5, 10, 20, 30}) {
      line(
          "| %d | %d | %s |", wave, formulas.enemyCount(wave), decimal(formulas.enemyHealth(wave)));
    }
    line("");
    int lastWave = data.balance().lastWave();
    int syntax =
        data.shop().syntax().stream()
            .filter(ShopConfig.SyntaxItem::inMvp)
            .mapToInt(ShopConfig.SyntaxItem::price)
            .sum();
    int hardware =
        data.shop().hardware().stream()
            .filter(ShopConfig.HardwareItem::inMvp)
            .flatMap(h -> h.prices().stream())
            .mapToInt(Integer::intValue)
            .sum();
    line("Economía de estrellas (D-57, sustituye al «Farmeo» del plan): cada oleada da hasta 3");
    line("estrellas y cada una se cobra una sola vez (superarla, sin fugas y su reto). Repetir no");
    line("paga. Cada jefe da un trofeo la primera vez que cae.");
    line("");
    line("| Concepto | Estrellas |");
    line("|---|---|");
    line("| Al empezar | %d |", data.balance().startingStars());
    line("| Máximo en las oleadas 1–%d | %d |", lastWave, 3 * lastWave);
    line("| Toda la sintaxis del MVP | %d |", syntax);
    line("| Todo el hardware del MVP | %d |", hardware);
    line("");
    if (syntax > data.balance().startingStars() + 3 * lastWave) {
      contradictions.add(
          "La sintaxis del MVP cuesta "
              + syntax
              + " estrellas y solo se pueden ganar "
              + (data.balance().startingStars() + 3 * lastWave)
              + ".");
    }
  }

  private void notFromPlan() {
    line("## 2. Números que no vienen del plan");
    line("");
    line("Valores provisionales (D-12 y siguientes) que el plan no fija. Salen de la lista");
    line("`notFromPlan` de cada fichero.");
    line("");
    line("| Fichero | Campo | Valor |");
    line("|---|---|---|");
    ObjectMapper mapper = new ObjectMapper();
    for (String file : DATA_FILES) {
      try {
        JsonNode root = mapper.readTree(Files.readString(assets.resolve(file)));
        for (JsonNode field : root.path("notFromPlan")) {
          String path = field.asText();
          JsonNode value = root.at("/" + path.replace('.', '/'));
          line("| `%s` | `%s` | %s |", file, path, compact(value));
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    line(
        "| `sim-cli` (jugador) | `manualReloadReactionSeconds` | %s |",
        decimal(config.manualReloadReactionSeconds()));
    line("");
  }

  private void referencePlayer(ProgressionRun.Result run) {
    line("## 3. Jugador de referencia");
    line("");
    line("- Escribe el mejor script de referencia que le permite su sintaxis");
    line("  (`sim-cli/src/main/resources/reference/scripts/`). Con la oleada 6 alcanzada usa");
    line("  `usarMunicion` si su sintaxis cabe en la memoria.");
    line("- Las variantes `-b` y `-c` dejan fuera otra cosa para caber en 12 líneas. En la");
    line("  partida usa la primera; en la regla del doble prueba todas y se queda la mejor.");
    line(
        "- Recarga a mano una torreta vacía %s s después de que se quede sin balas, también si",
        decimal(config.manualReloadReactionSeconds()));
    line("  está colgada (D-03b).");
    line("- Si supera una oleada sin cumplir su reto y alguno de los scripts que sabe escribir");
    line("  la supera cumpliéndolo, la repite con ese script. Sabe de antemano qué script cumple:");
    line("  es la cota alta de alguien que lee el reto y lo intenta.");
    line("- Compra la siguiente pieza de sintaxis en cuanto puede pagarla; con toda la sintaxis,");
    line("  gasta en hardware. Atascado tras una derrota: sintaxis si llega; si no, la torreta");
    line("  extra; si no, el hardware más barato, y vuelve a intentarlo. Sin nada que comprar,");
    line("  repite la primera oleada anterior que aún le dé una estrella (sin fugas o su reto).");
    line("  Si no queda ninguna, la partida está **bloqueada**.");
    line(
        "- Usa ×3 en las oleadas que ya superó sin fugas. El tiempo de escribir código no cuenta.");
    line("");
    line("| Script | Memoria | Código |");
    line("|---|---|---|");
    for (String name :
        new String[] {
          "L0",
          "L1",
          "L2",
          "L2-ammo",
          "L2-ammo-b",
          "L2-ammo-c",
          "L3-ammo",
          "L3-ammo-b",
          "L4",
          "L4-ammo",
          "L4-ammo-b"
        }) {
      String source = ReferencePlayer.resource(name);
      int memory =
          data.compiler()
              .compile(
                  source,
                  io.github.yeray06.wavedefense.script.api.Unlocks.of(10, Feature.values()),
                  1000)
              .memoryUsed();
      line(
          "| `%s` | %d/%d | `%s` |",
          name,
          memory,
          data.turret().memoryLines(),
          source.strip().replaceAll("\\s+", " ").replace("|", "\\|"));
    }
    line("");
  }

  private void progression(ProgressionRun.Result run) {
    line("## 4. Partida simulada");
    line("");
    if (run.bossDefeated()) {
      line(
          "El jugador de referencia derrota al Jefe 1 tras **%d oleadas jugadas**.",
          run.rows().size());
    } else if (run.lockedAt() != null) {
      line(
          "La partida queda **bloqueada** en la oleada %d: no puede comprar nada ni ganar más"
              + " estrellas.",
          run.lockedAt());
      contradictions.add(
          "Partida bloqueada en la oleada "
              + run.lockedAt()
              + ": sin estrellas para comprar y ninguna oleada anterior que dé más.");
    } else {
      line(
          "El jugador de referencia **no derrota al Jefe 1** en %d oleadas jugadas.",
          run.rows().size());
      contradictions.add(
          "El jugador de referencia no llega a derrotar al Jefe 1 en "
              + run.rows().size()
              + " oleadas.");
    }
    line("");
    line("Para: avance, reto (repite por el reto), muro (reintento) o repaso (atascado, a por");
    line("una estrella que falta).");
    line("");
    line(
        "| # | Oleada | Para | Resultado | Fugas | Gana | Estrellas | Script | Torretas | Compra |"
            + " Excepciones |");
    line("|---|---|---|---|---|---|---|---|---|---|---|");
    for (ProgressionRun.Row row : run.rows()) {
      line(
          "| %d | %d%s | %s | %s | %d | %s | %d | %s | %d | %s | %d |",
          row.number(),
          row.wave(),
          row.speedUp() ? " (×3)" : "",
          purpose(row.purpose()),
          row.won() ? (row.leaks() == 0 ? "perfecta" : "superada") : "**derrota**",
          row.leaks(),
          goals(row.earned()),
          row.starsAfter(),
          row.script(),
          row.turrets(),
          row.bought().isEmpty() ? "" : String.join(", ", row.bought()),
          row.faults());
    }
    line("");
  }

  private static String purpose(ProgressionRun.Purpose purpose) {
    return switch (purpose) {
      case ADVANCE -> "avance";
      case CHALLENGE -> "reto";
      case WALL -> "muro";
      case REVISIT -> "repaso";
    };
  }

  private static String goals(List<Goal> goals) {
    List<String> names = new ArrayList<>();
    for (Goal goal : goals) {
      names.add(
          switch (goal) {
            case CLEAR -> "superada";
            case PERFECT -> "sin fugas";
            case CHALLENGE -> "reto";
          });
    }
    return String.join(", ", names);
  }

  private void stars(ProgressionRun.Result run) {
    line("## 4.1 Estrellas por oleada");
    line("");
    line("Lo conseguido al acabar la partida simulada. «Script que cumple el reto» es el primero");
    line("que lo cumple con lo que tenía comprado al final (vacío: ninguno de referencia).");
    line("");
    line("| Oleada | Reto | Superada | Sin fugas | Reto cumplido | Script que cumple el reto |");
    line("|---|---|---|---|---|---|");
    Progress end = run.finalProgress();
    for (WaveTable.WaveSpec spec : data.waves().waves()) {
      int wave = spec.wave();
      String script = run.challengeAtTheEnd().getOrDefault(wave, "");
      line(
          "| %d | %s | %s | %s | %s | %s |",
          wave,
          describe(spec.challenge()),
          end.met(wave, Goal.CLEAR) ? "★" : "—",
          end.met(wave, Goal.PERFECT) ? "★" : "—",
          end.met(wave, Goal.CHALLENGE) ? "★" : "—",
          script.isEmpty() ? "—" : "`" + script + "`");
      if (script.isEmpty()) {
        warnings.add(
            "El reto de la oleada "
                + wave
                + " («"
                + describe(spec.challenge())
                + "») no lo cumple ningún script de referencia: o es muy difícil o el jugador de"
                + " referencia no sabe hacerlo.");
      }
    }
    line("");
    line(
        "Estrellas ganadas: **%d de %d**; sin gastar al final: %d. Trofeos: %d.",
        end.starsEarned(), 3 * data.balance().lastWave(), end.stars(), end.trophies());
    line("");
    if (run.bossDefeated()) {
      int before = 3 * (data.balance().lastWave() - 1);
      line(
          "Antes de vencer al Jefe 1 gastó **%d estrellas** de las %d que dan las oleadas 1–%d:"
              + " margen de %d para quien no las consiga todas.",
          run.starsSpentBeforeTheBoss(),
          before,
          data.balance().lastWave() - 1,
          before - run.starsSpentBeforeTheBoss());
      line("");
    }
  }

  private void withoutChallenges(ProgressionRun.Result run) {
    line("## 4.2 Sin hacer ningún reto");
    line("");
    line("El mismo jugador, pero sin intentar nunca los retos de código: solo superar oleadas y");
    line("repetirlas sin fugas. Mide si los retos hacen falta para llegar al jefe.");
    line("");
    if (run.bossDefeated()) {
      line(
          "Derrota al Jefe 1 tras %d oleadas jugadas, con %d estrellas ganadas.",
          run.rows().size(), run.finalProgress().starsEarned());
    } else if (run.lockedAt() != null) {
      line(
          "Queda **bloqueado** en la oleada %d con %d estrellas ganadas: sin retos no se llega"
              + " al jefe.",
          run.lockedAt(), run.finalProgress().starsEarned());
    } else {
      line(
          "No derrota al Jefe 1 en %d oleadas jugadas (%d estrellas).",
          run.rows().size(), run.finalProgress().starsEarned());
    }
    line("");
  }

  static String describe(Challenge challenge) {
    return switch (challenge.type()) {
      case NO_AIR_SHOTS -> "ningún disparo al aire";
      case NO_FAULTS -> "ninguna excepción";
      case NO_MANUAL_RELOADS -> "sin recargar a mano";
      case MAX_MEMORY -> "como mucho " + challenge.limit() + " líneas de memoria";
      case MAX_SHOTS -> "como mucho " + challenge.limit() + " disparos";
      case NO_WASTED_SHOTS -> "ningún disparo a enemigos ya muertos";
    };
  }

  private void walls(ProgressionRun.Result run) {
    line("## 5. Pasadas por muro");
    line("");
    line("Un muro es una oleada perdida antes de superarla por primera vez. «Pasadas» son las");
    line("oleadas anteriores repetidas mientras tanto para ganar estrellas que faltaban. Regla");
    line("del plan: como mucho %d.", MAX_PASSES);
    line("");
    if (run.walls().isEmpty()) {
      line("No hubo muros: el jugador de referencia no perdió ninguna oleada nueva.");
      line("");
      return;
    }
    line("| Muro (oleada) | Intentos | Pasadas | Superado | Regla de 5 |");
    line("|---|---|---|---|---|");
    for (ProgressionRun.Wall wall : run.walls()) {
      boolean ok = wall.beaten() && wall.passes() <= MAX_PASSES;
      line(
          "| %d | %d | %d | %s | %s |",
          wall.wave(),
          wall.attempts(),
          wall.passes(),
          wall.beaten() ? "sí" : "no",
          ok ? "cumple" : "**no cumple**");
      if (!ok) {
        contradictions.add(
            "Muro en la oleada "
                + wall.wave()
                + ": "
                + wall.passes()
                + " pasadas"
                + (wall.beaten() ? "" : " sin llegar a superarlo")
                + " (el plan pide como mucho "
                + MAX_PASSES
                + ").");
      }
    }
    line("");
  }

  private void playTime(ProgressionRun.Result run) {
    double minutes = run.playSeconds() / 60;
    line("## 6. Tiempo hasta el Jefe 1");
    line("");
    line(
        "Tiempo de juego simulado: **%s min** (con ×3 en las repeticiones permitidas, sin contar",
        decimal(minutes));
    line("el tiempo de escribir código). El plan apunta a 20–30 min «por comprobar con");
    line("jugadores», incluido el tiempo de escribir, así que este valor es un mínimo.");
    line("");
    if (run.bossDefeated() && minutes < TARGET_MIN_MINUTES) {
      warnings.add(
          "Llegar al Jefe 1 lleva "
              + decimal(minutes)
              + " min de juego sin contar el tiempo de escribir código; el plan apunta a 20–30 min"
              + " en total. Sin farmeo (D-57), el resto lo pone escribir y probar el código de los"
              + " retos: hay que medirlo con jugadores.");
    }
    if (run.bossDefeated() && minutes > TARGET_MAX_MINUTES) {
      contradictions.add(
          "Llegar al Jefe 1 lleva "
              + decimal(minutes)
              + " min de juego sin contar el tiempo de escribir; el plan apunta a 20–30 min en total.");
    }
  }

  private void rhythm(ProgressionRun.Result run) {
    line("## 7. Ritmo de compras de sintaxis");
    line("");
    line("Oleada en la que el jugador de referencia compra cada pieza frente a la oleada en la");
    line("que el plan dice que llega («Llega hacia la oleada»).");
    line("");
    line(
        "| Pieza | Precio (estrellas) | Llega (plan) | Comprada en la oleada | Nº de oleada jugada |");
    line("|---|---|---|---|---|");
    Shop shop = new Shop(data);
    for (ShopConfig.SyntaxItem item : data.shop().syntax()) {
      if (!item.inMvp()) {
        continue;
      }
      ProgressionRun.Row bought =
          run.rows().stream()
              .filter(r -> r.bought().contains(item.feature().name()))
              .findFirst()
              .orElse(null);
      line(
          "| %s | %d | %s | %s | %s |",
          item.feature(),
          shop.price(item.feature()),
          planRange(item),
          bought == null ? "nunca" : Integer.toString(bought.wave()),
          bought == null ? "—" : Integer.toString(bought.number()));
    }
    line("");
  }

  private static String planRange(ShopConfig.SyntaxItem item) {
    return item.arrivesAroundWave() + "–" + (item.arrivesAroundWave() + 1);
  }

  private void doubleRule(List<DoubleRule.Entry> capped, List<DoubleRule.Entry> uncapped) {
    line("## 8. Regla del doble");
    line("");
    line("`k` = mayor multiplicador de vida de los enemigos con el que el jugador de referencia");
    line(
        "aún supera **sin fugas** la oleada en la que llega la pieza (búsqueda binaria entre %s y"
            + " %s).",
        decimal(config.healthScaleSearch().min()), decimal(config.healthScaleSearch().max()));
    line("Sin fugas y no solo «superada»: si una oleada trae menos enemigos que vidas tiene el");
    line("núcleo, no se puede perder y la medida se saturaría.");
    line("Se parte del estado justo antes de comprar la pieza y se compara la pieza con su");
    line("script frente al mejor hardware que se compra con las mismas estrellas. En cada lado se");
    line("prueban todas las variantes de script que caben en memoria y se toma la mejor.");
    line("Cumple si la proporción es ≥ 2.");
    line("");
    line("### 8.1 Con los topes de hardware del juego (sección 9)");
    line("");
    doubleRuleTable(capped, true);
    line("### 8.2 Sin topes de hardware (referencia)");
    line("");
    line("Lo que pasaría si el hardware no tuviera topes: mide cuánto aporta la pieza por sí");
    line("sola frente al hardware libre.");
    line("");
    doubleRuleTable(uncapped, false);
    line("D-20 pide comprobar Variables contra el Regenerador: es la fila VARIABLES (oleada 4,");
    line("la primera con regeneradores).");
    line("");
  }

  private void doubleRuleTable(List<DoubleRule.Entry> entries, boolean countsForThePlan) {
    line(
        "| Pieza | Oleada | Con la pieza | k | Sin ella ni hardware | k | Sin ella: mejor hardware"
            + " | k | Proporción | Regla |");
    line("|---|---|---|---|---|---|---|---|---|---|");
    for (DoubleRule.Entry e : entries) {
      if (!e.evaluated()) {
        line(
            "| %s | %d | — | — | — | — | — | — | — | no evaluada: no se compró |",
            e.feature(), e.wave());
        if (countsForThePlan) {
          contradictions.add(
              "Regla del doble de " + e.feature() + " no evaluada: el jugador nunca la compró.");
        }
        continue;
      }
      line(
          "| %s | %d | `%s` | %s | `%s` | %s | `%s` + %s | %s | %s | %s |",
          e.feature(),
          e.wave(),
          e.scriptWith(),
          decimal(e.kWith()),
          e.scriptWithout(),
          decimal(e.kNeither()),
          e.scriptWithout(),
          e.bestHardware(),
          decimal(e.kWithout()),
          Double.isInfinite(e.ratio()) ? "∞" : decimal(e.ratio()),
          e.passes() ? "cumple" : "**no cumple**");
      if (countsForThePlan && !e.passes()) {
        contradictions.add(
            "Regla del doble (con topes): "
                + e.feature()
                + " rinde "
                + decimal(e.ratio())
                + " veces lo mismo en hardware en la oleada "
                + e.wave()
                + " (el plan pide al menos 2).");
      }
    }
    line("");
  }

  private void hardwareCaps() {
    line("## 9. Topes de hardware según la sintaxis");
    line("");
    line("Red de seguridad aprobada como añadido al plan: un nivel de hardware no se puede");
    line("comprar hasta tener la pieza indicada. En la tienda: «Necesitas variables para");
    line("seguir mejorando el daño». Están en `shop.json` (`caps`).");
    line("");
    line("| Hardware | Nivel máximo | Hasta comprar |");
    line("|---|---|---|");
    boolean any = false;
    for (ShopConfig.HardwareItem item : data.shop().hardware()) {
      if (!item.inMvp()) {
        continue;
      }
      for (ShopConfig.LevelCap cap : item.caps()) {
        any = true;
        line("| %s | %d | %s |", item.kind(), cap.maxLevel(), cap.untilFeature());
      }
    }
    if (!any) {
      line("| — | — | sin topes |");
    }
    line("");
  }

  /** Waves with fewer enemies than core lives and no boss cannot be lost, whatever the code. */
  private void unlosableWaves() {
    List<Integer> waves = new ArrayList<>();
    for (var spec : data.waves().waves()) {
      int enemies = spec.groups().stream().mapToInt(g -> g.count()).sum();
      if (!spec.hasBoss() && enemies < data.balance().coreLivesPerWave()) {
        waves.add(spec.wave());
      }
    }
    if (!waves.isEmpty()) {
      warnings.add(
          "Con "
              + data.balance().coreLivesPerWave()
              + " vidas en el núcleo, "
              + (waves.size() == 1
                  ? "la oleada " + waves.get(0) + " trae"
                  : "las oleadas " + waves.get(0) + "–" + waves.get(waves.size() - 1) + " traen")
              + " menos enemigos que vidas y no se pueden perder aunque ninguna torreta"
              + " dispare: ahí no puede haber muros y solo está en juego la estrella sin fugas"
              + " (y el reto).");
    }
  }

  /** Plan: "En la oleada 1 recargas a mano y no das abasto". */
  private void waveOne(ProgressionRun.Result run) {
    run.rows().stream()
        .filter(r -> r.wave() == 1)
        .findFirst()
        .ifPresent(
            r ->
                warnings.add(
                    "Oleada 1 con `disparar();` y recarga a mano tras "
                        + decimal(config.manualReloadReactionSeconds())
                        + " s: se cuelan "
                        + r.leaks()
                        + " de "
                        + new Formulas(data.balance()).enemyCount(1)
                        + " enemigos y salta "
                        + r.faults()
                        + " veces SinMunicionException. El plan quiere una recarga a mano que"
                        + " «agobie lo justo» y que «no des abasto»; con estos datos la oleada "
                        + (r.leaks() == 0 ? "sale perfecta" : "se supera con fugas")
                        + ". Lo deciden el tiempo de reacción real, el rango y las velocidades."));
  }

  private void summary() {
    line("## 10. Contradicciones con el plan");
    line("");
    if (contradictions.isEmpty()) {
      line("Ninguna: con los datos actuales la simulación cumple las reglas del plan.");
    } else {
      line("Datos, no ajustes: decidir qué cambiar es de Yeray.");
      line("");
      for (String c : contradictions) {
        line("- " + c);
      }
    }
    line("");
    line("## 11. Avisos");
    line("");
    line("No contradicen el plan, pero conviene saberlos al decidir.");
    line("");
    for (String w : warnings) {
      line("- " + w);
    }
  }

  private void line(String format, Object... args) {
    out.append(args.length == 0 ? format : String.format(Locale.ROOT, format, args)).append('\n');
  }

  private static String decimal(double value) {
    if (value == Math.rint(value) && Math.abs(value) < 1e15) {
      return String.format(Locale.ROOT, "%d", (long) value);
    }
    return String.format(Locale.ROOT, "%.2f", value).replace('.', ',');
  }

  private static String compact(JsonNode value) {
    String text = value.isMissingNode() ? "(no encontrado)" : value.toString();
    return text.length() > 120 ? text.substring(0, 117) + "…" : text.replace("|", "\\|");
  }
}
