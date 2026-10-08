package io.github.yeray06.wavedefense.script.api;

import java.util.List;

/**
 * Every call a script can make. Signatures are fixed here; the names the player types and when each
 * one unlocks come from data ({@link ScriptApi}).
 */
public enum ApiFunction {
  FIRE(Receiver.TURRET, ValueType.VOID),
  RELOAD(Receiver.TURRET, ValueType.VOID),
  AMMO(Receiver.TURRET, ValueType.INT),
  HEAT(Receiver.TURRET, ValueType.INT),
  HAS_ENEMIES(Receiver.TURRET, ValueType.BOOLEAN),
  NEAREST_ENEMY(Receiver.TURRET, ValueType.ENEMY),
  TARGET(Receiver.TURRET, ValueType.ENEMY),
  /** Enemies in range in the order they came out ({@code enemigos()}). */
  ENEMIES(Receiver.TURRET, ValueType.ENEMY_LIST),
  AIM(Receiver.TURRET, ValueType.VOID, ValueType.ENEMY),
  USE_AMMO(Receiver.TURRET, ValueType.VOID, ValueType.AMMO),
  HEALTH(Receiver.ENEMY, ValueType.INT),
  SPEED(Receiver.ENEMY, ValueType.INT),
  ARMORED(Receiver.ENEMY, ValueType.BOOLEAN),
  DISTANCE_TO_CORE(Receiver.ENEMY, ValueType.INT);

  /** Whether the call is written bare ({@code disparar()}) or on an enemy ({@code e.vida()}). */
  public enum Receiver {
    TURRET,
    ENEMY
  }

  private final Receiver receiver;
  private final ValueType returnType;
  private final List<ValueType> parameters;

  ApiFunction(Receiver receiver, ValueType returnType, ValueType... parameters) {
    this.receiver = receiver;
    this.returnType = returnType;
    this.parameters = List.of(parameters);
  }

  public Receiver receiver() {
    return receiver;
  }

  public ValueType returnType() {
    return returnType;
  }

  public List<ValueType> parameters() {
    return parameters;
  }
}
