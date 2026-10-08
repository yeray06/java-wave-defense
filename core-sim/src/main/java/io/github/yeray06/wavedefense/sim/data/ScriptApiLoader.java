package io.github.yeray06.wavedefense.sim.data;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.ApiFunction;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Requirement;
import io.github.yeray06.wavedefense.script.api.ScriptApi;
import io.github.yeray06.wavedefense.script.api.ScriptLimits;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Reads {@code data/script-api.json}: the player-facing names of the API and the limits. */
public final class ScriptApiLoader {
  public static final String FILE = "data/script-api.json";

  /** What the file describes. */
  public record ScriptApiData(ScriptApi api, ScriptLimits limits) {}

  record FileContents(
      List<String> notFromPlan, Limits limits, Types types, List<Call> calls, List<Ammo> ammo) {}

  record Limits(
      Integer maxLoopIterations,
      Integer maxStepsPerCycle,
      Integer maxNestingDepth,
      Integer maxCallDepth) {}

  record Types(String enemy, String ammo) {}

  record Call(String id, String name, Unlock unlock) {}

  /** Empty means free; otherwise exactly one of the fields. */
  record Unlock(String feature, Integer wave) {}

  record Ammo(String id, String name) {}

  private ScriptApiLoader() {}

  /**
   * @throws DataException naming the problem if the file breaks any rule
   */
  public static ScriptApiData load(String json) {
    FileContents contents = Json.read(json, FileContents.class, FILE);
    require(contents.notFromPlan() != null, "missing notFromPlan");
    require(contents.limits() != null, "missing limits");
    require(contents.types() != null, "missing types");
    require(contents.calls() != null, "missing calls");
    require(contents.ammo() != null, "missing ammo");

    Map<ApiFunction, ScriptApi.FunctionSpec> functions = new EnumMap<>(ApiFunction.class);
    for (Call call : contents.calls()) {
      ApiFunction function = parse(ApiFunction.class, call.id(), "call id");
      require(call.unlock() != null, "call " + call.id() + " has no unlock");
      ScriptApi.FunctionSpec previous =
          functions.put(function, new ScriptApi.FunctionSpec(call.name(), requirement(call)));
      require(previous == null, "call " + call.id() + " appears twice");
    }
    Map<AmmoType, String> ammoNames = new EnumMap<>(AmmoType.class);
    for (Ammo ammo : contents.ammo()) {
      AmmoType type = parse(AmmoType.class, ammo.id(), "ammo id");
      require(ammoNames.put(type, ammo.name()) == null, "ammo " + ammo.id() + " appears twice");
    }

    Limits limits = contents.limits();
    try {
      ScriptApi api =
          new ScriptApi(contents.types().enemy(), contents.types().ammo(), functions, ammoNames);
      ScriptLimits scriptLimits =
          new ScriptLimits(
              required(limits.maxLoopIterations(), "limits.maxLoopIterations"),
              required(limits.maxStepsPerCycle(), "limits.maxStepsPerCycle"),
              required(limits.maxNestingDepth(), "limits.maxNestingDepth"),
              required(limits.maxCallDepth(), "limits.maxCallDepth"));
      return new ScriptApiData(api, scriptLimits);
    } catch (IllegalArgumentException e) {
      throw new DataException(FILE, e.getMessage(), e);
    }
  }

  private static Requirement requirement(Call call) {
    Unlock unlock = call.unlock();
    if (unlock.feature() == null && unlock.wave() == null) {
      return new Requirement.Free();
    }
    require(
        unlock.feature() == null || unlock.wave() == null,
        "call " + call.id() + " has both a feature and a wave");
    if (unlock.feature() != null) {
      return new Requirement.NeedsFeature(parse(Feature.class, unlock.feature(), "feature"));
    }
    require(unlock.wave() >= 1, "call " + call.id() + " unlocks at wave " + unlock.wave());
    return new Requirement.NeedsWave(unlock.wave());
  }

  private static int required(Integer value, String field) {
    require(value != null, "missing " + field);
    return value;
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value, String what) {
    require(value != null, "missing " + what);
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException e) {
      throw new DataException(FILE, "unknown " + what + " " + value, e);
    }
  }

  private static void require(boolean condition, String problem) {
    if (!condition) {
      throw new DataException(FILE, problem);
    }
  }
}
