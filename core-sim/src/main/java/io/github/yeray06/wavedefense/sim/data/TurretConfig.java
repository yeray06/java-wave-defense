package io.github.yeray06.wavedefense.sim.data;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** {@code data/turret.json}: base stats of every turret, before hardware. */
public record TurretConfig(
    List<String> notFromPlan,
    int cyclesPerSecond,
    int magazine,
    int memoryLines,
    double bulletDamage,
    Map<AmmoType, Integer> heatPerShot,
    int heatDissipationPerCycle,
    int maxHeat,
    int reloadCycles,
    double hangSeconds,
    double rangeUnits,
    double bulletSpeedUnitsPerSecond,
    DefaultTarget defaultTarget,
    String initialScript) {

  public TurretConfig {
    notFromPlan = List.copyOf(Check.present("notFromPlan", notFromPlan));
    Check.positive("cyclesPerSecond", cyclesPerSecond);
    Check.positive("magazine", magazine);
    Check.positive("memoryLines", memoryLines);
    Check.positive("bulletDamage", bulletDamage);
    Check.present("heatPerShot", heatPerShot);
    for (AmmoType type : AmmoType.values()) {
      Check.positive(
          "heatPerShot." + type, Check.present("heatPerShot." + type, heatPerShot.get(type)));
    }
    heatPerShot = Map.copyOf(new EnumMap<>(heatPerShot));
    Check.atLeastZero("heatDissipationPerCycle", heatDissipationPerCycle);
    Check.positive("maxHeat", maxHeat);
    Check.atLeastZero("reloadCycles", reloadCycles);
    Check.atLeastZero("hangSeconds", hangSeconds);
    Check.positive("rangeUnits", rangeUnits);
    Check.positive("bulletSpeedUnitsPerSecond", bulletSpeedUnitsPerSecond);
    Check.present("defaultTarget", defaultTarget);
    Check.present("initialScript", initialScript);
  }
}
