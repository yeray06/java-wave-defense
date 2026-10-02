package io.github.yeray06.wavedefense.script.ast;

import java.util.List;

/** Expressions. New syntax (lists, lambdas…) adds records here. */
public sealed interface Expr {
  Pos pos();

  /** An int literal; the parser has already folded a leading minus into {@code value}. */
  record IntLiteral(Pos pos, int value) implements Expr {}

  record BoolLiteral(Pos pos, boolean value) implements Expr {}

  record NullLiteral(Pos pos) implements Expr {}

  /** A bare name: a variable or a constant. */
  record Name(Pos pos, String name) implements Expr {}

  /** A call without receiver, such as {@code disparar()}. */
  record Call(Pos pos, String name, List<Expr> args) implements Expr {
    public Call {
      args = List.copyOf(args);
    }
  }

  /** A call on a value, such as {@code e.vida()}. */
  record MethodCall(Pos pos, Expr receiver, String name, List<Expr> args) implements Expr {
    public MethodCall {
      args = List.copyOf(args);
    }
  }

  /** {@code e.vida} without parentheses. Always an error in the game. */
  record FieldAccess(Pos pos, Expr receiver, String name) implements Expr {}

  record Unary(Pos pos, UnaryOp op, Expr operand) implements Expr {}

  record Binary(Pos pos, BinaryOp op, Expr left, Expr right) implements Expr {}

  /** {@code x = v} or a compound form. Only valid as a statement. */
  record Assign(Pos pos, AssignOp op, Expr target, Expr value) implements Expr {}

  /** {@code x++}, {@code ++x}, {@code x--}, {@code --x}. Only valid as a statement. */
  record IncDec(Pos pos, boolean increment, boolean prefix, Expr target) implements Expr {}

  /** Something the parser already reported; the checker stays silent about it. */
  record Invalid(Pos pos) implements Expr {}

  enum UnaryOp {
    NOT("!"),
    MINUS("-"),
    PLUS("+");

    private final String symbol;

    UnaryOp(String symbol) {
      this.symbol = symbol;
    }

    public String symbol() {
      return symbol;
    }
  }

  enum BinaryOp {
    OR("||"),
    AND("&&"),
    EQ("=="),
    NE("!="),
    LT("<"),
    GT(">"),
    LE("<="),
    GE(">="),
    ADD("+"),
    SUB("-"),
    MUL("*"),
    DIV("/"),
    REM("%");

    private final String symbol;

    BinaryOp(String symbol) {
      this.symbol = symbol;
    }

    public String symbol() {
      return symbol;
    }
  }

  enum AssignOp {
    SET("=", null),
    ADD("+=", BinaryOp.ADD),
    SUB("-=", BinaryOp.SUB),
    MUL("*=", BinaryOp.MUL),
    DIV("/=", BinaryOp.DIV),
    REM("%=", BinaryOp.REM);

    private final String symbol;
    private final BinaryOp arithmetic;

    AssignOp(String symbol, BinaryOp arithmetic) {
      this.symbol = symbol;
      this.arithmetic = arithmetic;
    }

    public String symbol() {
      return symbol;
    }

    /** The arithmetic of a compound assignment, or {@code null} for plain {@code =}. */
    public BinaryOp arithmetic() {
      return arithmetic;
    }
  }
}
