package io.github.yeray06.wavedefense.script.check;

import io.github.yeray06.wavedefense.script.api.AmmoType;
import io.github.yeray06.wavedefense.script.api.ApiFunction;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.Requirement;
import io.github.yeray06.wavedefense.script.api.ScriptApi;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.script.api.ValueType;
import io.github.yeray06.wavedefense.script.ast.Expr;
import io.github.yeray06.wavedefense.script.ast.Pos;
import io.github.yeray06.wavedefense.script.ast.Stmt;
import io.github.yeray06.wavedefense.script.ast.TypeRef;
import io.github.yeray06.wavedefense.script.diag.Diagnostics;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import io.github.yeray06.wavedefense.script.runtime.BoolEval;
import io.github.yeray06.wavedefense.script.runtime.Exec;
import io.github.yeray06.wavedefense.script.runtime.Function;
import io.github.yeray06.wavedefense.script.runtime.IntEval;
import io.github.yeray06.wavedefense.script.runtime.Nodes;
import io.github.yeray06.wavedefense.script.runtime.RefEval;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

/**
 * One pass over the AST that resolves names and scopes, checks static types and definite assignment
 * like javac, applies what the player has unlocked, counts memory lines and, as it goes, builds the
 * executable tree. Doing both in one pass avoids resolving every name twice; the tree is only used
 * when no error was reported.
 *
 * <p>It checks either a turret's script ({@link #check}) or the library ({@link #checkLibrary}).
 * Each function (the script, or one method) has its own numbering of slots.
 */
public final class Checker {

  /** What the checker produced. {@code root} is meaningless if errors were reported. */
  public record Result(
      Exec root,
      int intSlots,
      int refSlots,
      int memoryUsed,
      SortedMap<Integer, Integer> memoryByLine) {}

  private record Var(String name, Type type, int id, int slot) {}

  /** The function being checked: where its slots are counted and what it must return. */
  private static final class Current {
    final Function function;
    final Type returnType;
    int intSlots;
    int refSlots;

    Current(Function function, Type returnType) {
      this.function = function;
      this.returnType = returnType;
    }
  }

  /** Java's list type: the only generic type, and its name is Java's, not the theme's. */
  private static final String LIST_TYPE = "List";

  private final ScriptApi api;
  private final Unlocks unlocks;
  private final Diagnostics diagnostics;
  private final MemoryUsage memory;
  private final Deque<Map<String, Var>> scopes = new ArrayDeque<>();
  private final Set<String> reportedLocks = new HashSet<>();

  /** The library's methods: being declared ({@link #checkLibrary}) or already built. */
  private final Map<String, Library.Method> methods;

  /** Whether calls to the library must be refused because it has errors. */
  private final boolean libraryBroken;

  private Current current;
  private int nextVarId;

  private Checker(
      ScriptApi api,
      Unlocks unlocks,
      int memoryLimit,
      Diagnostics diagnostics,
      Map<String, Library.Method> methods,
      boolean libraryBroken) {
    this.api = api;
    this.unlocks = unlocks;
    this.diagnostics = diagnostics;
    this.memory = new MemoryUsage(memoryLimit);
    this.methods = methods;
    this.libraryBroken = libraryBroken;
  }

  /** Checks a turret's script, which may call the methods of {@code library}. */
  public static Result check(
      List<Stmt> statements,
      Library library,
      ScriptApi api,
      Unlocks unlocks,
      int memoryLimit,
      Diagnostics diagnostics) {
    Map<String, Library.Method> methods = new HashMap<>();
    for (String name : library.methodNames()) {
      methods.put(name, library.method(name).orElseThrow());
    }
    Checker checker =
        new Checker(api, unlocks, memoryLimit, diagnostics, methods, library.hasErrors());
    Function script = new Function();
    checker.current = new Current(script, Type.VOID);
    Exec root = checker.block(statements, new BitSet());
    script.define(root, checker.current.intSlots, checker.current.refSlots);
    Pos over = checker.memory.firstOverLimit();
    if (over != null) {
      diagnostics.report(
          ErrorCode.MEMORY_EXCEEDED,
          over.line(),
          over.column(),
          checker.memory.used(),
          checker.memory.limit());
    }
    return new Result(
        root,
        checker.current.intSlots,
        checker.current.refSlots,
        checker.memory.used(),
        checker.memory.byLine());
  }

  /**
   * Checks the library: every header first, so methods can call each other in any order (and
   * themselves), then every body. Library lines take no memory (the plan), so nothing is counted
   * against a limit.
   */
  public static Library checkLibrary(
      List<Stmt.MethodDecl> declarations, ScriptApi api, Unlocks unlocks, Diagnostics diagnostics) {
    Map<String, Library.Method> methods = new LinkedHashMap<>();
    Checker checker = new Checker(api, unlocks, Integer.MAX_VALUE - 1, diagnostics, methods, false);
    List<Library.Method> headers = new ArrayList<>();
    for (Stmt.MethodDecl declaration : declarations) {
      headers.add(checker.header(declaration));
    }
    int maxInts = 0;
    int maxRefs = 0;
    for (int i = 0; i < declarations.size(); i++) {
      Library.Method method = headers.get(i);
      checker.body(declarations.get(i), method);
      maxInts = Math.max(maxInts, method.function().intSlots());
      maxRefs = Math.max(maxRefs, method.function().refSlots());
    }
    return new Library(methods, diagnostics.hasErrors(), maxInts, maxRefs);
  }

  // ------------------------------------------------------------------- methods

  /** Resolves a method's signature and registers it, unless its name is taken. */
  private Library.Method header(Stmt.MethodDecl d) {
    requireFeature(Feature.METHODS, d.pos());
    Type returnType =
        d.returnType() instanceof TypeRef.VoidType ? Type.VOID : declaredType(d.returnType());
    List<Type> params = new ArrayList<>();
    int[] paramSlots = new int[d.params().size()];
    int ints = 0;
    int refs = 0;
    for (int i = 0; i < d.params().size(); i++) {
      Type type = declaredType(d.params().get(i).type());
      params.add(type);
      paramSlots[i] = type.isReference() ? refs++ : ints++;
    }
    Library.Method method =
        new Library.Method(d.name(), returnType, params, paramSlots, new Function());
    if (methods.containsKey(d.name())) {
      report(ErrorCode.DUPLICATE_METHOD, d.namePos(), d.name());
    } else if (api.turretCall(d.name()).isPresent()) {
      report(ErrorCode.METHOD_NAME_TAKEN, d.namePos(), d.name());
    } else {
      methods.put(d.name(), method);
    }
    return method;
  }

  private void body(Stmt.MethodDecl d, Library.Method method) {
    current = new Current(method.function(), method.returnType());
    scopes.clear();
    scopes.push(new HashMap<>());
    BitSet assigned = new BitSet();
    for (int i = 0; i < d.params().size(); i++) {
      Stmt.Param param = d.params().get(i);
      if (lookup(param.name()) != null) {
        report(ErrorCode.DUPLICATE_VARIABLE, param.pos(), param.name());
        continue;
      }
      Type type = method.params().get(i);
      Var var = declare(param.name(), type);
      assigned.set(var.id());
    }
    Exec body = block(d.body().statements(), assigned);
    scopes.pop();
    Type returns = method.returnType();
    if (returns != Type.VOID && returns != Type.ERROR && completes(d.body())) {
      report(ErrorCode.MISSING_RETURN, d.namePos(), d.name());
    }
    method.function().define(body, current.intSlots, current.refSlots);
  }

  /**
   * Whether running {@code statement} can reach its end, as javac decides it, so a method that
   * returns something cannot fall off its end. Like javac, an {@code if} can always end; unlike
   * javac, only the literal {@code true} counts as an endless loop's condition (javac also folds
   * expressions such as {@code 1 == 1}).
   */
  private static boolean completes(Stmt statement) {
    return switch (statement) {
      case Stmt.Block b -> b.statements().stream().allMatch(Checker::completes);
      case Stmt.If s ->
          s.elseBranch() == null || completes(s.thenBranch()) || completes(s.elseBranch());
      case Stmt.While s -> !isTrue(s.condition());
      case Stmt.For s -> s.condition() != null && !isTrue(s.condition());
      case Stmt.Return s -> false;
      default -> true;
    };
  }

  private static boolean isTrue(Expr condition) {
    return condition instanceof Expr.BoolLiteral literal && literal.value();
  }

  private Exec returnStatement(Stmt.Return s, BitSet assigned) {
    memory.count(s.pos());
    requireFeature(Feature.METHODS, s.pos());
    Type expected = current.returnType;
    int line = s.pos().line();
    if (s.value() == null) {
      if (expected != Type.VOID && expected != Type.ERROR) {
        report(ErrorCode.MISSING_RETURN_VALUE, s.pos(), name(expected));
      }
      return Nodes.statement(line, Nodes.returnVoid());
    }
    Typed value = expression(s.value(), assigned);
    if (expected == Type.VOID) {
      report(ErrorCode.UNEXPECTED_RETURN_VALUE, s.value().pos());
      return Nodes.empty();
    }
    requireAssignable(value.type(), expected, s.value().pos());
    if (value.type() == Type.ERROR || !assignable(value.type(), expected)) {
      return Nodes.empty();
    }
    Exec returns =
        switch (expected) {
          case INT -> Nodes.returnInt(value.asInt());
          case BOOLEAN -> Nodes.returnBool(value.asBool());
          case ENEMY, ENEMY_LIST -> Nodes.returnRef(value.asRef());
          default -> Nodes.empty();
        };
    return Nodes.statement(line, returns);
  }

  /** A method where only statements may go: the library is the place for methods. */
  private Exec misplacedMethod(Stmt.MethodDecl d) {
    if (unlocks.has(Feature.METHODS)) {
      report(ErrorCode.METHOD_NOT_ALLOWED_HERE, d.pos());
    } else {
      requireFeature(Feature.METHODS, d.pos());
    }
    return Nodes.empty();
  }

  /** A call to one of the library's methods. */
  private Typed libraryCall(Expr.Call e, Library.Method method, List<Typed> args) {
    requireFeature(Feature.METHODS, e.pos());
    Type returns = method.returnType();
    Typed failed = returns == Type.VOID ? new Typed(Type.VOID, Nodes.empty()) : Typed.ERROR;
    if (libraryBroken) {
      report(ErrorCode.LIBRARY_HAS_ERRORS, e.pos(), e.name());
      return failed;
    }
    List<Type> params = method.params();
    if (args.size() != params.size()) {
      report(ErrorCode.WRONG_ARGUMENT_COUNT, e.pos(), e.name(), params.size(), args.size());
      return failed;
    }
    boolean ok = true;
    Exec[] stores = new Exec[args.size()];
    boolean[] isRef = new boolean[args.size()];
    int[] from = new int[args.size()];
    for (int i = 0; i < args.size(); i++) {
      Type expected = params.get(i);
      Typed arg = args.get(i);
      if (arg.type() == Type.ERROR || expected == Type.ERROR) {
        ok = false;
        continue;
      }
      if (!assignable(arg.type(), expected)) {
        report(
            ErrorCode.ARGUMENT_TYPE_MISMATCH,
            e.args().get(i).pos(),
            e.name(),
            name(expected),
            name(arg.type()));
        ok = false;
        continue;
      }
      isRef[i] = expected.isReference();
      from[i] = isRef[i] ? current.refSlots++ : current.intSlots++;
      stores[i] =
          switch (expected) {
            case INT -> Nodes.storeInt(from[i], arg.asInt());
            case BOOLEAN -> Nodes.storeBool(from[i], arg.asBool());
            default -> Nodes.storeRef(from[i], arg.asRef());
          };
    }
    if (!ok || returns == Type.ERROR) {
      return failed;
    }
    Function caller = current.function;
    Function callee = method.function();
    int[] to = method.paramSlots().clone();
    int line = e.pos().line();
    return switch (returns) {
      case VOID ->
          new Typed(Type.VOID, Nodes.callVoid(caller, callee, stores, isRef, from, to, line));
      case INT -> new Typed(Type.INT, Nodes.callInt(caller, callee, stores, isRef, from, to, line));
      case BOOLEAN ->
          new Typed(Type.BOOLEAN, Nodes.callBool(caller, callee, stores, isRef, from, to, line));
      default -> new Typed(returns, Nodes.callRef(caller, callee, stores, isRef, from, to, line));
    };
  }

  // ---------------------------------------------------------------- statements

  /** Checks statements in a new scope; {@code assigned} is updated in place. */
  private Exec block(List<Stmt> statements, BitSet assigned) {
    scopes.push(new HashMap<>());
    List<Exec> execs = new ArrayList<>();
    for (Stmt statement : statements) {
      execs.add(statement(statement, assigned));
    }
    scopes.pop();
    return Nodes.block(execs.toArray(Exec[]::new));
  }

  /** A branch or loop body: its own scope, so a stray declaration cannot leak out. */
  private Exec branch(Stmt statement, BitSet assigned) {
    return statement instanceof Stmt.Block b
        ? block(b.statements(), assigned)
        : block(List.of(statement), assigned);
  }

  private Exec statement(Stmt statement, BitSet assigned) {
    return switch (statement) {
      case Stmt.Block b -> block(b.statements(), assigned);
      case Stmt.Empty e -> Nodes.empty();
      case Stmt.If s -> ifStatement(s, assigned);
      case Stmt.While s -> whileStatement(s, assigned);
      case Stmt.For s -> forStatement(s, assigned);
      case Stmt.ForEach s -> forEachStatement(s, assigned);
      case Stmt.VarDecl s -> declaration(s, assigned);
      case Stmt.ExprStmt s -> expressionStatement(s, assigned);
      case Stmt.Return s -> returnStatement(s, assigned);
      case Stmt.MethodDecl d -> misplacedMethod(d);
    };
  }

  private Exec ifStatement(Stmt.If s, BitSet assigned) {
    memory.count(s.pos());
    requireFeature(Feature.IF_ELSE, s.pos());
    BoolEval condition = condition(s.condition(), assigned);
    BitSet afterThen = (BitSet) assigned.clone();
    Exec thenBranch = branch(s.thenBranch(), afterThen);
    Exec elseBranch = null;
    BitSet afterElse = (BitSet) assigned.clone();
    if (s.elseBranch() != null) {
      elseBranch = branch(s.elseBranch(), afterElse);
    }
    // Definitely assigned after the if only if assigned on both paths; a path that cannot reach
    // the end (it returns) does not count.
    if (s.elseBranch() != null && !completes(s.thenBranch())) {
      assigned.or(afterElse);
    } else if (s.elseBranch() != null && !completes(s.elseBranch())) {
      assigned.or(afterThen);
    } else {
      afterThen.and(afterElse);
      assigned.or(afterThen);
    }
    return Nodes.ifElse(s.pos().line(), condition, thenBranch, elseBranch);
  }

  private Exec whileStatement(Stmt.While s, BitSet assigned) {
    memory.count(s.pos());
    requireFeature(Feature.WHILE, s.pos());
    BoolEval condition = condition(s.condition(), assigned);
    // The body may run zero times: its assignments do not count afterwards.
    Exec body = branch(s.body(), (BitSet) assigned.clone());
    return Nodes.whileLoop(s.pos().line(), condition, body);
  }

  /**
   * The header is one memory line, like a {@code while}; its declaration and updates are part of
   * it. Variables declared in it live until the end of the loop.
   */
  private Exec forStatement(Stmt.For s, BitSet assigned) {
    memory.count(s.pos());
    requireFeature(Feature.FOR, s.pos());
    scopes.push(new HashMap<>());
    List<Exec> init = new ArrayList<>();
    for (Stmt statement : s.init()) {
      init.add(
          switch (statement) {
            case Stmt.VarDecl d -> declarationBody(d, assigned);
            case Stmt.ExprStmt e -> statementExpression(e.expr(), assigned);
            default -> Nodes.empty();
          });
    }
    BoolEval condition = s.condition() == null ? null : condition(s.condition(), assigned);
    // Body and update may run zero times: their assignments do not count afterwards.
    BitSet inLoop = (BitSet) assigned.clone();
    Exec body = branch(s.body(), inLoop);
    List<Exec> update = new ArrayList<>();
    for (Expr e : s.update()) {
      update.add(statementExpression(e, inLoop));
    }
    scopes.pop();
    return Nodes.forLoop(
        s.pos().line(),
        Nodes.block(init.toArray(Exec[]::new)),
        condition,
        Nodes.block(update.toArray(Exec[]::new)),
        body);
  }

  private Exec forEachStatement(Stmt.ForEach s, BitSet assigned) {
    memory.count(s.pos());
    requireFeature(Feature.FOR, s.pos());
    Type elementType = declaredType(s.type());
    Typed list = expression(s.iterable(), assigned);
    boolean listOk = list.type() == Type.ENEMY_LIST;
    if (list.type() != Type.ERROR && !listOk) {
      report(ErrorCode.FOR_EACH_NOT_A_LIST, s.iterable().pos(), name(list.type()));
    }
    if (elementType != Type.ERROR && elementType != Type.ENEMY) {
      report(ErrorCode.FOR_EACH_WRONG_TYPE, s.type().pos(), name(elementType), name(Type.ENEMY));
    }
    scopes.push(new HashMap<>());
    Var var = null;
    if (lookup(s.name()) != null) {
      report(ErrorCode.DUPLICATE_VARIABLE, s.namePos(), s.name());
    } else {
      var = declare(s.name(), elementType == Type.ENEMY ? Type.ENEMY : Type.ERROR);
    }
    BitSet inLoop = (BitSet) assigned.clone();
    if (var != null) {
      inLoop.set(var.id());
    }
    Exec body = branch(s.body(), inLoop);
    scopes.pop();
    if (!listOk || var == null || var.type() != Type.ENEMY) {
      return Nodes.empty();
    }
    return Nodes.forEach(s.pos().line(), list.asRef(), var.slot(), body);
  }

  /** A statement expression without its own memory line: a for's init or update. */
  private Exec statementExpression(Expr expr, BitSet assigned) {
    return switch (expr) {
      case Expr.Assign a -> assignment(a, assigned);
      case Expr.IncDec i -> incDec(i, assigned);
      case Expr.Invalid i -> Nodes.empty();
      default -> discard(expression(expr, assigned));
    };
  }

  private Exec declaration(Stmt.VarDecl s, BitSet assigned) {
    memory.count(s.pos());
    return Nodes.statement(s.pos().line(), declarationBody(s, assigned));
  }

  private Exec declarationBody(Stmt.VarDecl s, BitSet assigned) {
    requireFeature(Feature.VARIABLES, s.pos());
    Type type = declaredType(s.type());
    List<Exec> stores = new ArrayList<>();
    for (Stmt.Declarator d : s.declarators()) {
      Var var = null;
      if (lookup(d.name()) != null) {
        report(ErrorCode.DUPLICATE_VARIABLE, d.pos(), d.name());
      } else {
        var = declare(d.name(), type);
      }
      if (d.initializer() == null) {
        continue;
      }
      Typed value = expression(d.initializer(), assigned);
      requireAssignable(value.type(), type, d.initializer().pos());
      if (var != null) {
        assigned.set(var.id());
        stores.add(store(var, value));
      }
    }
    return Nodes.block(stores.toArray(Exec[]::new));
  }

  private Type declaredType(TypeRef ref) {
    return switch (ref) {
      case TypeRef.IntType t -> Type.INT;
      case TypeRef.BooleanType t -> Type.BOOLEAN;
      case TypeRef.Invalid t -> Type.ERROR;
      case TypeRef.VoidType t -> {
        report(ErrorCode.UNSUPPORTED_TYPE, t.pos(), "void");
        yield Type.ERROR;
      }
      case TypeRef.Generic t -> {
        requireFeature(Feature.FOR, t.pos());
        boolean enemyList =
            t.name().equals(LIST_TYPE)
                && t.argument() instanceof TypeRef.Named n
                && n.name().equals(api.enemyTypeName());
        if (enemyList) {
          yield Type.ENEMY_LIST;
        }
        report(ErrorCode.UNSUPPORTED_TYPE, t.pos(), describe(t));
        yield Type.ERROR;
      }
      case TypeRef.Named t -> {
        if (t.name().equals(LIST_TYPE)) {
          // A raw List: javac takes it, but its elements would be Object.
          report(ErrorCode.UNSUPPORTED_TYPE, t.pos(), t.name());
          yield Type.ERROR;
        }
        if (t.name().equals(api.enemyTypeName())) {
          yield Type.ENEMY;
        }
        if (t.name().equals(api.ammoTypeName())) {
          report(ErrorCode.UNSUPPORTED_TYPE, t.pos(), t.name());
        } else {
          report(ErrorCode.UNKNOWN_TYPE, t.pos(), t.name());
        }
        yield Type.ERROR;
      }
    };
  }

  private Exec expressionStatement(Stmt.ExprStmt s, BitSet assigned) {
    memory.count(s.pos());
    int line = s.pos().line();
    return switch (s.expr()) {
      case Expr.Assign a -> Nodes.statement(line, assignment(a, assigned));
      case Expr.IncDec i -> Nodes.statement(line, incDec(i, assigned));
      case Expr.Invalid i -> Nodes.empty();
      default -> {
        // Calls, plus non-statements the parser already reported (still checked for errors).
        Typed value = expression(s.expr(), assigned);
        yield Nodes.statement(line, discard(value));
      }
    };
  }

  private Exec discard(Typed value) {
    return switch (value.type()) {
      case INT -> Nodes.discardInt(value.asInt());
      case BOOLEAN -> Nodes.discardBool(value.asBool());
      case ENEMY, ENEMY_LIST, NULL -> Nodes.discardRef(value.asRef());
      case VOID -> (Exec) value.node();
      case AMMO, ERROR -> Nodes.empty();
    };
  }

  private Exec assignment(Expr.Assign a, BitSet assigned) {
    requireFeature(Feature.VARIABLES, a.pos());
    Var var = assignableVariable(a.target());
    Typed value = expression(a.value(), assigned);
    if (var == null) {
      return Nodes.empty();
    }
    if (a.op() == Expr.AssignOp.SET) {
      requireAssignable(value.type(), var.type(), a.value().pos());
      assigned.set(var.id());
      return store(var, value);
    }
    requireInitialized(var, assigned, a.target().pos());
    if (var.type() != Type.INT || (value.type() != Type.INT && value.type() != Type.ERROR)) {
      if (var.type() != Type.ERROR && value.type() != Type.ERROR) {
        report(
            ErrorCode.BAD_OPERAND_TYPES,
            a.pos(),
            a.op().symbol(),
            name(var.type()),
            name(value.type()));
      }
      return Nodes.empty();
    }
    return Nodes.compoundInt(var.slot(), a.op().arithmetic(), value.asInt(), a.pos().line());
  }

  private Exec incDec(Expr.IncDec i, BitSet assigned) {
    requireFeature(Feature.VARIABLES, i.pos());
    Var var = assignableVariable(i.target());
    if (var == null) {
      return Nodes.empty();
    }
    requireInitialized(var, assigned, i.target().pos());
    if (var.type() != Type.INT) {
      if (var.type() != Type.ERROR) {
        report(ErrorCode.BAD_OPERAND_TYPE, i.pos(), i.increment() ? "++" : "--", name(var.type()));
      }
      return Nodes.empty();
    }
    return Nodes.addToInt(var.slot(), i.increment() ? 1 : -1);
  }

  /** The local variable an assignment writes to, or {@code null} after reporting why not. */
  private Var assignableVariable(Expr target) {
    if (target instanceof Expr.Name n) {
      Var var = lookup(n.name());
      if (var != null) {
        return var;
      }
      if (api.ammoConstant(n.name()).isPresent()) {
        report(ErrorCode.CANNOT_ASSIGN, n.pos(), n.name());
      } else {
        report(ErrorCode.UNKNOWN_SYMBOL, n.pos(), n.name());
      }
      return null;
    }
    if (!(target instanceof Expr.Invalid)) {
      report(ErrorCode.CANNOT_ASSIGN, target.pos(), describe(target));
    }
    return null;
  }

  private Exec store(Var var, Typed value) {
    return switch (var.type()) {
      case INT -> value.type() == Type.INT ? Nodes.storeInt(var.slot(), value.asInt()) : null;
      case BOOLEAN ->
          value.type() == Type.BOOLEAN ? Nodes.storeBool(var.slot(), value.asBool()) : null;
      case ENEMY, ENEMY_LIST ->
          assignable(value.type(), var.type()) ? Nodes.storeRef(var.slot(), value.asRef()) : null;
      default -> null;
    };
  }

  // --------------------------------------------------------------- expressions

  private BoolEval condition(Expr expr, BitSet assigned) {
    Typed value = expression(expr, assigned);
    requireAssignable(value.type(), Type.BOOLEAN, expr.pos());
    return value.type() == Type.BOOLEAN ? value.asBool() : null;
  }

  private Typed expression(Expr expr, BitSet assigned) {
    return switch (expr) {
      case Expr.IntLiteral e -> {
        requireFeature(Feature.IF_ELSE, e.pos());
        yield new Typed(Type.INT, Nodes.intConst(e.value()));
      }
      case Expr.BoolLiteral e -> {
        requireFeature(Feature.IF_ELSE, e.pos());
        yield new Typed(Type.BOOLEAN, Nodes.boolConst(e.value()));
      }
      case Expr.NullLiteral e -> {
        requireFeature(Feature.VARIABLES, e.pos());
        yield new Typed(Type.NULL, Nodes.nullRef());
      }
      case Expr.Name e -> name(e, assigned);
      case Expr.Call e -> turretCall(e, assigned);
      case Expr.MethodCall e -> methodCall(e, assigned);
      case Expr.FieldAccess e -> {
        expression(e.receiver(), assigned);
        report(ErrorCode.FIELD_ACCESS, e.pos(), e.name());
        yield Typed.ERROR;
      }
      case Expr.Unary e -> unary(e, assigned);
      case Expr.Binary e -> binary(e, assigned);
      case Expr.Assign e -> {
        report(ErrorCode.ASSIGNMENT_NOT_ALLOWED_HERE, e.pos());
        expression(e.value(), assigned);
        yield Typed.ERROR;
      }
      case Expr.IncDec e -> {
        report(ErrorCode.ASSIGNMENT_NOT_ALLOWED_HERE, e.pos());
        yield Typed.ERROR;
      }
      case Expr.Invalid e -> Typed.ERROR;
    };
  }

  private Typed name(Expr.Name e, BitSet assigned) {
    Var var = lookup(e.name());
    if (var != null) {
      requireInitialized(var, assigned, e.pos());
      return switch (var.type()) {
        case INT -> new Typed(Type.INT, Nodes.intLocal(var.slot()));
        case BOOLEAN -> new Typed(Type.BOOLEAN, Nodes.boolLocal(var.slot()));
        case ENEMY -> new Typed(Type.ENEMY, Nodes.refLocal(var.slot()));
        case ENEMY_LIST -> new Typed(Type.ENEMY_LIST, Nodes.refLocal(var.slot()));
        default -> Typed.ERROR;
      };
    }
    Optional<AmmoType> ammo = api.ammoConstant(e.name());
    if (ammo.isPresent()) {
      return new Typed(Type.AMMO, ammo.get());
    }
    report(ErrorCode.UNKNOWN_SYMBOL, e.pos(), e.name());
    return Typed.ERROR;
  }

  private Typed turretCall(Expr.Call e, BitSet assigned) {
    List<Typed> args = arguments(e.args(), assigned);
    Optional<ApiFunction> found = api.turretCall(e.name());
    if (found.isEmpty() && methods.containsKey(e.name())) {
      return libraryCall(e, methods.get(e.name()), args);
    }
    if (found.isEmpty()) {
      report(ErrorCode.UNKNOWN_METHOD, e.pos(), e.name());
      return Typed.ERROR;
    }
    ApiFunction function = found.get();
    requireUnlocked(function, e.pos());
    if (!argumentsMatch(function, args, e.args(), e.pos())) {
      return errorOrVoid(function);
    }
    int line = e.pos().line();
    return switch (function) {
      case FIRE -> new Typed(Type.VOID, Nodes.fire(line));
      case RELOAD -> new Typed(Type.VOID, Nodes.reload(line));
      case AMMO -> new Typed(Type.INT, Nodes.ammo());
      case HEAT -> new Typed(Type.INT, Nodes.heat());
      case HAS_ENEMIES -> new Typed(Type.BOOLEAN, Nodes.hasEnemies());
      case NEAREST_ENEMY -> new Typed(Type.ENEMY, Nodes.nearestEnemy());
      case TARGET -> new Typed(Type.ENEMY, Nodes.target());
      case ENEMIES -> new Typed(Type.ENEMY_LIST, Nodes.enemies());
      case AIM -> new Typed(Type.VOID, Nodes.aim(args.get(0).asRef(), line));
      case USE_AMMO -> new Typed(Type.VOID, Nodes.useAmmo((AmmoType) args.get(0).node()));
      case HEALTH, SPEED, ARMORED, MUTANT, DISTANCE_TO_CORE ->
          throw new IllegalStateException("Enemy call resolved as turret call: " + function);
    };
  }

  private Typed methodCall(Expr.MethodCall e, BitSet assigned) {
    Typed receiver = expression(e.receiver(), assigned);
    List<Typed> args = arguments(e.args(), assigned);
    if (receiver.type() == Type.ERROR) {
      return Typed.ERROR;
    }
    if (receiver.type() == Type.ENEMY_LIST) {
      return listCall(e, receiver, args);
    }
    return enemyCall(e, receiver, args);
  }

  /**
   * {@code size()}, {@code get(i)} and {@code isEmpty()}: Java's own names, not the theme's,
   * because they belong to {@code List}.
   */
  private Typed listCall(Expr.MethodCall e, Typed receiver, List<Typed> args) {
    int line = e.pos().line();
    int expected =
        switch (e.name()) {
          case "size", "isEmpty" -> 0;
          case "get" -> 1;
          default -> -1;
        };
    if (expected < 0) {
      report(ErrorCode.UNKNOWN_ENEMY_METHOD, e.pos(), e.name(), name(Type.ENEMY_LIST));
      return Typed.ERROR;
    }
    if (args.size() != expected) {
      report(ErrorCode.WRONG_ARGUMENT_COUNT, e.pos(), e.name(), expected, args.size());
      return Typed.ERROR;
    }
    RefEval list = receiver.asRef();
    return switch (e.name()) {
      case "size" -> new Typed(Type.INT, Nodes.listSize(list, line));
      case "isEmpty" -> new Typed(Type.BOOLEAN, Nodes.listIsEmpty(list, line));
      default -> {
        Typed index = args.get(0);
        if (index.type() == Type.ERROR) {
          yield Typed.ERROR;
        }
        if (index.type() != Type.INT) {
          report(
              ErrorCode.ARGUMENT_TYPE_MISMATCH,
              e.args().get(0).pos(),
              e.name(),
              name(Type.INT),
              name(index.type()));
          yield Typed.ERROR;
        }
        yield new Typed(Type.ENEMY, Nodes.listGet(list, index.asInt(), line));
      }
    };
  }

  private Typed enemyCall(Expr.MethodCall e, Typed receiver, List<Typed> args) {
    if (receiver.type() != Type.ENEMY) {
      report(ErrorCode.BAD_RECEIVER, e.pos(), name(receiver.type()));
      return Typed.ERROR;
    }
    Optional<ApiFunction> found = api.enemyCall(e.name());
    if (found.isEmpty()) {
      report(ErrorCode.UNKNOWN_ENEMY_METHOD, e.pos(), e.name(), api.enemyTypeName());
      return Typed.ERROR;
    }
    ApiFunction function = found.get();
    requireUnlocked(function, e.pos());
    if (!argumentsMatch(function, args, e.args(), e.pos())) {
      return errorOrVoid(function);
    }
    RefEval enemy = receiver.asRef();
    int line = e.pos().line();
    return switch (function) {
      case HEALTH -> new Typed(Type.INT, Nodes.health(enemy, line));
      case SPEED -> new Typed(Type.INT, Nodes.speed(enemy, line));
      case DISTANCE_TO_CORE -> new Typed(Type.INT, Nodes.distanceToCore(enemy, line));
      case ARMORED -> new Typed(Type.BOOLEAN, Nodes.armored(enemy, line));
      case MUTANT -> new Typed(Type.BOOLEAN, Nodes.mutant(enemy, line));
      default -> throw new IllegalStateException("Turret call resolved as enemy call: " + function);
    };
  }

  private List<Typed> arguments(List<Expr> args, BitSet assigned) {
    List<Typed> typed = new ArrayList<>();
    for (Expr arg : args) {
      typed.add(expression(arg, assigned));
    }
    return typed;
  }

  private boolean argumentsMatch(
      ApiFunction function, List<Typed> args, List<Expr> exprs, Pos pos) {
    List<ValueType> parameters = function.parameters();
    if (args.size() != parameters.size()) {
      report(
          ErrorCode.WRONG_ARGUMENT_COUNT, pos, api.name(function), parameters.size(), args.size());
      return false;
    }
    boolean ok = true;
    for (int i = 0; i < args.size(); i++) {
      Type expected = type(parameters.get(i));
      Type found = args.get(i).type();
      if (found == Type.ERROR) {
        ok = false;
      } else if (!assignable(found, expected)) {
        report(
            ErrorCode.ARGUMENT_TYPE_MISMATCH,
            exprs.get(i).pos(),
            api.name(function),
            name(expected),
            name(found));
        ok = false;
      }
    }
    return ok;
  }

  /** After a bad call, keep the call's type when it is void so statements stay quiet. */
  private static Typed errorOrVoid(ApiFunction function) {
    return function.returnType() == ValueType.VOID
        ? new Typed(Type.VOID, Nodes.empty())
        : Typed.ERROR;
  }

  private Typed unary(Expr.Unary e, BitSet assigned) {
    Typed operand = expression(e.operand(), assigned);
    if (e.op() == Expr.UnaryOp.NOT) {
      requireFeature(Feature.LOGIC_OPS, e.pos());
      if (!expect(operand, Type.BOOLEAN, e)) {
        return new Typed(Type.BOOLEAN, null);
      }
      return new Typed(Type.BOOLEAN, Nodes.not(operand.asBool()));
    }
    requireFeature(Feature.VARIABLES, e.pos());
    if (!expect(operand, Type.INT, e)) {
      return new Typed(Type.INT, null);
    }
    IntEval value = operand.asInt();
    return new Typed(Type.INT, e.op() == Expr.UnaryOp.MINUS ? Nodes.negate(value) : value);
  }

  private boolean expect(Typed operand, Type type, Expr.Unary e) {
    if (operand.type() == type) {
      return true;
    }
    if (operand.type() != Type.ERROR) {
      report(ErrorCode.BAD_OPERAND_TYPE, e.pos(), e.op().symbol(), name(operand.type()));
    }
    return false;
  }

  private Typed binary(Expr.Binary e, BitSet assigned) {
    Typed left = expression(e.left(), assigned);
    Typed right = expression(e.right(), assigned);
    Expr.BinaryOp op = e.op();
    switch (op) {
      case OR, AND -> {
        requireFeature(Feature.LOGIC_OPS, e.pos());
        if (!operands(e, left, right, Type.BOOLEAN)) {
          return new Typed(Type.BOOLEAN, null);
        }
        BoolEval node =
            op == Expr.BinaryOp.AND
                ? Nodes.and(left.asBool(), right.asBool())
                : Nodes.or(left.asBool(), right.asBool());
        return new Typed(Type.BOOLEAN, node);
      }
      case EQ, NE -> {
        requireFeature(Feature.IF_ELSE, e.pos());
        return equality(e, left, right);
      }
      case LT, GT, LE, GE -> {
        requireFeature(Feature.IF_ELSE, e.pos());
        if (!operands(e, left, right, Type.INT)) {
          return new Typed(Type.BOOLEAN, null);
        }
        return new Typed(Type.BOOLEAN, Nodes.compareInts(op, left.asInt(), right.asInt()));
      }
      default -> {
        requireFeature(Feature.VARIABLES, e.pos());
        if (!operands(e, left, right, Type.INT)) {
          return new Typed(Type.INT, null);
        }
        return new Typed(
            Type.INT, Nodes.arithmetic(op, left.asInt(), right.asInt(), e.pos().line()));
      }
    }
  }

  private Typed equality(Expr.Binary e, Typed left, Typed right) {
    boolean negate = e.op() == Expr.BinaryOp.NE;
    Type l = left.type();
    Type r = right.type();
    if (l == Type.ERROR || r == Type.ERROR) {
      return new Typed(Type.BOOLEAN, null);
    }
    if (l == Type.INT && r == Type.INT) {
      BoolEval node = Nodes.compareInts(e.op(), left.asInt(), right.asInt());
      return new Typed(Type.BOOLEAN, node);
    }
    if (l == Type.BOOLEAN && r == Type.BOOLEAN) {
      return new Typed(Type.BOOLEAN, Nodes.equalBools(negate, left.asBool(), right.asBool()));
    }
    if (l.isReference() && r.isReference() && (l == r || l == Type.NULL || r == Type.NULL)) {
      return new Typed(Type.BOOLEAN, Nodes.equalRefs(negate, left.asRef(), right.asRef()));
    }
    report(ErrorCode.BAD_OPERAND_TYPES, e.pos(), e.op().symbol(), name(l), name(r));
    return new Typed(Type.BOOLEAN, null);
  }

  /** Whether both operands have {@code type}; reports a mismatch unless already an error. */
  private boolean operands(Expr.Binary e, Typed left, Typed right, Type type) {
    if (left.type() == type && right.type() == type) {
      return true;
    }
    if (left.type() != Type.ERROR && right.type() != Type.ERROR) {
      report(
          ErrorCode.BAD_OPERAND_TYPES,
          e.pos(),
          e.op().symbol(),
          name(left.type()),
          name(right.type()));
    }
    return false;
  }

  // ------------------------------------------------------------------ helpers

  private Var lookup(String name) {
    for (Map<String, Var> scope : scopes) {
      Var var = scope.get(name);
      if (var != null) {
        return var;
      }
    }
    return null;
  }

  private Var declare(String name, Type type) {
    int slot =
        switch (type) {
          case ENEMY, ENEMY_LIST -> current.refSlots++;
          case INT, BOOLEAN -> current.intSlots++;
          default -> -1;
        };
    Var var = new Var(name, type, nextVarId++, slot);
    scopes.peek().put(name, var);
    return var;
  }

  private void requireInitialized(Var var, BitSet assigned, Pos pos) {
    if (!assigned.get(var.id())) {
      report(ErrorCode.UNINITIALIZED_VARIABLE, pos, var.name());
      // Report once per variable, like javac does per use site; marking it avoids repeats.
      assigned.set(var.id());
    }
  }

  private void requireAssignable(Type found, Type expected, Pos pos) {
    if (!assignable(found, expected)) {
      report(ErrorCode.TYPE_MISMATCH, pos, name(expected), name(found));
    }
  }

  private static boolean assignable(Type found, Type expected) {
    return found == expected
        || found == Type.ERROR
        || expected == Type.ERROR
        || ((expected == Type.ENEMY || expected == Type.ENEMY_LIST) && found == Type.NULL);
  }

  /** Reports a locked feature once per line, so one line does not get the same error twice. */
  private void requireFeature(Feature feature, Pos pos) {
    if (!unlocks.has(feature) && reportedLocks.add(pos.line() + ":" + feature)) {
      report(ErrorCode.FEATURE_LOCKED, pos, feature);
    }
  }

  private void requireUnlocked(ApiFunction function, Pos pos) {
    Requirement requirement = api.requirement(function);
    if (unlocks.satisfies(requirement)) {
      return;
    }
    switch (requirement) {
      case Requirement.NeedsFeature needs -> {
        if (reportedLocks.add(pos.line() + ":" + needs.feature())) {
          report(ErrorCode.API_LOCKED_FEATURE, pos, api.name(function), needs.feature());
        }
      }
      case Requirement.NeedsWave needs ->
          report(ErrorCode.API_LOCKED_WAVE, pos, api.name(function), needs.wave());
      case Requirement.Free free -> {}
    }
  }

  private void report(ErrorCode code, Pos pos, Object... args) {
    diagnostics.report(code, pos.line(), pos.column(), args);
  }

  private static Type type(ValueType valueType) {
    return switch (valueType) {
      case INT -> Type.INT;
      case BOOLEAN -> Type.BOOLEAN;
      case ENEMY -> Type.ENEMY;
      case ENEMY_LIST -> Type.ENEMY_LIST;
      case AMMO -> Type.AMMO;
      case VOID -> Type.VOID;
    };
  }

  /** How a type is written in messages; game types use their data-driven names. */
  private String name(Type type) {
    return switch (type) {
      case INT -> "int";
      case BOOLEAN -> "boolean";
      case ENEMY -> api.enemyTypeName();
      case ENEMY_LIST -> LIST_TYPE + "<" + api.enemyTypeName() + ">";
      case AMMO -> api.ammoTypeName();
      case NULL -> "null";
      case VOID -> "void";
      case ERROR -> "?";
    };
  }

  /** A generic type as written, for messages. */
  private static String describe(TypeRef type) {
    return switch (type) {
      case TypeRef.IntType t -> "int";
      case TypeRef.BooleanType t -> "boolean";
      case TypeRef.VoidType t -> "void";
      case TypeRef.Named t -> t.name();
      case TypeRef.Generic t -> t.name() + "<" + describe(t.argument()) + ">";
      case TypeRef.Invalid t -> "?";
    };
  }

  /** Source-like text of an assignment target, for messages. */
  private static String describe(Expr target) {
    return switch (target) {
      case Expr.Call c -> c.name() + "()";
      case Expr.MethodCall c -> c.name() + "()";
      case Expr.FieldAccess f -> f.name();
      case Expr.IntLiteral l -> Integer.toString(l.value());
      case Expr.BoolLiteral l -> Boolean.toString(l.value());
      case Expr.NullLiteral l -> "null";
      default -> "?";
    };
  }
}
