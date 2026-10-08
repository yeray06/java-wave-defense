package io.github.yeray06.wavedefense.sim.world;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.EnemyHandle;
import io.github.yeray06.wavedefense.script.api.FireResult;
import io.github.yeray06.wavedefense.script.api.TurretApi;
import io.github.yeray06.wavedefense.script.diag.CycleOutcome;
import io.github.yeray06.wavedefense.script.runtime.CompiledScript;
import io.github.yeray06.wavedefense.sim.data.DefaultTarget;
import io.github.yeray06.wavedefense.sim.data.EnemyCatalog;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.data.MapConfig;
import io.github.yeray06.wavedefense.sim.data.TurretConfig;
import io.github.yeray06.wavedefense.sim.data.WaveTable;
import io.github.yeray06.wavedefense.sim.economy.Formulas;
import io.github.yeray06.wavedefense.sim.rng.SplitMix64;
import java.util.ArrayList;
import java.util.List;

/**
 * One wave, simulated in fixed ticks. Deterministic: it reads no clock and no random source, so the
 * same setup always produces the same report. {@link #tick()} allocates nothing.
 *
 * <p>Order inside a tick: spawn, turret cycles, bullets, enemies, end check.
 */
public final class Battle {
  /** Bullets in flight per turret: a burst is at most a handful and lives under one cycle. */
  private static final int BULLETS_PER_TURRET = 64;

  /**
   * Health at or below this counts as dead: 5 × (1 − 0.8) is 0.99999… in floating point, and
   * without the tolerance an armored enemy would need one shot more than the plan's 80 %.
   */
  private static final double DEAD = 1e-9;

  /**
   * Instant events kept for the render (D-40). The busiest wave makes a few hundred per second, so
   * this holds several seconds; a loss only drops visual effects.
   */
  private static final int EVENT_CAPACITY = 4096;

  private final GameData data;
  private final int wave;

  /** The ammo this wave's Mutants are weak to, or {@code null} without Mutants. */
  private final AmmoType mutantWeakness;

  private final PathTrack path;
  private final EnemyPool enemies;
  private final BulletPool bullets;
  private final Turret[] turrets;
  private final TurretApi[] apis;
  private final DefaultTarget defaultTarget;
  private final SplitMix64 random;
  private final BattleEvents events = new BattleEvents(EVENT_CAPACITY);

  // Spawn schedule, computed once.
  private final int[] spawnTicks;
  private final EnemyKind[] spawnKinds;
  private int nextSpawn;

  // Rules in ticks and per-tick units, computed once from data and the loadout.
  private final int ticksPerSecond;
  private final int ticksPerCycle;
  private final int hangTicks;
  private final double rangeSquared;
  private final double bulletStep;
  private final double bulletDamage;
  private final int magazine;
  private final int heatDissipation;
  private final int maxHeat;

  /** Heat per shot by {@link AmmoType#ordinal()}. */
  private final int[] heatPerShot;

  private final double baseHealth;
  private final double healthScale;

  private int tick;
  private int coreLives;
  private boolean finished;
  private boolean defeated;
  private boolean bossKilled;
  private boolean bossLeaked;
  private int kills;
  private final WaveReport.Leak[] leaks;
  private int leakCount;

  public Battle(GameData data, BattleSetup setup) {
    this.data = data;
    this.wave = setup.wave();
    if (wave > data.waves().waves().size()) {
      throw new IllegalArgumentException("no data for wave " + wave);
    }
    this.mutantWeakness = data.waves().wave(wave).mutantWeakness();
    TurretConfig turret = data.turret();
    MapConfig map = data.map();
    if (setup.scripts().size() > map.turretSlots().size()) {
      throw new IllegalArgumentException("more turrets than slots");
    }
    Formulas formulas = new Formulas(data.balance());
    Loadout loadout = setup.loadout();

    ticksPerSecond = data.balance().ticksPerSecond();
    double cyclesPerSecond =
        turret.cyclesPerSecond() + effect(HardwareKind.PROCESSOR) * loadout.processorLevel();
    ticksPerCycle = Math.max(1, (int) Math.round(ticksPerSecond / cyclesPerSecond));
    hangTicks = (int) Math.round(turret.hangSeconds() * ticksPerSecond);
    rangeSquared = turret.rangeUnits() * turret.rangeUnits();
    bulletStep = turret.bulletSpeedUnitsPerSecond() / ticksPerSecond;
    bulletDamage =
        turret.bulletDamage() * (1 + effect(HardwareKind.DAMAGE) * loadout.damageLevel());
    magazine =
        (int)
            Math.round(turret.magazine() + effect(HardwareKind.MAGAZINE) * loadout.magazineLevel());
    heatDissipation =
        (int)
            Math.round(
                turret.heatDissipationPerCycle()
                    + effect(HardwareKind.HEAT_SINK) * loadout.heatSinkLevel());
    maxHeat = turret.maxHeat();
    heatPerShot = new int[AmmoType.values().length];
    for (AmmoType type : AmmoType.values()) {
      heatPerShot[type.ordinal()] = turret.heatPerShot().get(type);
    }
    baseHealth = formulas.enemyHealth(wave);
    healthScale = setup.healthScale();
    coreLives = data.balance().coreLivesPerWave();
    defaultTarget = turret.defaultTarget();
    random = new SplitMix64(setup.seed());

    path = new PathTrack(map.path());
    turrets = new Turret[setup.scripts().size()];
    apis = new TurretApi[turrets.length];
    for (int i = 0; i < turrets.length; i++) {
      MapConfig.Point slot = map.turretSlots().get(i);
      CompiledScript script = setup.scripts().get(i);
      turrets[i] = new Turret(i, slot.x(), slot.y(), script, magazine);
    }

    List<int[]> schedule = new ArrayList<>();
    List<EnemyKind> kinds = new ArrayList<>();
    buildSchedule(data.waves().wave(wave), schedule, kinds);
    spawnTicks = new int[schedule.size()];
    spawnKinds = new EnemyKind[schedule.size()];
    for (int i = 0; i < spawnTicks.length; i++) {
      spawnTicks[i] = schedule.get(i)[0];
      spawnKinds[i] = kinds.get(i);
    }
    enemies = new EnemyPool(spawnTicks.length);
    bullets = new BulletPool(turrets.length * BULLETS_PER_TURRET);
    // After the pool: each turret's enemy list is sized to it.
    for (int i = 0; i < turrets.length; i++) {
      apis[i] = new Api(turrets[i]);
    }
    leaks = new WaveReport.Leak[spawnTicks.length];
  }

  /**
   * Spreads spawn slots over the spawn window. Each slot is followed by a gap weighted by its
   * type's {@code spawnGapFactor} (all 1: an even split). A swarm group is one slot whose members
   * follow each other at the group spacing.
   */
  private void buildSchedule(WaveTable.WaveSpec spec, List<int[]> ticks, List<EnemyKind> kinds) {
    double totalWeight = 0;
    for (WaveTable.Group group : spec.groups()) {
      EnemyCatalog.EnemyType type = data.enemies().type(group.type());
      totalWeight += group.count() / type.groupSize() * type.spawnGapFactor();
    }
    double window = data.waves().spawnWindowSeconds() * ticksPerSecond;
    double weightBefore = 0;
    for (WaveTable.Group group : spec.groups()) {
      EnemyCatalog.EnemyType type = data.enemies().type(group.type());
      int groupTicks = (int) Math.round(type.groupSpacingUnits() / type.speed() * ticksPerSecond);
      for (int g = 0; g < group.count() / type.groupSize(); g++) {
        int start = (int) Math.round(weightBefore * window / totalWeight);
        for (int member = 0; member < type.groupSize(); member++) {
          ticks.add(new int[] {start + member * groupTicks});
          kinds.add(group.type());
        }
        weightBefore += type.spawnGapFactor();
      }
    }
  }

  private double effect(HardwareKind kind) {
    return data.shop().hardware(kind).effectPerLevel();
  }

  // ------------------------------------------------------------------ the tick

  /** Advances one tick. Does nothing once the wave is over. */
  public void tick() {
    if (finished) {
      return;
    }
    spawn();
    for (Turret turret : turrets) {
      updateTurret(turret);
    }
    updateBullets();
    updateEnemies();
    if (defeated || (nextSpawn == spawnTicks.length && enemies.activeCount() == 0)) {
      finished = true;
    }
    tick++;
  }

  /** Runs to the end of the wave. */
  public WaveReport runToEnd() {
    while (!finished) {
      tick();
    }
    return report();
  }

  private void spawn() {
    while (nextSpawn < spawnTicks.length && spawnTicks[nextSpawn] <= tick) {
      EnemyKind kind = spawnKinds[nextSpawn++];
      EnemyCatalog.EnemyType type = data.enemies().type(kind);
      Enemy enemy = enemies.obtain();
      enemy.kind = kind;
      enemy.type = type;
      enemy.maxHealth = baseHealth * type.healthMultiplier() * healthScale;
      enemy.health = enemy.maxHealth;
      enemy.distance = 0;
      enemy.armored = type.togglesArmor() ? type.startsArmored() : type.armored();
      enemy.spawnTick = tick;
      enemy.lastDamageTick = tick;
      enemy.lastInRangeDistance = -1;
      enemy.x = path.x(0);
      enemy.y = path.y(0);
      enemy.prevX = enemy.x;
      enemy.prevY = enemy.y;
    }
  }

  private void updateTurret(Turret turret) {
    if (turret.hungTicksLeft > 0) {
      turret.hungTicksLeft--;
      turret.hungTicks++;
    }
    if (tick % ticksPerCycle != 0) {
      return;
    }
    // D-04: heat dissipates at the start of every cycle, before anything else.
    turret.heat = Math.max(0, turret.heat - heatDissipation);
    if (turret.reloadCyclesLeft > 0) {
      // D-03: busy for the reload cycles, magazine full when it ends. A manual reload started
      // while hung keeps counting during the hang.
      if (--turret.reloadCyclesLeft == 0) {
        turret.ammo = magazine;
      }
      return;
    }
    if (turret.hungTicksLeft > 0) {
      return;
    }
    if (turret.script == null) {
      return;
    }
    CycleOutcome outcome = turret.script.run(apis[turret.index]);
    if (outcome == CycleOutcome.RELOADING) {
      startReload(turret, false);
    } else if (outcome == CycleOutcome.FAULT) {
      turret.faults.record(
          turret.script.faultType(), turret.script.faultLine(), turret.script.faultInLibrary());
      turret.lastFault = turret.script.faultType();
      turret.lastFaultLine = turret.script.faultLine();
      turret.lastFaultInLibrary = turret.script.faultInLibrary();
      turret.hungTicksLeft = hangTicks;
      events.add(
          BattleEventType.FAULT,
          tick,
          turret.index,
          turret.lastFault.ordinal(),
          turret.x,
          turret.y);
    }
  }

  private void startReload(Turret turret, boolean manual) {
    events.add(BattleEventType.RELOAD, tick, turret.index, manual ? 1 : 0, turret.x, turret.y);
    turret.reloads++;
    if (manual) {
      turret.manualReloads++;
    }
    turret.reloadCyclesLeft = data.turret().reloadCycles();
    if (turret.reloadCyclesLeft == 0) {
      turret.ammo = magazine;
    }
  }

  private void updateBullets() {
    for (int i = 0; i < bullets.capacity(); i++) {
      Bullet bullet = bullets.get(i);
      if (!bullet.active) {
        continue;
      }
      Enemy target = enemies.get(bullet.targetIndex);
      if (!target.active || target.generation != bullet.targetGeneration) {
        bullet.active = false;
        turrets[bullet.turret].wastedOnDead++;
        continue;
      }
      bullet.prevX = bullet.x;
      bullet.prevY = bullet.y;
      double dx = target.x - bullet.x;
      double dy = target.y - bullet.y;
      double remaining = StrictMath.hypot(dx, dy);
      if (remaining <= bulletStep) {
        bullet.active = false;
        hit(target, bullet);
      } else {
        bullet.x += dx / remaining * bulletStep;
        bullet.y += dy / remaining * bulletStep;
      }
    }
  }

  private void hit(Enemy enemy, Bullet bullet) {
    double damage = bullet.damage;
    boolean armorStops = enemy.armored && bullet.ammo == AmmoType.NORMAL;
    boolean wrongAmmo = enemy.type.mutant() && bullet.ammo != mutantWeakness;
    if (armorStops) {
      damage *= 1 - data.enemies().armorDamageReduction();
    } else if (wrongAmmo) {
      damage *= 1 - data.enemies().mutantDamageReduction();
    }
    boolean blocked = armorStops || wrongAmmo;
    enemy.health -= damage;
    enemy.lastDamageTick = tick;
    events.add(BattleEventType.HIT, tick, enemy.kind.ordinal(), blocked ? 1 : 0, enemy.x, enemy.y);
    if (enemy.health <= DEAD) {
      events.add(BattleEventType.KILL, tick, enemy.kind.ordinal(), 0, enemy.x, enemy.y);
      kills++;
      if (enemy.kind == EnemyKind.BOSS) {
        bossKilled = true;
      }
      enemies.free(enemy);
    }
  }

  private void updateEnemies() {
    double length = path.length();
    for (int i = 0; i < enemies.capacity(); i++) {
      Enemy enemy = enemies.get(i);
      if (!enemy.active) {
        continue;
      }
      EnemyCatalog.EnemyType type = enemy.type;
      if (type.regenerates()
          && tick - enemy.lastDamageTick >= type.regenDelaySeconds() * ticksPerSecond) {
        enemy.health =
            Math.min(
                enemy.maxHealth,
                enemy.health + enemy.maxHealth * type.regenFractionPerSecond() / ticksPerSecond);
      }
      if (type.togglesArmor()) {
        int toggles =
            (int) ((tick - enemy.spawnTick) / (type.armorToggleSeconds() * ticksPerSecond));
        enemy.armored = type.startsArmored() ^ (toggles % 2 == 1);
      }
      enemy.prevX = enemy.x;
      enemy.prevY = enemy.y;
      enemy.distance += (double) type.speed() / ticksPerSecond;
      if (enemy.distance >= length) {
        leak(enemy);
        continue;
      }
      enemy.x = path.x(enemy.distance);
      enemy.y = path.y(enemy.distance);
      for (Turret turret : turrets) {
        if (inRange(turret, enemy)) {
          enemy.lastInRangeDistance = enemy.distance;
          break;
        }
      }
    }
  }

  private void leak(Enemy enemy) {
    events.add(BattleEventType.LEAK, tick, enemy.kind.ordinal(), 0, enemy.x, enemy.y);
    leaks[leakCount++] =
        new WaveReport.Leak(enemy.kind, enemy.health / enemy.maxHealth, enemy.lastInRangeDistance);
    enemies.free(enemy);
    coreLives--;
    if (enemy.kind == EnemyKind.BOSS) {
      bossLeaked = true;
      defeated = true;
    }
    if (coreLives <= 0) {
      defeated = true;
    }
  }

  private boolean inRange(Turret turret, Enemy enemy) {
    double dx = enemy.x - turret.x;
    double dy = enemy.y - turret.y;
    return dx * dx + dy * dy <= rangeSquared;
  }

  // ----------------------------------------------------------------- commands

  /**
   * The player's emergency click (D-03): same as {@code recargar()}. Allowed even while the turret
   * is hung (D-03b): a hang lasts exactly six cycles, so a script that throws on an empty magazine
   * wakes up and throws again in the same tick, and a player who could only click while it is not
   * hung would never get to reload. Refused while already reloading.
   *
   * @return whether the reload started
   */
  public boolean manualReload(int turretIndex) {
    Turret turret = turrets[turretIndex];
    if (finished || turret.reloadCyclesLeft > 0) {
      return false;
    }
    startReload(turret, true);
    return true;
  }

  // ------------------------------------------------------------------- reading

  public int wave() {
    return wave;
  }

  public int tickCount() {
    return tick;
  }

  public int ticksPerSecond() {
    return ticksPerSecond;
  }

  public boolean finished() {
    return finished;
  }

  public int coreLives() {
    return coreLives;
  }

  public PathTrack path() {
    return path;
  }

  public EnemyPool enemies() {
    return enemies;
  }

  public BulletPool bullets() {
    return bullets;
  }

  /** Instant events for the render (D-40); the battle never reads them. */
  public BattleEvents events() {
    return events;
  }

  /** Magazine size with the loadout's hardware. */
  public int magazine() {
    return magazine;
  }

  public int turretCount() {
    return turrets.length;
  }

  public Turret turret(int index) {
    return turrets[index];
  }

  /** The report so far; final once {@link #finished()}. */
  public WaveReport report() {
    List<WaveReport.TurretReport> turretReports = new ArrayList<>(turrets.length);
    for (Turret turret : turrets) {
      turretReports.add(turret.report());
    }
    List<WaveReport.Leak> leakList = new ArrayList<>(leakCount);
    for (int i = 0; i < leakCount; i++) {
      leakList.add(leaks[i]);
    }
    return new WaveReport(
        wave,
        finished && !defeated,
        nextSpawn,
        kills,
        leakList,
        bossKilled,
        bossLeaked,
        tick,
        turretReports);
  }

  // ---------------------------------------------------------------- turret API

  /** What a turret's script sees. Allocation-free; every call reads the live world. */
  private final class Api implements TurretApi {
    private final Turret turret;

    /** Pool indexes of the enemies in range, in the order they came out; built once per tick. */
    private final int[] inRange;

    private int inRangeCount;
    private int inRangeTick = -1;

    Api(Turret turret) {
      this.turret = turret;
      this.inRange = new int[enemies.capacity()];
    }

    /**
     * Enemies only move between turret cycles, so one list per tick serves every call of a script
     * run. Insertion sort: a few dozen enemies, no allocation.
     */
    private void refreshInRange() {
      if (inRangeTick == tick) {
        return;
      }
      inRangeTick = tick;
      inRangeCount = 0;
      for (int i = 0; i < enemies.capacity(); i++) {
        Enemy enemy = enemies.get(i);
        if (!enemy.active || !inRange(turret, enemy)) {
          continue;
        }
        int at = inRangeCount++;
        while (at > 0 && cameOutAfter(enemies.get(inRange[at - 1]), enemy)) {
          inRange[at] = inRange[at - 1];
          at--;
        }
        inRange[at] = i;
      }
    }

    private static boolean cameOutAfter(Enemy a, Enemy b) {
      return a.spawnTick > b.spawnTick || (a.spawnTick == b.spawnTick && a.index > b.index);
    }

    @Override
    public int enemyCount() {
      refreshInRange();
      return inRangeCount;
    }

    @Override
    public long enemyAt(int index) {
      refreshInRange();
      return enemies.get(inRange[index]).handle();
    }

    @Override
    public FireResult fire() {
      if (turret.ammo == 0) {
        return FireResult.NO_AMMO;
      }
      if (turret.heat >= maxHeat) {
        return FireResult.OVERHEATED;
      }
      turret.ammo--;
      turret.heat = Math.min(maxHeat, turret.heat + heatPerShot[turret.ammoType.ordinal()]);
      turret.shots++;
      if (turret.ammo == 0) {
        events.add(BattleEventType.EMPTY, tick, turret.index, 0, turret.x, turret.y);
      }
      long handle = target();
      if (handle == EnemyHandle.NONE) {
        handle = defaultTarget();
      }
      Enemy enemy = enemies.resolve(handle);
      if (enemy == null) {
        // D-05: nothing in range, the shot goes into the air.
        turret.airShots++;
        events.add(BattleEventType.AIR_SHOT, tick, turret.index, 0, turret.x, turret.y);
        return FireResult.FIRED;
      }
      events.add(
          BattleEventType.SHOT, tick, turret.index, turret.ammoType.ordinal(), turret.x, turret.y);
      Bullet bullet = bullets.obtain();
      if (bullet == null) {
        // Heat caps a burst far below the pool size; reaching this is a bug, reported as such.
        throw new IllegalStateException("bullet pool exhausted");
      }
      bullet.x = turret.x;
      bullet.y = turret.y;
      bullet.prevX = bullet.x;
      bullet.prevY = bullet.y;
      bullet.targetIndex = enemy.index;
      bullet.targetGeneration = enemy.generation;
      bullet.damage = bulletDamage;
      bullet.ammo = turret.ammoType;
      bullet.turret = turret.index;
      return FireResult.FIRED;
    }

    @Override
    public void reload() {
      // The interpreter ends the script; the battle starts the reload from the outcome.
    }

    @Override
    public int ammo() {
      return turret.ammo;
    }

    @Override
    public int heat() {
      return turret.heat;
    }

    @Override
    public boolean hasEnemies() {
      for (int i = 0; i < enemies.capacity(); i++) {
        Enemy enemy = enemies.get(i);
        if (enemy.active && inRange(turret, enemy)) {
          return true;
        }
      }
      return false;
    }

    /** Whom an unaimed shot hits (D-31), among enemies in range. */
    private long defaultTarget() {
      return switch (defaultTarget) {
        case NEAREST_TO_TURRET -> nearestEnemy();
        case NEAREST_TO_CORE -> closestToCore();
        case RANDOM_IN_RANGE -> randomInRange();
      };
    }

    private long closestToCore() {
      Enemy best = null;
      for (int i = 0; i < enemies.capacity(); i++) {
        Enemy enemy = enemies.get(i);
        if (enemy.active
            && inRange(turret, enemy)
            && (best == null || enemy.distance > best.distance)) {
          best = enemy;
        }
      }
      return best == null ? EnemyHandle.NONE : best.handle();
    }

    /** Reservoir sampling: uniform over enemies in range, one pass, no allocation. */
    private long randomInRange() {
      Enemy chosen = null;
      int seen = 0;
      for (int i = 0; i < enemies.capacity(); i++) {
        Enemy enemy = enemies.get(i);
        if (enemy.active && inRange(turret, enemy) && random.nextInt(++seen) == 0) {
          chosen = enemy;
        }
      }
      return chosen == null ? EnemyHandle.NONE : chosen.handle();
    }

    @Override
    public long nearestEnemy() {
      Enemy best = null;
      double bestDistance = Double.MAX_VALUE;
      for (int i = 0; i < enemies.capacity(); i++) {
        Enemy enemy = enemies.get(i);
        if (!enemy.active) {
          continue;
        }
        double dx = enemy.x - turret.x;
        double dy = enemy.y - turret.y;
        double squared = dx * dx + dy * dy;
        if (squared <= rangeSquared && squared < bestDistance) {
          best = enemy;
          bestDistance = squared;
        }
      }
      return best == null ? EnemyHandle.NONE : best.handle();
    }

    @Override
    public long target() {
      Enemy enemy = enemies.resolve(turret.aimed);
      if (enemy == null || !inRange(turret, enemy)) {
        // D-20: a dead or out-of-range target is let go.
        turret.aimed = EnemyHandle.NONE;
        return EnemyHandle.NONE;
      }
      return turret.aimed;
    }

    @Override
    public void aim(long enemy) {
      turret.aimed = enemy;
    }

    @Override
    public void useAmmo(AmmoType type) {
      turret.ammoType = type;
    }

    @Override
    public boolean exists(long enemy) {
      return enemies.resolve(enemy) != null;
    }

    @Override
    public int health(long enemy) {
      // D-22: rounded up, so a living enemy never reads 0.
      return (int) Math.ceil(enemies.resolve(enemy).health);
    }

    @Override
    public int speed(long enemy) {
      return enemies.resolve(enemy).type.speed();
    }

    @Override
    public boolean armored(long enemy) {
      return enemies.resolve(enemy).armored;
    }

    @Override
    public boolean mutant(long enemy) {
      return enemies.resolve(enemy).type.mutant();
    }

    @Override
    public int distanceToCore(long enemy) {
      return (int) Math.max(0, Math.floor(path.length() - enemies.resolve(enemy).distance));
    }
  }
}
