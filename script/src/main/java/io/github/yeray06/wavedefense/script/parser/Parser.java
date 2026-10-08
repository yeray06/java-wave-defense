package io.github.yeray06.wavedefense.script.parser;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.ast.Expr;
import io.github.yeray06.wavedefense.script.ast.Pos;
import io.github.yeray06.wavedefense.script.ast.Stmt;
import io.github.yeray06.wavedefense.script.ast.TypeRef;
import io.github.yeray06.wavedefense.script.diag.Diagnostics;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import io.github.yeray06.wavedefense.script.lexer.Lexer;
import io.github.yeray06.wavedefense.script.lexer.Token;
import io.github.yeray06.wavedefense.script.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for the player's script: a method body, with Java's precedences.
 *
 * <p>Syntax the game knows but cannot run yet ({@code try}, methods, classes) is reported as locked
 * and skipped whole, never half-interpreted. Syntax it runs ({@code if}, {@code while}, {@code
 * for}…) is always parsed; whether the player owns it is the checker's business. After an error the
 * parser resynchronizes at the end of the statement so several errors can be shown at once.
 */
public final class Parser {

  /** Thrown once the nesting limit is hit; parsing stops there. */
  private static final class TooDeep extends RuntimeException {
    private static final long serialVersionUID = 1L;

    TooDeep() {
      super(null, null, false, false);
    }
  }

  private final List<Token> tokens;
  private final Diagnostics diagnostics;
  private final int maxDepth;
  private int index;
  private int depth;

  private Parser(List<Token> tokens, Diagnostics diagnostics, int maxDepth) {
    this.tokens = tokens;
    this.diagnostics = diagnostics;
    this.maxDepth = maxDepth;
  }

  /**
   * Parses a whole script. Never throws for bad input: problems go to {@code diagnostics}.
   *
   * @param tokens tokens ending with {@link TokenKind#EOF}
   */
  public static List<Stmt> parse(List<Token> tokens, Diagnostics diagnostics, int maxDepth) {
    Parser parser = new Parser(tokens, diagnostics, maxDepth);
    List<Stmt> statements = new ArrayList<>();
    try {
      parser.statementsUntil(TokenKind.EOF, statements);
    } catch (TooDeep e) {
      // Already reported; what was parsed so far is kept for the memory counter.
    }
    return List.copyOf(statements);
  }

  // ---------------------------------------------------------------- statements

  private void statementsUntil(TokenKind end, List<Stmt> out) {
    while (!at(end) && !at(TokenKind.EOF)) {
      int before = index;
      Stmt statement = statement(false);
      if (statement != null) {
        out.add(statement);
      }
      if (index == before) {
        advance();
      }
    }
  }

  /**
   * @param nested whether this is the direct body of an {@code if}/{@code while} without braces
   * @return the statement, or {@code null} if it was skipped after an error
   */
  private Stmt statement(boolean nested) {
    enter();
    try {
      Token t = peek();
      return switch (t.kind()) {
        case LBRACE -> block();
        case IF -> ifStatement();
        case WHILE -> whileStatement();
        case SEMICOLON -> {
          advance();
          yield new Stmt.Empty(pos(t));
        }
        case ELSE -> {
          report(ErrorCode.ELSE_WITHOUT_IF, t);
          advance();
          yield statement(nested);
        }
        case FOR -> forStatement();
        case TRY, CATCH, FINALLY -> {
          lockedTry(t);
          yield null;
        }
        case CLASS, MODIFIER -> {
          lockedDeclaration(t);
          yield null;
        }
        case VOID -> {
          report(ErrorCode.FEATURE_LOCKED, t, Feature.METHODS);
          skipDeclarationBody();
          yield null;
        }
        case RETURN -> {
          report(ErrorCode.FEATURE_LOCKED, t, Feature.METHODS);
          skipConstruct();
          yield null;
        }
        case DO, OTHER_KEYWORD -> {
          report(ErrorCode.UNSUPPORTED_KEYWORD, t, t.text());
          advance();
          skipConstruct();
          yield null;
        }
        case INT, BOOLEAN, OTHER_PRIMITIVE -> declaration(nested);
        case IDENTIFIER -> {
          yield startsDeclaration() ? declaration(nested) : expressionStatement();
        }
        default -> expressionStatement();
      };
    } finally {
      depth--;
    }
  }

  private Stmt block() {
    Token open = advance();
    List<Stmt> statements = new ArrayList<>();
    statementsUntil(TokenKind.RBRACE, statements);
    expect(TokenKind.RBRACE, "}");
    return new Stmt.Block(pos(open), statements);
  }

  private Stmt ifStatement() {
    Token keyword = advance();
    Expr condition = parenthesizedCondition();
    Stmt thenBranch = orEmpty(statement(true), keyword);
    Stmt elseBranch = null;
    if (at(TokenKind.ELSE)) {
      Token elseToken = advance();
      elseBranch = orEmpty(statement(true), elseToken);
    }
    return new Stmt.If(pos(keyword), condition, thenBranch, elseBranch);
  }

  private Stmt whileStatement() {
    Token keyword = advance();
    Expr condition = parenthesizedCondition();
    Stmt body = orEmpty(statement(true), keyword);
    return new Stmt.While(pos(keyword), condition, body);
  }

  /**
   * {@code for (Enemigo e : enemigos())} or {@code for (init; condition; update)}. Both are always
   * parsed; the checker decides whether the player owns {@code for}.
   */
  private Stmt forStatement() {
    Token keyword = advance();
    expect(TokenKind.LPAREN, "(");
    if (forEachHeaderAhead()) {
      TypeRef type = type();
      Token name = advance();
      advance(); // :
      Expr iterable = expression();
      expect(TokenKind.RPAREN, ")");
      Stmt body = orEmpty(statement(true), keyword);
      return new Stmt.ForEach(pos(keyword), type, pos(name), name.text(), iterable, body);
    }
    List<Stmt> init = new ArrayList<>();
    if (!at(TokenKind.SEMICOLON)) {
      if (startsDeclaration()
          || at(TokenKind.INT)
          || at(TokenKind.BOOLEAN)
          || at(TokenKind.OTHER_PRIMITIVE)) {
        Stmt declaration = forDeclaration();
        if (declaration != null) {
          init.add(declaration);
        }
      } else {
        for (Expr e : statementExpressions()) {
          init.add(new Stmt.ExprStmt(e.pos(), e));
        }
      }
    }
    expect(TokenKind.SEMICOLON, ";");
    Expr condition = at(TokenKind.SEMICOLON) ? null : expression();
    expect(TokenKind.SEMICOLON, ";");
    List<Expr> update = at(TokenKind.RPAREN) ? List.of() : statementExpressions();
    expect(TokenKind.RPAREN, ")");
    Stmt body = orEmpty(statement(true), keyword);
    return new Stmt.For(pos(keyword), init, condition, update, body);
  }

  /** Whether the tokens after {@code for (} are {@code Type name :}. */
  private boolean forEachHeaderAhead() {
    int offset;
    if (at(TokenKind.INT) || at(TokenKind.BOOLEAN) || at(TokenKind.OTHER_PRIMITIVE)) {
      offset = 1;
    } else if (at(TokenKind.IDENTIFIER)) {
      offset = genericArgumentAhead(1) ? 4 : 1;
    } else {
      return false;
    }
    Token colon = tokens.get(Math.min(index + offset + 1, tokens.size() - 1));
    return peekKind(offset) == TokenKind.IDENTIFIER
        && colon.kind() == TokenKind.UNSUPPORTED_OPERATOR
        && colon.text().equals(":");
  }

  /** Comma-separated expressions that must each be a statement: a for's init or update. */
  private List<Expr> statementExpressions() {
    List<Expr> out = new ArrayList<>();
    while (true) {
      Expr expr = expression();
      if (!isStatementLike(expr)) {
        report(ErrorCode.NOT_A_STATEMENT, expr.pos());
      }
      out.add(expr);
      if (!at(TokenKind.COMMA)) {
        return out;
      }
      advance();
    }
  }

  private Expr parenthesizedCondition() {
    expect(TokenKind.LPAREN, "(");
    Expr condition = expression();
    expect(TokenKind.RPAREN, ")");
    return condition;
  }

  /** The declaration in a {@code for} header: no {@code ;} of its own, the header has it. */
  private Stmt forDeclaration() {
    return declaration(false, true);
  }

  private Stmt declaration(boolean nested) {
    return declaration(nested, false);
  }

  private Stmt declaration(boolean nested, boolean inForHeader) {
    Token start = peek();
    TypeRef type = type();
    if (at(TokenKind.LBRACKET)) {
      // Lists come from enemigos(); arrays are not part of the game.
      report(ErrorCode.ARRAYS_NOT_SUPPORTED, peek());
      while (at(TokenKind.LBRACKET)) {
        skipBalanced(TokenKind.LBRACKET, TokenKind.RBRACKET);
      }
      type = new TypeRef.Invalid(pos(start));
    }
    if (!at(TokenKind.IDENTIFIER)) {
      report(ErrorCode.EXPECTED_NAME, peek(), peek().display());
      syncToStatementEnd();
      return null;
    }
    if (peekKind(1) == TokenKind.LPAREN) {
      report(ErrorCode.FEATURE_LOCKED, start, Feature.METHODS);
      skipDeclarationBody();
      return null;
    }
    if (nested) {
      report(ErrorCode.DECLARATION_NOT_ALLOWED_HERE, start);
    }
    List<Stmt.Declarator> declarators = new ArrayList<>();
    while (true) {
      if (!at(TokenKind.IDENTIFIER)) {
        report(ErrorCode.EXPECTED_NAME, peek(), peek().display());
        syncToStatementEnd();
        return new Stmt.VarDecl(pos(start), type, declarators);
      }
      Token name = advance();
      Expr initializer = null;
      if (at(TokenKind.ASSIGN)) {
        advance();
        initializer = expression();
      }
      declarators.add(new Stmt.Declarator(pos(name), name.text(), initializer));
      if (!at(TokenKind.COMMA)) {
        break;
      }
      advance();
    }
    if (!inForHeader) {
      endStatement();
    }
    return new Stmt.VarDecl(pos(start), type, declarators);
  }

  /**
   * Whether a declaration starts here: {@code Type name}, {@code Type[] name} or {@code A<B> c}.
   */
  private boolean startsDeclaration() {
    if (!at(TokenKind.IDENTIFIER)) {
      return false;
    }
    return peekKind(1) == TokenKind.IDENTIFIER
        || (peekKind(1) == TokenKind.LBRACKET && peekKind(2) == TokenKind.RBRACKET)
        || (genericArgumentAhead(1) && peekKind(4) == TokenKind.IDENTIFIER);
  }

  /** Whether {@code <Name>} starts {@code offset} tokens ahead. */
  private boolean genericArgumentAhead(int offset) {
    return peekKind(offset) == TokenKind.LT
        && peekKind(offset + 1) == TokenKind.IDENTIFIER
        && peekKind(offset + 2) == TokenKind.GT;
  }

  private TypeRef type() {
    Token t = advance();
    return switch (t.kind()) {
      case INT -> new TypeRef.IntType(pos(t));
      case BOOLEAN -> new TypeRef.BooleanType(pos(t));
      case IDENTIFIER -> {
        if (!at(TokenKind.LT)) {
          yield new TypeRef.Named(pos(t), t.text());
        }
        advance(); // <
        TypeRef argument = type();
        expect(TokenKind.GT, ">");
        yield new TypeRef.Generic(pos(t), t.text(), argument);
      }
      default -> {
        report(ErrorCode.UNSUPPORTED_TYPE, t, t.text());
        yield new TypeRef.Invalid(pos(t));
      }
    };
  }

  private Stmt expressionStatement() {
    Token start = peek();
    int before = index;
    Expr expr = expression();
    if (expr instanceof Expr.Invalid && index == before) {
      advance();
      return null;
    }
    if (!isStatementLike(expr)) {
      report(ErrorCode.NOT_A_STATEMENT, expr.pos());
    }
    endStatement();
    return new Stmt.ExprStmt(pos(start), expr);
  }

  /** What Java accepts as an expression statement. */
  private static boolean isStatementLike(Expr expr) {
    return expr instanceof Expr.Call
        || expr instanceof Expr.MethodCall
        || expr instanceof Expr.Assign
        || expr instanceof Expr.IncDec
        || expr instanceof Expr.Invalid;
  }

  /** Consumes the closing {@code ;} or reports it missing. */
  private void endStatement() {
    if (at(TokenKind.SEMICOLON)) {
      advance();
      return;
    }
    Token previous = tokens.get(Math.max(0, index - 1));
    // Report right after the last token, like javac's "';' expected".
    diagnostics.report(
        ErrorCode.EXPECTED_TOKEN,
        previous.line(),
        previous.column() + previous.text().length(),
        ";",
        peek().display());
    // A missing ';' at the end of a line: the next line is probably fine, keep it.
    if (peek().line() == previous.line()) {
      syncToStatementEnd();
    }
  }

  // ------------------------------------------------------- locked constructs

  private void lockedTry(Token keyword) {
    report(ErrorCode.FEATURE_LOCKED, keyword, Feature.TRY_CATCH);
    advance();
    skipParensAndBlock();
    while (at(TokenKind.CATCH) || at(TokenKind.FINALLY)) {
      advance();
      skipParensAndBlock();
    }
  }

  private void skipParensAndBlock() {
    if (at(TokenKind.LPAREN)) {
      skipBalanced(TokenKind.LPAREN, TokenKind.RPAREN);
    }
    if (at(TokenKind.LBRACE)) {
      skipBalanced(TokenKind.LBRACE, TokenKind.RBRACE);
    }
  }

  /** A class, or a method written with modifiers ({@code public static void x()}). */
  private void lockedDeclaration(Token start) {
    int i = index;
    while (tokens.get(i).kind() == TokenKind.MODIFIER) {
      i++;
    }
    Feature feature = tokens.get(i).kind() == TokenKind.CLASS ? Feature.CLASSES : Feature.METHODS;
    report(ErrorCode.FEATURE_LOCKED, start, feature);
    skipDeclarationBody();
  }

  /** Skips a method or class declaration: header up to its body, then the body. */
  private void skipDeclarationBody() {
    while (!at(TokenKind.LBRACE)
        && !at(TokenKind.SEMICOLON)
        && !at(TokenKind.RBRACE)
        && !at(TokenKind.EOF)) {
      if (at(TokenKind.LPAREN)) {
        skipBalanced(TokenKind.LPAREN, TokenKind.RPAREN);
      } else {
        advance();
      }
    }
    if (at(TokenKind.LBRACE)) {
      skipBalanced(TokenKind.LBRACE, TokenKind.RBRACE);
    } else if (at(TokenKind.SEMICOLON)) {
      advance();
    }
  }

  /** Skips one statement-like construct: up to a {@code ;} or through one block. */
  private void skipConstruct() {
    while (!at(TokenKind.EOF) && !at(TokenKind.RBRACE)) {
      if (at(TokenKind.SEMICOLON)) {
        advance();
        return;
      }
      if (at(TokenKind.LBRACE)) {
        skipBalanced(TokenKind.LBRACE, TokenKind.RBRACE);
        return;
      }
      if (at(TokenKind.LPAREN)) {
        skipBalanced(TokenKind.LPAREN, TokenKind.RPAREN);
      } else {
        advance();
      }
    }
  }

  private void syncToStatementEnd() {
    while (!at(TokenKind.EOF) && !at(TokenKind.RBRACE)) {
      if (advance().kind() == TokenKind.SEMICOLON) {
        return;
      }
    }
  }

  private void skipBalanced(TokenKind open, TokenKind close) {
    int level = 0;
    do {
      if (at(open)) {
        level++;
      } else if (at(close)) {
        level--;
      }
      advance();
    } while (level > 0 && !at(TokenKind.EOF));
  }

  // --------------------------------------------------------------- expressions

  private Expr expression() {
    return assignment();
  }

  private Expr assignment() {
    enter();
    try {
      return assignmentBody();
    } finally {
      depth--;
    }
  }

  private Expr assignmentBody() {
    Expr left = conditional();
    Expr.AssignOp op =
        switch (peek().kind()) {
          case ASSIGN -> Expr.AssignOp.SET;
          case PLUS_ASSIGN -> Expr.AssignOp.ADD;
          case MINUS_ASSIGN -> Expr.AssignOp.SUB;
          case STAR_ASSIGN -> Expr.AssignOp.MUL;
          case SLASH_ASSIGN -> Expr.AssignOp.DIV;
          case PERCENT_ASSIGN -> Expr.AssignOp.REM;
          default -> null;
        };
    if (op == null) {
      return left;
    }
    Token operator = advance();
    Expr value = assignment();
    return new Expr.Assign(pos(operator), op, left, value);
  }

  /** Where unsupported binary operators ({@code ?:}, {@code &}, {@code <<}…) are caught. */
  private Expr conditional() {
    Expr expr = or();
    while (at(TokenKind.UNSUPPORTED_OPERATOR) && !isPrefixOnly(peek().text())) {
      Token operator = advance();
      report(ErrorCode.UNSUPPORTED_OPERATOR, operator, operator.text());
      or();
      expr = new Expr.Invalid(expr.pos());
    }
    return expr;
  }

  private Expr or() {
    Expr left = and();
    while (at(TokenKind.OR_OR)) {
      Token operator = advance();
      left = new Expr.Binary(pos(operator), Expr.BinaryOp.OR, left, and());
    }
    return left;
  }

  private Expr and() {
    Expr left = equality();
    while (at(TokenKind.AND_AND)) {
      Token operator = advance();
      left = new Expr.Binary(pos(operator), Expr.BinaryOp.AND, left, equality());
    }
    return left;
  }

  private Expr equality() {
    Expr left = relational();
    while (at(TokenKind.EQ) || at(TokenKind.NE)) {
      Token operator = advance();
      Expr.BinaryOp op = operator.kind() == TokenKind.EQ ? Expr.BinaryOp.EQ : Expr.BinaryOp.NE;
      left = new Expr.Binary(pos(operator), op, left, relational());
    }
    return left;
  }

  private Expr relational() {
    Expr left = additive();
    while (true) {
      Token t = peek();
      Expr.BinaryOp op =
          switch (t.kind()) {
            case LT -> Expr.BinaryOp.LT;
            case GT -> Expr.BinaryOp.GT;
            case LE -> Expr.BinaryOp.LE;
            case GE -> Expr.BinaryOp.GE;
            default -> null;
          };
      if (op != null) {
        advance();
        left = new Expr.Binary(pos(t), op, left, additive());
      } else if (t.kind() == TokenKind.OTHER_KEYWORD && t.text().equals("instanceof")) {
        advance();
        report(ErrorCode.UNSUPPORTED_OPERATOR, t, t.text());
        additive();
        left = new Expr.Invalid(left.pos());
      } else {
        return left;
      }
    }
  }

  private Expr additive() {
    Expr left = multiplicative();
    while (at(TokenKind.PLUS) || at(TokenKind.MINUS)) {
      Token operator = advance();
      Expr.BinaryOp op = operator.kind() == TokenKind.PLUS ? Expr.BinaryOp.ADD : Expr.BinaryOp.SUB;
      left = new Expr.Binary(pos(operator), op, left, multiplicative());
    }
    return left;
  }

  private Expr multiplicative() {
    Expr left = unary();
    while (true) {
      Token t = peek();
      Expr.BinaryOp op =
          switch (t.kind()) {
            case STAR -> Expr.BinaryOp.MUL;
            case SLASH -> Expr.BinaryOp.DIV;
            case PERCENT -> Expr.BinaryOp.REM;
            default -> null;
          };
      if (op == null) {
        return left;
      }
      advance();
      left = new Expr.Binary(pos(t), op, left, unary());
    }
  }

  private Expr unary() {
    enter();
    try {
      Token t = peek();
      switch (t.kind()) {
        case BANG -> {
          advance();
          return new Expr.Unary(pos(t), Expr.UnaryOp.NOT, unary());
        }
        case MINUS -> {
          advance();
          if (at(TokenKind.INT_LITERAL)) {
            // Folded so that -2147483648 is valid, as in Java.
            Token literal = advance();
            return postfix(new Expr.IntLiteral(pos(t), (int) -literal.value()));
          }
          return new Expr.Unary(pos(t), Expr.UnaryOp.MINUS, unary());
        }
        case PLUS -> {
          advance();
          return new Expr.Unary(pos(t), Expr.UnaryOp.PLUS, unary());
        }
        case PLUS_PLUS, MINUS_MINUS -> {
          advance();
          return new Expr.IncDec(pos(t), t.kind() == TokenKind.PLUS_PLUS, true, unary());
        }
        case UNSUPPORTED_OPERATOR -> {
          if (isPrefixOnly(t.text())) {
            advance();
            report(ErrorCode.UNSUPPORTED_OPERATOR, t, t.text());
            unary();
            return new Expr.Invalid(pos(t));
          }
          return postfix(primary());
        }
        case LPAREN -> {
          if (isPrimitive(peekKind(1)) && peekKind(2) == TokenKind.RPAREN) {
            report(ErrorCode.CAST_NOT_SUPPORTED, t);
            advance();
            advance();
            advance();
            unary();
            return new Expr.Invalid(pos(t));
          }
          return postfix(primary());
        }
        default -> {
          return postfix(primary());
        }
      }
    } finally {
      depth--;
    }
  }

  private Expr postfix(Expr expr) {
    while (true) {
      Token t = peek();
      if (t.kind() == TokenKind.DOT) {
        advance();
        if (!at(TokenKind.IDENTIFIER)) {
          report(ErrorCode.EXPECTED_NAME, peek(), peek().display());
          return new Expr.Invalid(expr.pos());
        }
        Token name = advance();
        expr =
            at(TokenKind.LPAREN)
                ? new Expr.MethodCall(pos(name), expr, name.text(), arguments())
                : new Expr.FieldAccess(pos(name), expr, name.text());
      } else if (t.kind() == TokenKind.PLUS_PLUS || t.kind() == TokenKind.MINUS_MINUS) {
        advance();
        expr = new Expr.IncDec(pos(t), t.kind() == TokenKind.PLUS_PLUS, false, expr);
      } else if (t.kind() == TokenKind.LBRACKET) {
        report(ErrorCode.ARRAYS_NOT_SUPPORTED, t);
        skipBalanced(TokenKind.LBRACKET, TokenKind.RBRACKET);
        expr = new Expr.Invalid(expr.pos());
      } else {
        return expr;
      }
    }
  }

  private Expr primary() {
    Token t = peek();
    switch (t.kind()) {
      case INT_LITERAL -> {
        advance();
        if (t.value() >= Lexer.INT_LITERAL_LIMIT) {
          report(ErrorCode.INTEGER_TOO_LARGE, t, t.text());
          return new Expr.Invalid(pos(t));
        }
        return new Expr.IntLiteral(pos(t), (int) t.value());
      }
      case ERROR_LITERAL -> {
        advance();
        return new Expr.Invalid(pos(t));
      }
      case TRUE, FALSE -> {
        advance();
        return new Expr.BoolLiteral(pos(t), t.kind() == TokenKind.TRUE);
      }
      case NULL -> {
        advance();
        return new Expr.NullLiteral(pos(t));
      }
      case IDENTIFIER -> {
        advance();
        return at(TokenKind.LPAREN)
            ? new Expr.Call(pos(t), t.text(), arguments())
            : new Expr.Name(pos(t), t.text());
      }
      case LPAREN -> {
        advance();
        Expr inner = expression();
        expect(TokenKind.RPAREN, ")");
        return inner;
      }
      case NEW -> {
        report(ErrorCode.FEATURE_LOCKED, t, Feature.CLASSES);
        advance();
        while (at(TokenKind.IDENTIFIER) || at(TokenKind.DOT) || isPrimitive(peek().kind())) {
          advance();
        }
        while (at(TokenKind.LPAREN) || at(TokenKind.LBRACKET) || at(TokenKind.LBRACE)) {
          TokenKind open = peek().kind();
          skipBalanced(open, closing(open));
        }
        return new Expr.Invalid(pos(t));
      }
      case THIS -> {
        report(ErrorCode.FEATURE_LOCKED, t, Feature.CLASSES);
        advance();
        return new Expr.Invalid(pos(t));
      }
      case UNSUPPORTED_OPERATOR -> {
        report(ErrorCode.UNSUPPORTED_OPERATOR, t, t.text());
        advance();
        return new Expr.Invalid(pos(t));
      }
      default -> {
        report(ErrorCode.EXPECTED_EXPRESSION, t, t.display());
        return new Expr.Invalid(pos(t));
      }
    }
  }

  private List<Expr> arguments() {
    advance(); // (
    List<Expr> args = new ArrayList<>();
    if (at(TokenKind.RPAREN)) {
      advance();
      return args;
    }
    while (true) {
      args.add(expression());
      if (!at(TokenKind.COMMA)) {
        break;
      }
      advance();
    }
    expect(TokenKind.RPAREN, ")");
    return args;
  }

  // ------------------------------------------------------------------ helpers

  private void enter() {
    if (++depth > maxDepth) {
      report(ErrorCode.NESTING_TOO_DEEP, peek(), maxDepth);
      throw new TooDeep();
    }
  }

  private Stmt orEmpty(Stmt statement, Token anchor) {
    return statement != null ? statement : new Stmt.Empty(pos(anchor));
  }

  private boolean expect(TokenKind kind, String text) {
    if (at(kind)) {
      advance();
      return true;
    }
    report(ErrorCode.EXPECTED_TOKEN, peek(), text, peek().display());
    return false;
  }

  private void report(ErrorCode code, Token at, Object... args) {
    diagnostics.report(code, at.line(), at.column(), args);
  }

  private void report(ErrorCode code, Pos at, Object... args) {
    diagnostics.report(code, at.line(), at.column(), args);
  }

  private Token peek() {
    return tokens.get(index);
  }

  private TokenKind peekKind(int offset) {
    return tokens.get(Math.min(index + offset, tokens.size() - 1)).kind();
  }

  private boolean at(TokenKind kind) {
    return peek().kind() == kind;
  }

  private Token advance() {
    Token t = peek();
    if (index < tokens.size() - 1) {
      index++;
    }
    return t;
  }

  private static Pos pos(Token t) {
    return new Pos(t.line(), t.column());
  }

  private static boolean isPrimitive(TokenKind kind) {
    return kind == TokenKind.INT || kind == TokenKind.BOOLEAN || kind == TokenKind.OTHER_PRIMITIVE;
  }

  private static boolean isPrefixOnly(String operator) {
    return operator.equals("~") || operator.equals("@");
  }

  private static TokenKind closing(TokenKind open) {
    return switch (open) {
      case LPAREN -> TokenKind.RPAREN;
      case LBRACKET -> TokenKind.RBRACKET;
      default -> TokenKind.RBRACE;
    };
  }
}
