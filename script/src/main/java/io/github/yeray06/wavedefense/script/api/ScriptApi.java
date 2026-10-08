package io.github.yeray06.wavedefense.script.api;

import io.github.yeray06.wavedefense.script.lexer.Keywords;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The player-facing vocabulary of the turret API: the name of every call, type and constant, and
 * what unlocks each call. Built from data so the theme can rename everything.
 */
public final class ScriptApi {

  /** Name and unlock requirement of one call. */
  public record FunctionSpec(String name, Requirement requirement) {}

  private final String enemyTypeName;
  private final String ammoTypeName;
  private final Map<ApiFunction, FunctionSpec> functions;
  private final Map<AmmoType, String> ammoNames;
  private final Map<String, ApiFunction> turretCalls = new HashMap<>();
  private final Map<String, ApiFunction> enemyCalls = new HashMap<>();
  private final Map<String, AmmoType> ammoConstants = new HashMap<>();

  /**
   * @throws IllegalArgumentException if a call or constant is missing, a name is not a valid Java
   *     identifier, is a reserved word, or is used twice in the same namespace
   */
  public ScriptApi(
      String enemyTypeName,
      String ammoTypeName,
      Map<ApiFunction, FunctionSpec> functions,
      Map<AmmoType, String> ammoNames) {
    this.enemyTypeName = requireIdentifier(enemyTypeName, "enemy type");
    this.ammoTypeName = requireIdentifier(ammoTypeName, "ammo type");
    if (enemyTypeName.equals(ammoTypeName)) {
      throw new IllegalArgumentException("Type names must differ: " + enemyTypeName);
    }
    this.functions = new EnumMap<>(functions);
    this.ammoNames = new EnumMap<>(ammoNames);

    for (ApiFunction function : ApiFunction.values()) {
      FunctionSpec spec = this.functions.get(function);
      if (spec == null || spec.requirement() == null) {
        throw new IllegalArgumentException("Missing call " + function);
      }
      String name = requireIdentifier(spec.name(), "call " + function);
      Map<String, ApiFunction> namespace =
          function.receiver() == ApiFunction.Receiver.TURRET ? turretCalls : enemyCalls;
      if (namespace.put(name, function) != null) {
        throw new IllegalArgumentException("Call name used twice: " + name);
      }
    }
    Set<String> constantNames = new HashSet<>();
    for (AmmoType type : AmmoType.values()) {
      String name = requireIdentifier(this.ammoNames.get(type), "ammo " + type);
      if (!constantNames.add(name)) {
        throw new IllegalArgumentException("Ammo name used twice: " + name);
      }
      ammoConstants.put(name, type);
    }
  }

  public String enemyTypeName() {
    return enemyTypeName;
  }

  public String ammoTypeName() {
    return ammoTypeName;
  }

  public String name(ApiFunction function) {
    return functions.get(function).name();
  }

  public Requirement requirement(ApiFunction function) {
    return functions.get(function).requirement();
  }

  public String ammoName(AmmoType type) {
    return ammoNames.get(type);
  }

  /** A call written without a receiver, such as {@code disparar()}. */
  public Optional<ApiFunction> turretCall(String name) {
    return Optional.ofNullable(turretCalls.get(name));
  }

  /** A call written on an enemy, such as {@code e.vida()}. */
  public Optional<ApiFunction> enemyCall(String name) {
    return Optional.ofNullable(enemyCalls.get(name));
  }

  public Optional<AmmoType> ammoConstant(String name) {
    return Optional.ofNullable(ammoConstants.get(name));
  }

  /** Names of the calls written without a receiver, for suggestions and autocompletion. */
  public Set<String> turretCallNames() {
    return Set.copyOf(turretCalls.keySet());
  }

  /** Names of the calls written on an enemy. */
  public Set<String> enemyCallNames() {
    return Set.copyOf(enemyCalls.keySet());
  }

  /** Names of the ammo constants. */
  public Set<String> ammoConstantNames() {
    return Set.copyOf(ammoConstants.keySet());
  }

  private static String requireIdentifier(String name, String what) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Missing name for " + what);
    }
    if (!Character.isJavaIdentifierStart(name.charAt(0))
        || !name.chars().allMatch(Character::isJavaIdentifierPart)) {
      throw new IllegalArgumentException("Not a Java identifier for " + what + ": " + name);
    }
    if (Keywords.isReserved(name)) {
      throw new IllegalArgumentException("Reserved word used for " + what + ": " + name);
    }
    return name;
  }
}
