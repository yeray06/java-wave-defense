package io.github.yeray06.wavedefense.script.ast;

import java.util.List;

/** Statements. {@code try} and methods will add records here. */
public sealed interface Stmt {
  Pos pos();

  record Block(Pos pos, List<Stmt> statements) implements Stmt {
    public Block {
      statements = List.copyOf(statements);
    }
  }

  /** {@code elseBranch} is {@code null} when there is no {@code else}. */
  record If(Pos pos, Expr condition, Stmt thenBranch, Stmt elseBranch) implements Stmt {}

  record While(Pos pos, Expr condition, Stmt body) implements Stmt {}

  /**
   * {@code for (init; condition; update) body}. {@code init} holds one declaration or expression
   * statements; {@code condition} is {@code null} when absent, which loops until something stops
   * it.
   */
  record For(Pos pos, List<Stmt> init, Expr condition, List<Expr> update, Stmt body)
      implements Stmt {
    public For {
      init = List.copyOf(init);
      update = List.copyOf(update);
    }
  }

  /** {@code for (Type name : iterable) body}. */
  record ForEach(Pos pos, TypeRef type, Pos namePos, String name, Expr iterable, Stmt body)
      implements Stmt {}

  /** {@code int a = 1, b;} is one statement with two declarators. */
  record VarDecl(Pos pos, TypeRef type, List<Declarator> declarators) implements Stmt {
    public VarDecl {
      declarators = List.copyOf(declarators);
    }
  }

  /** {@code initializer} is {@code null} when absent. */
  record Declarator(Pos pos, String name, Expr initializer) {}

  record ExprStmt(Pos pos, Expr expr) implements Stmt {}

  /** A lone {@code ;}. */
  record Empty(Pos pos) implements Stmt {}
}
