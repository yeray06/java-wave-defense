package io.github.yeray06.wavedefense.sim.world;

import io.github.yeray06.wavedefense.sim.data.MapConfig;
import java.util.List;

/** The enemies' path as a polyline, addressed by distance travelled from its start. */
public final class PathTrack {
  private final double[] xs;
  private final double[] ys;

  /** Distance from the start to each point. */
  private final double[] cumulative;

  private final double length;

  public PathTrack(List<MapConfig.Point> points) {
    int n = points.size();
    xs = new double[n];
    ys = new double[n];
    cumulative = new double[n];
    for (int i = 0; i < n; i++) {
      xs[i] = points.get(i).x();
      ys[i] = points.get(i).y();
      if (i > 0) {
        cumulative[i] = cumulative[i - 1] + StrictMath.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
      }
    }
    length = cumulative[n - 1];
  }

  public double length() {
    return length;
  }

  public double x(double distance) {
    int s = segment(distance);
    return xs[s] + (xs[s + 1] - xs[s]) * fraction(s, distance);
  }

  public double y(double distance) {
    int s = segment(distance);
    return ys[s] + (ys[s + 1] - ys[s]) * fraction(s, distance);
  }

  /** Index of the segment containing {@code distance}, clamped to the path. */
  public int segment(double distance) {
    for (int i = 1; i < cumulative.length - 1; i++) {
      if (distance < cumulative[i]) {
        return i - 1;
      }
    }
    return cumulative.length - 2;
  }

  private double fraction(int segment, double distance) {
    double start = cumulative[segment];
    double span = cumulative[segment + 1] - start;
    return Math.min(1, Math.max(0, (distance - start) / span));
  }
}
