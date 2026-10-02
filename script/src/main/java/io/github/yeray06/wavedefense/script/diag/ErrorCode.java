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

  /** A bug in the compiler, not in the player's code. */
  INTERNAL_ERROR
}
