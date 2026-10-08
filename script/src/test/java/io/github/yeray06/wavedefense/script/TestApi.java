package io.github.yeray06.wavedefense.script;

import static io.github.yeray06.wavedefense.script.api.Feature.IF_ELSE;
import static io.github.yeray06.wavedefense.script.api.Feature.VARIABLES;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.ApiFunction;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Requirement;
import io.github.yeray06.wavedefense.script.api.ScriptApi;
import io.github.yeray06.wavedefense.script.api.ScriptApi.FunctionSpec;
import io.github.yeray06.wavedefense.script.api.ScriptLimits;
import java.util.EnumMap;
import java.util.Map;

/**
 * Test fixture with the same vocabulary as {@code assets/data/script-api.json}. The game never uses
 * this: it loads the vocabulary from data (core-sim checks the real file).
 */
final class TestApi {
  static final ScriptLimits LIMITS = new ScriptLimits(1000, 100_000, 64, 64);
  static final int WAVE_FOR_AMMO = 6;

  private TestApi() {}

  static ScriptApi spanish() {
    return new ScriptApi("Enemigo", "Municion", functions(), ammoNames());
  }

  static Map<ApiFunction, FunctionSpec> functions() {
    Map<ApiFunction, FunctionSpec> f = new EnumMap<>(ApiFunction.class);
    f.put(ApiFunction.FIRE, spec("disparar", new Requirement.Free()));
    f.put(ApiFunction.RELOAD, spec("recargar", new Requirement.Free()));
    f.put(ApiFunction.AMMO, spec("municion", IF_ELSE));
    f.put(ApiFunction.HEAT, spec("calor", IF_ELSE));
    f.put(ApiFunction.HAS_ENEMIES, spec("hayEnemigos", IF_ELSE));
    f.put(ApiFunction.NEAREST_ENEMY, spec("enemigoMasCercano", VARIABLES));
    f.put(ApiFunction.TARGET, spec("objetivo", VARIABLES));
    f.put(ApiFunction.ENEMIES, spec("enemigos", Feature.FOR));
    f.put(ApiFunction.AIM, spec("apuntar", VARIABLES));
    f.put(ApiFunction.USE_AMMO, spec("usarMunicion", new Requirement.NeedsWave(WAVE_FOR_AMMO)));
    f.put(ApiFunction.HEALTH, spec("vida", VARIABLES));
    f.put(ApiFunction.SPEED, spec("velocidad", VARIABLES));
    f.put(ApiFunction.ARMORED, spec("blindado", VARIABLES));
    f.put(ApiFunction.DISTANCE_TO_CORE, spec("distanciaAlNucleo", VARIABLES));
    return f;
  }

  static Map<AmmoType, String> ammoNames() {
    Map<AmmoType, String> names = new EnumMap<>(AmmoType.class);
    names.put(AmmoType.NORMAL, "NORMAL");
    names.put(AmmoType.PIERCING, "PERFORANTE");
    return names;
  }

  private static FunctionSpec spec(String name, Feature feature) {
    return new FunctionSpec(name, new Requirement.NeedsFeature(feature));
  }

  private static FunctionSpec spec(String name, Requirement requirement) {
    return new FunctionSpec(name, requirement);
  }
}
