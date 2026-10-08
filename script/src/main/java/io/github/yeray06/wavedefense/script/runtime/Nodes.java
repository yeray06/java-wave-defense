package io.github.yeray06.wavedefense.script.runtime;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.EnemyHandle;
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

  /**
   * {@code for (init; condition; update) body}. Same limits as {@link #whileLoop}; a missing
   * condition ({@code null}) is always true.
   */
  public static Exec forLoop(int line, Exec init, BoolEval condition, Exec update, Exec body) {
    BoolEval test = condition != null ? condition : frame -> true;
    return frame -> {
      int outerLoop = frame.loopLine;
      frame.loopLine = line;
      int iterations = 0;
      frame.step(line);
      init.exec(frame);
      while (test.eval(frame)) {
        if (++iterations > frame.maxLoopIterations()) {
          throw frame.fault(FaultType.INFINITE_LOOP, line);
        }
        body.exec(frame);
        update.exec(frame);
        frame.step(line);
      }
      frame.loopLine = outerLoop;
    };
  }

  /**
   * {@code for (Enemigo e : list) body}: each enemy of the list in turn goes into {@code slot}. The
   * list cannot change while a script runs, so its size is read once, as Java's iterator would.
   */
  public static Exec forEach(int line, RefEval list, int slot, Exec body) {
    return frame -> {
      checkList(frame, list.eval(frame), line);
      int outerLoop = frame.loopLine;
      frame.loopLine = line;
      frame.step(line);
      int size = frame.api.enemyCount();
      for (int i = 0; i < size; i++) {
        if (i >= frame.maxLoopIterations()) {
          throw frame.fault(FaultType.INFINITE_LOOP, line);
        }
        frame.refs[frame.refBase + slot] = frame.api.enemyAt(i);
        body.exec(frame);
        frame.step(line);
      }
      frame.loopLine = outerLoop;
    };
  }

  public static Exec storeInt(int slot, IntEval value) {
    return frame -> frame.ints[frame.intBase + slot] = value.eval(frame);
  }

  public static Exec storeBool(int slot, BoolEval value) {
    return frame -> frame.ints[frame.intBase + slot] = value.eval(frame) ? 1 : 0;
  }

  public static Exec storeRef(int slot, RefEval value) {
    return frame -> frame.refs[frame.refBase + slot] = value.eval(frame);
  }

  /** {@code x op= value} on an int slot, with Java's semantics (division by zero throws). */
  public static Exec compoundInt(int slot, BinaryOp op, IntEval value, int line) {
    IntEval combined = arithmetic(op, intLocal(slot), value, line);
    return frame -> frame.ints[frame.intBase + slot] = combined.eval(frame);
  }

  /** {@code x++}, {@code x--} and their prefix forms: statements only, so all the same. */
  public static Exec addToInt(int slot, int delta) {
    return frame -> frame.ints[frame.intBase + slot] += delta;
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

  // ------------------------------------------------------------ library calls

  /** {@code return;}: ends the method, or the turret's script for this cycle. */
  public static Exec returnVoid() {
    return frame -> {
      throw frame.returnSignal;
    };
  }

  public static Exec returnInt(IntEval value) {
    return frame -> {
      frame.returnedInt = value.eval(frame);
      throw frame.returnSignal;
    };
  }

  public static Exec returnBool(BoolEval value) {
    return frame -> {
      frame.returnedInt = value.eval(frame) ? 1 : 0;
      throw frame.returnSignal;
    };
  }

  public static Exec returnRef(RefEval value) {
    return frame -> {
      frame.returnedRef = value.eval(frame);
      throw frame.returnSignal;
    };
  }

  /**
   * A call to a library method, written from inside {@code caller}.
   *
   * @param arguments evaluate each argument, left to right, into a slot of the caller's (so a call
   *     inside an argument cannot overwrite the ones already evaluated)
   * @param isRef per parameter: whether it is a reference (otherwise int or boolean)
   * @param from per parameter: the caller's slot holding the argument
   * @param to per parameter: the callee's slot for the parameter
   */
  public static Exec callVoid(
      Function caller,
      Function callee,
      Exec[] arguments,
      boolean[] isRef,
      int[] from,
      int[] to,
      int line) {
    Call call = new Call(caller, callee, arguments, isRef, from, to, line);
    return call::invoke;
  }

  public static IntEval callInt(
      Function caller,
      Function callee,
      Exec[] arguments,
      boolean[] isRef,
      int[] from,
      int[] to,
      int line) {
    Call call = new Call(caller, callee, arguments, isRef, from, to, line);
    return frame -> {
      call.invoke(frame);
      return frame.returnedInt;
    };
  }

  public static BoolEval callBool(
      Function caller,
      Function callee,
      Exec[] arguments,
      boolean[] isRef,
      int[] from,
      int[] to,
      int line) {
    Call call = new Call(caller, callee, arguments, isRef, from, to, line);
    return frame -> {
      call.invoke(frame);
      return frame.returnedInt != 0;
    };
  }

  public static RefEval callRef(
      Function caller,
      Function callee,
      Exec[] arguments,
      boolean[] isRef,
      int[] from,
      int[] to,
      int line) {
    Call call = new Call(caller, callee, arguments, isRef, from, to, line);
    return frame -> {
      call.invoke(frame);
      return frame.returnedRef;
    };
  }

  private record Call(
      Function caller,
      Function callee,
      Exec[] arguments,
      boolean[] isRef,
      int[] from,
      int[] to,
      int line) {

    /**
     * Evaluates the arguments, moves to a new region of slots above the caller's, runs the body and
     * comes back. Past the depth limit: StackOverflowError at the call.
     */
    void invoke(Frame frame) {
      for (Exec argument : arguments) {
        argument.exec(frame);
      }
      if (frame.depth >= frame.maxCallDepth()) {
        throw frame.fault(FaultType.STACK_OVERFLOW, line);
      }
      int intBase = frame.intBase;
      int refBase = frame.refBase;
      int loopLine = frame.loopLine;
      int calleeInts = intBase + caller.intSlots;
      int calleeRefs = refBase + caller.refSlots;
      for (int i = 0; i < isRef.length; i++) {
        if (isRef[i]) {
          frame.refs[calleeRefs + to[i]] = frame.refs[refBase + from[i]];
        } else {
          frame.ints[calleeInts + to[i]] = frame.ints[intBase + from[i]];
        }
      }
      frame.intBase = calleeInts;
      frame.refBase = calleeRefs;
      frame.depth++;
      frame.loopLine = 0;
      try {
        callee.body.exec(frame);
      } catch (Return returned) {
        // The value, if any, is in the frame.
      } finally {
        frame.intBase = intBase;
        frame.refBase = refBase;
        frame.depth--;
        frame.loopLine = loopLine;
      }
    }
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

  /**
   * {@code enemigos()}. A list value only says "the enemies in range"; what is in it is read when
   * it is used, which is the same thing because the world does not move while a script runs.
   */
  public static RefEval enemies() {
    return frame -> LIST;
  }

  // ---------------------------------------------------------------- list calls

  /** The value of a non-null list: any value but {@link EnemyHandle#NONE}. */
  private static final long LIST = 1L;

  private static void checkList(Frame frame, long list, int line) {
    if (list == EnemyHandle.NONE) {
      throw frame.fault(FaultType.NULL_POINTER, line);
    }
  }

  public static IntEval listSize(RefEval list, int line) {
    return frame -> {
      checkList(frame, list.eval(frame), line);
      return frame.api.enemyCount();
    };
  }

  public static BoolEval listIsEmpty(RefEval list, int line) {
    return frame -> {
      checkList(frame, list.eval(frame), line);
      return frame.api.enemyCount() == 0;
    };
  }

  /** {@code lista.get(i)}; outside {@code 0..size()-1} raises IndexOutOfBoundsException. */
  public static RefEval listGet(RefEval list, IntEval index, int line) {
    return frame -> {
      checkList(frame, list.eval(frame), line);
      int i = index.eval(frame);
      if (i < 0 || i >= frame.api.enemyCount()) {
        throw frame.fault(FaultType.INDEX_OUT_OF_BOUNDS, line);
      }
      return frame.api.enemyAt(i);
    };
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

  public static BoolEval mutant(RefEval enemy, int line) {
    return frame -> frame.api.mutant(frame.enemy(enemy.eval(frame), line));
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
    return frame -> frame.ints[frame.intBase + slot];
  }

  public static BoolEval boolLocal(int slot) {
    return frame -> frame.ints[frame.intBase + slot] != 0;
  }

  public static RefEval refLocal(int slot) {
    return frame -> frame.refs[frame.refBase + slot];
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
