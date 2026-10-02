package io.github.yeray06.wavedefense.script;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The game teaches real Java, so the real compiler is the referee: whatever the game accepts, javac
 * must accept too, and the game must reject what javac rejects. javac is only used here, in tests;
 * the game never runs it (ARCHITECTURE.md §4.1).
 *
 * <p>The few things the game rejects on purpose although javac accepts them are listed in {@link
 * #gameIsStricter()}, and the one thing it lets through that javac rejects in {@link
 * #gameIsMoreLenient()}, so every deviation stays explicit.
 */
class JavacAgreementTest {
  private static final String STUB =
      """
      class Enemigo {
        int vida() { return 1; }
        int velocidad() { return 1; }
        boolean blindado() { return false; }
        int distanciaAlNucleo() { return 1; }
      }
      enum Municion { NORMAL, PERFORANTE }
      abstract class Torreta {
        static final Municion NORMAL = Municion.NORMAL;
        static final Municion PERFORANTE = Municion.PERFORANTE;
        void disparar() {}
        void recargar() {}
        int municion() { return 0; }
        int calor() { return 0; }
        boolean hayEnemigos() { return false; }
        Enemigo enemigoMasCercano() { return null; }
        Enemigo objetivo() { return null; }
        void apuntar(Enemigo e) {}
        void usarMunicion(Municion m) {}
      }
      class Script extends Torreta {
        void ciclo() {
      %s
        }
      }
      """;

  @TempDir Path classes;

  static Stream<String> bothAccept() {
    return Stream.of(
        "disparar();",
        "if (municion() == 0) {\n    recargar();\n} else {\n        disparar();\n}",
        "if (calor() < 80) disparar();",
        "if (calor() > 50) recargar(); else if (calor() > 10) disparar(); else disparar();",
        """
        Enemigo o = objetivo();
        if (o == null) {
          o = enemigoMasCercano();
          apuntar(o);
        }
        disparar();
        """,
        """
        Enemigo e = enemigoMasCercano();
        if (e != null && e.blindado() && calor() < 60) {
          usarMunicion(PERFORANTE);
        } else {
          usarMunicion(NORMAL);
        }
        """,
        "int n = 0;\nwhile (n < 3 && calor() < 80) {\n  disparar();\n  n++;\n}",
        "int x = 10;\nx -= 4;\nx *= 3;\nx /= 2;\nx %= 7;\nx += 3;\n--x;\n++x;\nx++;\nx--;",
        "int a = 1, b;\nb = a + 2 * -a;\nboolean c = !(a > b) || b == a;\nc = c != true;",
        "int m = -2147483648;\nint z = - -4 + +3;",
        "int x;\nif (hayEnemigos()) {\n  x = 1;\n} else {\n  x = 2;\n}\nint y = x;",
        "hayEnemigos();\nmunicion();\nenemigoMasCercano();",
        "{\n  int a = 1;\n}\n{\n  int a = 2;\n}",
        "boolean b = objetivo() == enemigoMasCercano();",
        "int daño = 1_000;",
        "while (true);",
        ";;\n// comentario\n/* otro */",
        "");
  }

  static Stream<String> bothReject() {
    return Stream.of(
        "int x = true;",
        "int x;\nint y = x;",
        "int x;\nif (hayEnemigos()) {\n  x = 1;\n}\nint y = x;",
        "int x = x + 1;",
        "int x = 1;\nint x = 2;",
        "int x = 1;\nif (true) {\n  int x = 2;\n}",
        "Enemigo e = 3;",
        "disparar(3);",
        "apuntar(3);",
        "int x = 1;\nx + 1;",
        "if (3) {\n}",
        "int x = disparar();",
        "boolean b = 1 < true;",
        "int x = !3;",
        "boolean b = true;\nb++;",
        "int x = 2147483648;",
        "Enemigo e = enemigoMasCercano();\nint v = e.vida;",
        "if (hayEnemigos()) int x = 1;",
        "else disparar();");
  }

  /** Valid Java the game refuses on purpose; each one has a clear message in the game. */
  static Stream<String> gameIsStricter() {
    return Stream.of(
        // Assignments and ++/-- only as statements: hides no logic inside conditions.
        "int x;\nif ((x = 3) > 2) disparar();",
        "int x = 1;\nint y = x++;",
        // Not sold in the shop.
        "while (true) {\n  break;\n}",
        "double d = 1;",
        "int x = (int) 3;",
        "int x = 0x1F;",
        "int x = 1 & 2;",
        "int x = true ? 1 : 2;",
        // Ammo constants are arguments, not variable types (D-07).
        "Municion m = NORMAL;",
        // The sandbox: nothing outside the turret API exists.
        "System.exit(0);");
  }

  /**
   * javac rejects code after an endless loop ("unreachable statement"). The game does not check
   * reachability: the loop raises BucleInfinitoException at run time, which teaches the same
   * lesson.
   */
  static Stream<String> gameIsMoreLenient() {
    return Stream.of("while (true) {\n}\ndisparar();");
  }

  @ParameterizedTest
  @MethodSource("bothAccept")
  void javacAcceptsWhatTheGameAccepts(String script) {
    assertTrue(Scripts.compile(script).succeeded(), () -> "game rejected:\n" + script);
    assertJavac(script, true);
  }

  @ParameterizedTest
  @MethodSource("bothReject")
  void gameRejectsWhatJavacRejects(String script) {
    assertFalse(Scripts.compile(script).succeeded(), () -> "game accepted:\n" + script);
    assertJavac(script, false);
  }

  @ParameterizedTest
  @MethodSource("gameIsStricter")
  void deliberateDeviationsAreValidJavaRejectedByTheGame(String script) {
    assertFalse(Scripts.compile(script).succeeded(), () -> "game accepted:\n" + script);
    assertJavac(script, true);
  }

  @ParameterizedTest
  @MethodSource("gameIsMoreLenient")
  void knownLenienciesAreRejectedByJavacOnly(String script) {
    assertTrue(Scripts.compile(script).succeeded(), () -> "game rejected:\n" + script);
    assertJavac(script, false);
  }

  private void assertJavac(String script, boolean shouldCompile) {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    assertNotNull(javac, "tests must run on a JDK");
    JavaFileObject source =
        new SimpleJavaFileObject(URI.create("string:///Script.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return STUB.formatted(script);
          }
        };
    StringWriter output = new StringWriter();
    boolean compiled =
        javac
            .getTask(
                output,
                null,
                diagnostic -> output.append(diagnostic.toString()).append('\n'),
                List.of("-d", classes.toString(), "-proc:none", "-Xlint:none"),
                null,
                List.of(source))
            .call();
    if (compiled != shouldCompile) {
      throw new AssertionError(
          "javac " + (compiled ? "accepted" : "rejected") + ":\n" + script + "\n" + output);
    }
  }
}
