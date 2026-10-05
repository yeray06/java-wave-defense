package io.github.yeray06.wavedefense.script.runtime;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.FireResult;
import io.github.yeray06.wavedefense.script.ast.Expr.BinaryOp;
import io.github.yeray06.wavedefense.script.diag.FaultType;

/**
 * Factories for executable nodes. The checker builds the tree once at compile time; running it
 * allocates nothing: values live in primitive slots and the lambdas capture only final fields.
 *
 * <p>Evaluation order follows Java: left operand first, then right, then the operation.
 */
public final class Nodes {
  private static final Exec EMPTY = frame -> {};

  private Nodes() {}

  // ------------------------------------------------------------- statements

  public static Exec empty() {
    return EMPTY;
  }

  /** A counted statement: charges a step, then runs. */
  public static Exec statement(int line, Exec body) {
    return frame -> {
      frame.step(line);
      body.exec(frame);
    };
  }

  public static Exec block(Exec[] statements) {
    Exec[] copy = statements.clone();
    return frame -> {
      for (Exec statement : copy) {
        statement.exec(frame);
      }
    };
  }

  public static Exec ifElse(int line, BoolEval condition, Exec thenBranch, Exec elseBranch) {
    Exec otherwise = elseBranch != null ? elseBranch : EMPTY;
    return frame -> {
      frame.step(line);
      if (condition.eval(frame)) {
        thenBranch.exec(frame);
      } else {
        otherwise.exec(frame);
      }
    };
  }

  /**
   * A {@code while}. Each entry may run {@code maxLoopIterations} iterations; the next one raises
   * the infinite-loop exception at the loop's line. Every iteration also charges a step.
   */
  public static Exec whileLoop(int line, BoolEval condition, Exec body) {
    return frame -> {
      int outerLoop = frame.loopLine;
      frame.loopLine = line;
      int iterations = 0;
      frame.step(line);
      while (condition.eval(frame)) {
        if (++iterations > frame.maxLoopIterations()) {
          throw frame.fault(FaultType.INFINITE_LOOP, line);
        }
        body.exec(frame);
        frame.step(line);
      }
      frame.loopLine = outerLoop;
    };
  }

  public static Exec storeInt(int slot, IntEval value) {
    return frame -> frame.ints[slot] = value.eval(frame);
  }

  public static Exec storeBool(int slot, BoolEval value) {
    return frame -> frame.ints[slot] = value.eval(frame) ? 1 : 0;
  }

  public static Exec storeRef(int slot, RefEval value) {
    return frame -> frame.refs[slot] = value.eval(frame);
  }

  /** {@code x op= value} on an int slot, with Java's semantics (division by zero throws). */
  public static Exec compoundInt(int slot, BinaryOp op, IntEval value, int line) {
    IntEval combined = arithmetic(op, intLocal(slot), value, line);
    return frame -> frame.ints[slot] = combined.eval(frame);
  }

  /** {@code x++}, {@code x--} and their prefix forms: statements only, so all the same. */
  public static Exec addToInt(int slot, int delta) {
    return frame -> frame.ints[slot] += delta;
  }

  // Lambdas, not method references: a method reference would dereference a null node built for
  // a script with errors at construction time.

  public static Exec discardInt(IntEval value) {
    return frame -> value.eval(frame);
  }

  public static Exec discardBool(BoolEval value) {
    return frame -> value.eval(frame);
  }

  public static Exec discardRef(RefEval value) {
    return frame -> value.eval(frame);
  }

  // ---------------------------------------------------------------- turret calls

  public static Exec fire(int line) {
    return frame -> {
      FireResult result = frame.api.fire();
      if (result == FireResult.NO_AMMO) {
        throw frame.fault(FaultType.NO_AMMO, line);
      }
      if (result == FireResult.OVERHEATED) {
        throw frame.fault(FaultType.OVERHEAT, line);
      }
    };
  }

  /** Starts the reload and ends the script: the turret is busy from here on. */
  public static Exec reload(int line) {
    return frame -> {
      frame.api.reload();
      throw frame.stop(line);
    };
  }

  public static Exec aim(RefEval enemy, int line) {
    return frame -> frame.api.aim(frame.enemy(enemy.eval(frame), line));
  }

  public static Exec useAmmo(AmmoType type) {
    return frame -> frame.api.useAmmo(type);
  }

  public static IntEval ammo() {
    return frame -> frame.api.ammo();
  }

  public static IntEval heat() {
    return frame -> frame.api.heat();
  }

  public static BoolEval hasEnemies() {
    return frame -> frame.api.hasEnemies();
  }

  public static RefEval nearestEnemy() {
    return frame -> frame.api.nearestEnemy();
  }

  public static RefEval target() {
    return frame -> frame.api.target();
  }

  // ---------------------------------------------------------------- enemy calls

  public static IntEval health(RefEval enemy, int line) {
    return frame -> frame.api.health(frame.enemy(enemy.eval(frame), line));
  }

  public static IntEval speed(RefEval enemy, int line) {
    return frame -> frame.api.speed(frame.enemy(enemy.eval(frame), line));
  }

  public static IntEval distanceToCore(RefEval enemy, int line) {
    return frame -> frame.api.distanceToCore(frame.enemy(enemy.eval(frame), line));
  }

  public static BoolEval armored(RefEval enemy, int line) {
    return frame -> frame.api.armored(frame.enemy(enemy.eval(frame), line));
  }

  // ------------------------------------------------------------- expressions

  public static IntEval intConst(int value) {
    return frame -> value;
  }

  public static BoolEval boolConst(boolean value) {
    return frame -> value;
  }

  public static RefEval nullRef() {
    return frame -> 0L;
  }

  public static IntEval intLocal(int slot) {
    return frame -> frame.ints[slot];
  }

  public static BoolEval boolLocal(int slot) {
    return frame -> frame.ints[slot] != 0;
  }

  public static RefEval refLocal(int slot) {
    return frame -> frame.refs[slot];
  }

  public static IntEval negate(IntEval operand) {
    return frame -> -operand.eval(frame);
  }

  public static BoolEval not(BoolEval operand) {
    return frame -> !operand.eval(frame);
  }

  public static BoolEval and(BoolEval left, BoolEval right) {
    return frame -> left.eval(frame) && right.eval(frame);
  }

  public static BoolEval or(BoolEval left, BoolEval right) {
    return frame -> left.eval(frame) || right.eval(frame);
  }

  /** {@code + - * / %} on ints; {@code /} and {@code %} by zero raise ArithmeticException. */
  public static IntEval arithmetic(BinaryOp op, IntEval left, IntEval right, int line) {
    return switch (op) {
      case ADD -> frame -> left.eval(frame) + right.eval(frame);
      case SUB -> frame -> left.eval(frame) - right.eval(frame);
      case MUL -> frame -> left.eval(frame) * right.eval(frame);
      case DIV ->
          frame -> {
            int dividend = left.eval(frame);
            int divisor = right.eval(frame);
            if (divisor == 0) {
              throw frame.fault(FaultType.ARITHMETIC, line);
            }
            return dividend / divisor;
          };
      case REM ->
          frame -> {
            int dividend = left.eval(frame);
            int divisor = right.eval(frame);
            if (divisor == 0) {
              throw frame.fault(FaultType.ARITHMETIC, line);
            }
            return dividend % divisor;
          };
      default -> throw new IllegalArgumentException("Not arithmetic: " + op);
    };
  }

  /** {@code == != < > <= >=} on ints. */
  public static BoolEval compareInts(BinaryOp op, IntEval left, IntEval right) {
    return switch (op) {
      case EQ -> frame -> left.eval(frame) == right.eval(frame);
      case NE -> frame -> left.eval(frame) != right.eval(frame);
      case LT -> frame -> left.eval(frame) < right.eval(frame);
      case GT -> frame -> left.eval(frame) > right.eval(frame);
      case LE -> frame -> left.eval(frame) <= right.eval(frame);
      case GE -> frame -> left.eval(frame) >= right.eval(frame);
      default -> throw new IllegalArgumentException("Not a comparison: " + op);
    };
  }

  public static BoolEval equalBools(boolean negate, BoolEval left, BoolEval right) {
    return frame -> (left.eval(frame) == right.eval(frame)) != negate;
  }

  /** Reference equality, as in Java: comparing never dereferences, so it never throws. */
  public static BoolEval equalRefs(boolean negate, RefEval left, RefEval right) {
    return frame -> (left.eval(frame) == right.eval(frame)) != negate;
  }
}
