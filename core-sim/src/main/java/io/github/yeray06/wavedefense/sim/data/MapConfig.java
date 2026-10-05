package io.github.yeray06.wavedefense.sim.data;

import java.util.List;

/** {@code data/map.json}: the enemies' path (ends at the core) and the turret slots. */
public record MapConfig(List<String> notFromPlan, List<Point> path, List<Point> turretSlots) {

  public record Point(double x, double y) {}

  public MapConfig {
    notFromPlan = List.copyOf(Check.present("notFromPlan", notFromPlan));
    path = List.copyOf(Check.present("path", path));
    if (path.size() < 2) {
      throw new IllegalArgumentException("path needs at least two points");
    }
    for (int i = 1; i < path.size(); i++) {
      if (path.get(i).equals(path.get(i - 1))) {
        throw new IllegalArgumentException("path has a zero-length segment at point " + i);
      }
    }
    turretSlots = List.copyOf(Check.notEmpty("turretSlots", turretSlots));
  }
}
