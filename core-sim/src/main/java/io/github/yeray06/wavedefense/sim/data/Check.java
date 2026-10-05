package io.github.yeray06.wavedefense.sim.data;

import java.util.Collection;
import java.util.Map;

/** Range checks for data records. They throw; the loader turns that into a DataException. */
final class Check {
  private Check() {}

  static double positive(String field, double value) {
    if (!(value > 0) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(field + " must be positive: " + value);
    }
    return value;
  }

  static int positive(String field, int value) {
    if (value <= 0) {
      throw new IllegalArgumentException(field + " must be positive: " + value);
    }
    return value;
  }

  static double atLeastZero(String field, double value) {
    if (!(value >= 0) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(field + " must be zero or more: " + value);
    }
    return value;
  }

  static int atLeastZero(String field, int value) {
    if (value < 0) {
      throw new IllegalArgumentException(field + " must be zero or more: " + value);
    }
    return value;
  }

  static double fraction(String field, double value) {
    if (!(value >= 0 && value <= 1)) {
      throw new IllegalArgumentException(field + " must be between 0 and 1: " + value);
    }
    return value;
  }

  static <T> T present(String field, T value) {
    if (value == null) {
      throw new IllegalArgumentException("missing " + field);
    }
    return value;
  }

  static <T extends Collection<?>> T notEmpty(String field, T values) {
    if (present(field, values).isEmpty()) {
      throw new IllegalArgumentException(field + " must not be empty");
    }
    return values;
  }

  static <K, V> Map<K, V> notEmpty(String field, Map<K, V> values) {
    if (present(field, values).isEmpty()) {
      throw new IllegalArgumentException(field + " must not be empty");
    }
    return values;
  }
}
