package io.github.yeray06.wavedefense.script.diag;

/**
 * Every compile error. The text shown to the player lives in {@code i18n/messages.properties} under
 * {@code script.error.<NAME>}; the comment says what each argument is.
 */
public enum ErrorCode {
  // Lexical
  /** {0} the character. */
  UNEXPECTED_CHARACTER,
  UNTERMINATED_COMMENT,
  /** {0} the literal. */
  INTEGER_TOO_LARGE,
  /** {0} the literal (hex, octal, binary or with a suffix). */
  NUMBER_FORMAT_NOT_SUPPORTED,
  /** {0} the literal. */
  DECIMAL_NOT_SUPPORTED,
  STRING_NOT_SUPPORTED,
  CHAR_NOT_SUPPORTED,

  // Syntax
  /** {0} expected token, {1} found token. */
  EXPECTED_TOKEN,
  /** {0} found token. */
  EXPECTED_EXPRESSION,
  /** {0} found token. */
  EXPECTED_NAME,
  ELSE_WITHOUT_IF,
  NOT_A_STATEMENT,
  DECLARATION_NOT_ALLOWED_HERE,
  /** {0} the limit. */
  NESTING_TOO_DEEP,
  /** {0} the keyword. */
  UNSUPPORTED_KEYWORD,
  /** {0} the operator. */
  UNSUPPORTED_OPERATOR,
  CAST_NOT_SUPPORTED,
  /** {0} the type: a Java type, or a game type that cannot hold variables. */
  UNSUPPORTED_TYPE,
  /** {0} the {@link io.github.yeray06.wavedefense.script.api.Feature}. */
  FEATURE_LOCKED,
  /** {@code int[]} or {@code a[0]}: the game's lists come from {@code enemigos()}. */
  ARRAYS_NOT_SUPPORTED,
  /** {0} the modifier ({@code public}, {@code static}…). */
  MODIFIER_NOT_ALLOWED,
  /** Something other than a method at the top of the library. */
  STATEMENT_OUTSIDE_METHOD,

  // Semantic
  /** {0} the call name, {1} the {@link io.github.yeray06.wavedefense.script.api.Feature}. */
  API_LOCKED_FEATURE,
  /** {0} the call name, {1} the wave number. */
  API_LOCKED_WAVE,
  /** {0} the name. */
  UNKNOWN_SYMBOL,
  /** {0} the type name. */
  UNKNOWN_TYPE,
  /** {0} the call name. */
  UNKNOWN_METHOD,
  /** {0} the call name, {1} the enemy type name. */
  UNKNOWN_ENEMY_METHOD,
  /** {0} the receiver's type. */
  BAD_RECEIVER,
  /** {0} the field name. */
  FIELD_ACCESS,
  /** {0} the variable. */
  DUPLICATE_VARIABLE,
  /** {0} the variable. */
  UNINITIALIZED_VARIABLE,
  /** {0} expected type, {1} found type. */
  TYPE_MISMATCH,
  /** {0} the operator, {1} left type, {2} right type. */
  BAD_OPERAND_TYPES,
  /** {0} the operator, {1} the operand type. */
  BAD_OPERAND_TYPE,
  /** {0} the call name, {1} expected count, {2} found count. */
  WRONG_ARGUMENT_COUNT,
  /** {0} the call name, {1} expected type, {2} found type. */
  ARGUMENT_TYPE_MISMATCH,
  /** {0} what the code tried to assign to. */
  CANNOT_ASSIGN,
  ASSIGNMENT_NOT_ALLOWED_HERE,
  /** {0} lines used, {1} the limit. */
  MEMORY_EXCEEDED,
  /** {0} the type of what a for-each tried to go through. */
  FOR_EACH_NOT_A_LIST,
  /** {0} the type of the for-each variable, {1} the type of the list's elements. */
  FOR_EACH_WRONG_TYPE,

  // Methods
  /** A method in a turret's script or inside another method: methods go in the library. */
  METHOD_NOT_ALLOWED_HERE,
  /** {0} the method name. */
  DUPLICATE_METHOD,
  /** {0} the method name, which is already one of the game's calls. */
  METHOD_NAME_TAKEN,
  /** {0} the method name. */
  MISSING_RETURN,
  /** {@code return value;} in a void method or in a turret's script. */
  UNEXPECTED_RETURN_VALUE,
  /** {0} the type the method returns. */
  MISSING_RETURN_VALUE,
  /** {0} the method name. */
  LIBRARY_HAS_ERRORS,

  /** A bug in the compiler, not in the player's code. */
  INTERNAL_ERROR
}
