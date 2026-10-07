package io.github.yeray06.wavedefense.game.i18n;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.ApiFunction;
import io.github.yeray06.wavedefense.script.api.Requirement;
import io.github.yeray06.wavedefense.sim.data.EnemyCatalog;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.data.ShopConfig;
import io.github.yeray06.wavedefense.sim.world.PathTrack;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The numbers the manual quotes, by name, from the data files: the texts write {@code {cargador}}
 * and never repeat a balance number (CLAUDE.md). Named instead of {@code {0}} because a page quotes
 * many, and because code in a text would trip {@code MessageFormat} over its braces.
 */
public final class ManualFacts {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_.]+)}");
  private static final Locale SPANISH = Locale.of("es");

  private final Map<String, String> facts = new HashMap<>();

  public ManualFacts(GameData data) {
    var turret = data.turret();
    var balance = data.balance();
    put("ciclos", turret.cyclesPerSecond());
    put("cargador", turret.magazine());
    put("calorNormal", turret.heatPerShot().get(AmmoType.NORMAL));
    put("calorPerforante", turret.heatPerShot().get(AmmoType.PIERCING));
    put("disipacion", turret.heatDissipationPerCycle());
    put("calorMax", turret.maxHeat());
    put("ciclosRecarga", turret.reloadCycles());
    put("cuelgue", turret.hangSeconds());
    put("memoria", turret.memoryLines());
    put("dano", turret.bulletDamage());
    put("alcance", Math.round(turret.rangeUnits()));
    put("vueltas", data.scriptApi().limits().maxLoopIterations());
    put("vidas", balance.coreLivesPerWave());
    put("perfecta", balance.perfectWaveBonus() * 100);
    put("oleadas", balance.lastWave());
    put("velocidadMax", Collections.max(balance.speeds()));
    put("camino", Math.round(new PathTrack(data.map().path()).length()));
    put("blindaje", data.enemies().armorDamageReduction() * 100);
    if (data.scriptApi().api().requirement(ApiFunction.USE_AMMO)
        instanceof Requirement.NeedsWave wave) {
      put("oleadaMunicion", wave.wave());
    }
    for (ShopConfig.HardwareItem item : data.shop().hardware()) {
      double effect = item.effectPerLevel() * (item.kind() == HardwareKind.DAMAGE ? 100 : 1);
      put("nivel." + item.kind().name(), effect);
    }
    for (EnemyKind kind : EnemyKind.values()) {
      EnemyCatalog.EnemyType type = data.enemies().type(kind);
      put("velocidad." + kind.name(), type.speed());
      put("vida." + kind.name(), type.healthMultiplier());
      put("oleada." + kind.name(), data.waves().firstWave(kind));
    }
    EnemyCatalog.EnemyType regenerator = data.enemies().type(EnemyKind.REGENERATOR);
    put("regenEspera", regenerator.regenDelaySeconds());
    put("regenRitmo", regenerator.regenFractionPerSecond() * 100);
    put("enjambre", data.enemies().type(EnemyKind.SWARM).groupSize());
    put("jefeBlindaje", data.enemies().type(EnemyKind.BOSS).armorToggleSeconds());
  }

  /** Replaces every {@code {name}} with its number. */
  public String fill(String text) {
    Matcher matcher = PLACEHOLDER.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String value = facts.get(matcher.group(1));
      if (value == null) {
        throw new IllegalArgumentException("Unknown manual fact: " + matcher.group(1));
      }
      matcher.appendReplacement(out, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  /** Whether {@code name} is a fact the texts may quote. */
  public boolean has(String name) {
    return facts.containsKey(name);
  }

  /** Placeholders in {@code text}, for the tests. */
  public static List<String> placeholders(String text) {
    return PLACEHOLDER.matcher(text).results().map(r -> r.group(1)).toList();
  }

  /** Spanish style: "3" for 3.0, "0,5" for 0.5. */
  private void put(String name, double value) {
    NumberFormat format = NumberFormat.getNumberInstance(SPANISH);
    format.setMaximumFractionDigits(2);
    facts.put(name, format.format(value));
  }
}
