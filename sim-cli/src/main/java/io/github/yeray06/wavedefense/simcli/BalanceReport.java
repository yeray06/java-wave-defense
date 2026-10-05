package io.github.yeray06.wavedefense.simcli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.ShopConfig;
import io.github.yeray06.wavedefense.sim.economy.Formulas;
import io.github.yeray06.wavedefense.sim.economy.Money;
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

  String render(ProgressionRun.Result run, List<DoubleRule.Entry> doubleRule) {
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
    walls(run);
    playTime(run);
    rhythm(run);
    doubleRule(doubleRule);
    unlosableWaves();
    waveOne(run);
    summary();
    return out.toString();
  }

  private void economy() {
    Formulas formulas = new Formulas(data.balance());
    line("## 1. Economía: tabla del plan");
    line("");
    line("Calculada con las fórmulas de `balance.json`. Coincide con la tabla del plan");
    line("(lo comprueba `FormulasTest`).");
    line("");
    line("| Oleada | Enemigos | Vida por enemigo | Dinero por enemigo | Dinero por oleada |");
    line("|---|---|---|---|---|");
    for (int wave : new int[] {1, 5, 10, 20, 30}) {
      line(
          "| %d | %d | %s | %s | %s |",
          wave,
          formulas.enemyCount(wave),
          decimal(formulas.enemyHealth(wave)),
          decimal(Money.units(formulas.rewardPerEnemyCents(wave))),
          decimal(Money.units(formulas.waveRewardCents(wave))));
    }
    line("");
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
    line(
        "- Recarga a mano una torreta vacía %s s después de que se quede sin balas, también si",
        decimal(config.manualReloadReactionSeconds()));
    line("  está colgada (D-03b).");
    line("- Compra la siguiente pieza de sintaxis en cuanto puede pagarla. Atascado tras una");
    line("  derrota, compra lo que pueda tras cada oleada farmeada: sintaxis; si no, la torreta");
    line("  extra; si no, el hardware más barato. Con toda la sintaxis, gasta en hardware igual.");
    line(
        "- Usa ×3 en las oleadas que ya superó sin fugas. El tiempo de escribir código no cuenta.");
    line("");
    line("| Script | Memoria | Código |");
    line("|---|---|---|");
    for (String name : new String[] {"L0", "L1", "L2", "L2-ammo", "L3-ammo", "L4", "L4-ammo"}) {
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
    line(
        "| # | Oleada | Resultado | Fugas | Cobrado | Dinero | Script | Torretas | Compra | Excepciones |");
    line("|---|---|---|---|---|---|---|---|---|---|");
    for (ProgressionRun.Row row : run.rows()) {
      line(
          "| %d | %d%s | %s | %d | %s | %s | %s | %d | %s | %d |",
          row.number(),
          row.wave(),
          row.speedUp() ? " (×3)" : "",
          row.won() ? (row.leaks() == 0 ? "perfecta" : "superada") : "**derrota**",
          row.leaks(),
          decimal(Money.units(row.paidCents())),
          decimal(Money.units(row.moneyAfterCents())),
          row.script(),
          row.turrets(),
          row.bought().isEmpty() ? "" : String.join(", ", row.bought()),
          row.faults());
    }
    line("");
  }

  private void walls(ProgressionRun.Result run) {
    line("## 5. Pasadas por muro");
    line("");
    line("Un muro es una oleada perdida antes de superarla por primera vez. «Pasadas» son las");
    line("oleadas anteriores repetidas mientras tanto. Regla del plan: como mucho %d.", MAX_PASSES);
    line("");
    if (run.walls().isEmpty()) {
      line("No hubo muros: el jugador de referencia no perdió ninguna oleada nueva.");
      line("");
      return;
    }
    line("| Muro (oleada) | Intentos | Pasadas | Superado | Regla de 5 |");
    line("|---|---|---|---|---|");
    for (ProgressionRun.Wall wall : run.walls()) {
      boolean ok = wall.beaten() && wall.farmedWaves() <= MAX_PASSES;
      line(
          "| %d | %d | %d | %s | %s |",
          wall.wave(),
          wall.attempts(),
          wall.farmedWaves(),
          wall.beaten() ? "sí" : "no",
          ok ? "cumple" : "**no cumple**");
      if (!ok) {
        contradictions.add(
            "Muro en la oleada "
                + wall.wave()
                + ": "
                + wall.farmedWaves()
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
    line("| Pieza | Precio | Llega (plan) | Comprada en la oleada | Nº de oleada jugada |");
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
          "| %s | %s | %s | %s | %s |",
          item.feature(),
          decimal(Money.units(shop.priceCents(item.feature()))),
          planRange(item),
          bought == null ? "nunca" : Integer.toString(bought.wave()),
          bought == null ? "—" : Integer.toString(bought.number()));
    }
    line("");
  }

  private static String planRange(ShopConfig.SyntaxItem item) {
    return item.arrivesAroundWave() + "–" + (item.arrivesAroundWave() + 1);
  }

  private void doubleRule(List<DoubleRule.Entry> entries) {
    line("## 8. Regla del doble");
    line("");
    line("`k` = mayor multiplicador de vida de los enemigos con el que el jugador de referencia");
    line(
        "aún supera **sin fugas** la oleada en la que llega la pieza (búsqueda binaria entre %s y"
            + " %s).",
        decimal(config.healthScaleSearch().min()), decimal(config.healthScaleSearch().max()));
    line("Sin fugas y no solo «superada»: con 20 vidas en el núcleo, una oleada de menos de 20");
    line("enemigos no se puede perder y la medida se saturaría (ver «Contradicciones»).");
    line("Se parte del estado justo antes de comprar la pieza y se compara la pieza con su");
    line("script frente al mejor hardware que se compra con el mismo dinero. Cumple si la");
    line("proporción es ≥ 2.");
    line("");
    line(
        "| Pieza | Oleada | Con la pieza | k | Sin ella ni hardware | k | Sin ella: mejor hardware"
            + " | k | Proporción | Regla |");
    line("|---|---|---|---|---|---|---|---|---|---|");
    for (DoubleRule.Entry e : entries) {
      if (!e.evaluated()) {
        line(
            "| %s | %d | — | — | — | — | — | — | — | no evaluada: no se compró |",
            e.feature(), e.wave());
        contradictions.add(
            "Regla del doble de " + e.feature() + " no evaluada: el jugador nunca la compró.");
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
      if (!e.passes()) {
        contradictions.add(
            "Regla del doble: "
                + e.feature()
                + " rinde "
                + decimal(e.ratio())
                + " veces lo mismo en hardware en la oleada "
                + e.wave()
                + " (el plan pide al menos 2).");
      }
    }
    line("");
    line("D-20 pide comprobar Variables contra el Regenerador: es la fila VARIABLES (oleada 4,");
    line("la primera con regeneradores).");
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
              + " vidas en el núcleo, las oleadas "
              + waves.get(0)
              + "–"
              + waves.get(waves.size() - 1)
              + " traen menos enemigos que vidas y no se pueden perder aunque ninguna torreta"
              + " dispare: ahí no puede haber muros y la presión es solo de dinero (menos cobro y"
              + " sin oleada perfecta ni ×3).");
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
    line("## 9. Contradicciones con el plan");
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
    line("## 10. Avisos");
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
