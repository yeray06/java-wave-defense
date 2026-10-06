package io.github.yeray06.wavedefense.sim.world;

/**
 * The instant events of a battle, for the render's effects (D-40): shots, hits, kills, faults…
 *
 * <p>A fixed-capacity ring of primitive columns, so writing allocates nothing. It keeps everything
 * since the render last {@linkplain #clear() cleared} it, however many ticks that spans (×3 runs
 * several per frame). When it is full the oldest event is dropped: nobody reads it in the simulator
 * or the tests, and a lost effect is only a visual loss.
 *
 * <p>The battle only writes here and never reads, so it cannot influence the simulation or its
 * determinism. The wave report does not come from here either: the battle keeps its own counters.
 */
public final class BattleEvents {
  private static final BattleEventType[] TYPES = BattleEventType.values();

  private final int[] type;
  private final int[] tick;
  private final int[] a;
  private final int[] b;
  private final double[] x;
  private final double[] y;
  private int start;
  private int size;
  private int dropped;

  public BattleEvents(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    type = new int[capacity];
    tick = new int[capacity];
    a = new int[capacity];
    b = new int[capacity];
    x = new double[capacity];
    y = new double[capacity];
  }

  void add(BattleEventType eventType, int atTick, int first, int second, double atX, double atY) {
    int slot;
    if (size == type.length) {
      slot = start;
      start = (start + 1) % type.length;
      dropped++;
    } else {
      slot = (start + size) % type.length;
      size++;
    }
    type[slot] = eventType.ordinal();
    tick[slot] = atTick;
    a[slot] = first;
    b[slot] = second;
    x[slot] = atX;
    y[slot] = atY;
  }

  /** Events kept, oldest first. */
  public int size() {
    return size;
  }

  public int capacity() {
    return type.length;
  }

  /** Events lost to a full buffer since the last {@link #clear()}. */
  public int dropped() {
    return dropped;
  }

  public BattleEventType type(int i) {
    return TYPES[type[slot(i)]];
  }

  public int tick(int i) {
    return tick[slot(i)];
  }

  /** First argument; its meaning depends on the {@linkplain BattleEventType type}. */
  public int a(int i) {
    return a[slot(i)];
  }

  /** Second argument; its meaning depends on the {@linkplain BattleEventType type}. */
  public int b(int i) {
    return b[slot(i)];
  }

  public double x(int i) {
    return x[slot(i)];
  }

  public double y(int i) {
    return y[slot(i)];
  }

  /** The render's read is done. */
  public void clear() {
    start = 0;
    size = 0;
    dropped = 0;
  }

  private int slot(int i) {
    if (i < 0 || i >= size) {
      throw new IndexOutOfBoundsException(i);
    }
    return (start + i) % type.length;
  }
}
