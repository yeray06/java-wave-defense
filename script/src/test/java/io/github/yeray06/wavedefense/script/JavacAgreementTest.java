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
      import java.util.List;
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
        List<Enemigo> enemigos() { return List.of(); }
        void apuntar(Enemigo e) {}
        void usarMunicion(Municion m) {}
      }
      class Script extends Torreta {
      %s
        void ciclo() {
      %s
        }
      }
      """;

  /** A library and a turret's script that calls it, compiled together. */
  record WithLibrary(String library, String script) {
    @Override
    public String toString() {
      return (library + " | " + script).replace("\n", "⏎");
    }
  }

  @TempDir Path classes;

  static Stream<WithLibrary> bothAcceptWithLibrary() {
    return Stream.of(
        new WithLibrary("void rafaga() {\n  disparar();\n  disparar();\n}", "rafaga();"),
        new WithLibrary(
            "int doble(int x) {\n  return x * 2;\n}\nboolean grande(int x) {\n  return x > 10;\n}",
            "if (grande(doble(municion()))) disparar();"),
        new WithLibrary(
            """
            Enemigo masCercanoAlNucleo(List<Enemigo> lista) {
              Enemigo mejor = null;
              for (Enemigo e : lista) {
                if (mejor == null || e.distanciaAlNucleo() < mejor.distanciaAlNucleo()) {
                  mejor = e;
                }
              }
              return mejor;
            }
            """,
            "Enemigo e = masCercanoAlNucleo(enemigos());\nif (e != null) apuntar(e);"),
        new WithLibrary(
            "int f(int n) {\n  if (n <= 1) {\n    return 1;\n  }\n  return n * f(n - 1);\n}",
            "int x = f(4);"),
        new WithLibrary(
            "int a() {\n  return b();\n}\nint b() {\n  return 1;\n}", "int x = a() + b();"),
        new WithLibrary(
            "int signo(int x) {\n  if (x > 0) {\n    return 1;\n  } else {\n    return -1;\n  }\n}",
            ""),
        new WithLibrary("int nunca() {\n  while (true) {\n    disparar();\n  }\n}", ""),
        new WithLibrary(
            "int v(boolean b) {\n  int x;\n  if (b) {\n    x = 1;\n  } else {\n    return 0;\n  }\n  return x;\n}",
            ""),
        new WithLibrary("void nada() {\n}", "nada();\nreturn;"),
        new WithLibrary("void a(Enemigo e) {\n}", "a(null);"),
        new WithLibrary("", "if (!hayEnemigos()) {\n  return;\n}\ndisparar();"));
  }

  static Stream<WithLibrary> bothRejectWithLibrary() {
    return Stream.of(
        new WithLibrary("int a() {\n  if (hayEnemigos()) return 1;\n}", ""),
        new WithLibrary("void a() {\n  return 1;\n}", ""),
        new WithLibrary("int a() {\n  return;\n}", ""),
        new WithLibrary("int a() {\n  return true;\n}", ""),
        new WithLibrary("void a() {\n}\nvoid a() {\n}", ""),
        new WithLibrary("void a(int x, int x) {\n}", ""),
        new WithLibrary("void a(int x) {\n  int x = 1;\n}", ""),
        new WithLibrary("void a(int n) {\n}", "a();"),
        new WithLibrary("void a(int n) {\n}", "a(true);"),
        new WithLibrary("void a() {\n}", "int x = a();"),
        new WithLibrary("void a() {\n  int x = y;\n}", ""),
        new WithLibrary("void a() {\n  void b() {\n  }\n}", ""),
        new WithLibrary("", "return 1;"),
        new WithLibrary("void a(void x) {\n}", ""));
  }

  /** Valid Java that the game refuses in the library on purpose, with a clear message. */
  static Stream<WithLibrary> gameIsStricterWithLibrary() {
    return Stream.of(
        // Overloads: one name, one method, so the player always knows which one runs.
        new WithLibrary("void a(int x) {\n}\nvoid a(boolean b) {\n}", ""),
        // A method cannot hide one of the game's calls (javac takes it as an override).
        new WithLibrary("void disparar() {\n}", ""),
        // No modifiers: classes come later.
        new WithLibrary("static void a() {\n}", ""),
        new WithLibrary("", "final int x = 1;"),
        // No fields: the library is shared by every turret, its state would be too.
        new WithLibrary("int contador = 0;", ""),
        // javac folds 1 == 1 into true and sees an endless loop; the game only knows true.
        new WithLibrary("int a() {\n  while (1 == 1) {\n  }\n}", ""));
  }

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
        "for (int i = 0; i < 3; i++) {\n  disparar();\n}",
        "for (int i = 10, j = 0; i > j; i -= 2, j++) disparar();",
        "int a;\nfor (a = 0; a < 2; a++) disparar();\nint b = a;",
        "for (;;) {\n  disparar();\n}",
        """
        Enemigo objetivo = null;
        for (Enemigo e : enemigos()) {
          if (objetivo == null || e.distanciaAlNucleo() < objetivo.distanciaAlNucleo()) {
            objetivo = e;
          }
        }
        """,
        "List<Enemigo> l = enemigos();\nif (!l.isEmpty() && l.size() > 1) apuntar(l.get(0));",
        "List<Enemigo> l = null;\nboolean b = l == null;",
        "for (int i = 0; i < enemigos().size(); i++) apuntar(enemigos().get(i));",
        "for (int i = 0; i < 2; i++) {\n}\nfor (int i = 0; i < 2; i++) {\n}",
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
        "for (int x : enemigos()) {\n}",
        "for (Enemigo e : enemigoMasCercano()) {\n}",
        "for (int i = 0; i < 3; i++) {\n}\nint j = i;",
        "for (int i = 0; i < 3; i++) int x = i;",
        "Enemigo e = enemigos().get(true);",
        "int n = enemigos().size(1);",
        "List<Enemigo> l = 3;",
        "for (int i = 0; i + 1; i++) {\n}",
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
        // Lists of the game are List<Enemigo>; a raw List would give Object, and arrays are out.
        "List l = enemigos();",
        "int[] a = {1, 2};",
        // javac allows comparing a List with an Enemigo (an interface and a non-final class), but
        // it can never be true.
        "List<Enemigo> l = enemigos();\nEnemigo e = enemigoMasCercano();\nboolean b = l == e;",
        // Only size(), get() and isEmpty() of List.
        "boolean b = enemigos().contains(null);",
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

  /** The same leniency after a {@code return}: javac calls what follows unreachable. */
  static Stream<WithLibrary> gameIsMoreLenientWithLibrary() {
    return Stream.of(new WithLibrary("void a() {\n  return;\n  disparar();\n}", ""));
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

  @ParameterizedTest
  @MethodSource("bothAcceptWithLibrary")
  void javacAcceptsTheLibrariesTheGameAccepts(WithLibrary c) {
    assertTrue(game(c), () -> "game rejected:\n" + c);
    assertJavac(c.library(), c.script(), true);
  }

  @ParameterizedTest
  @MethodSource("bothRejectWithLibrary")
  void gameRejectsTheLibrariesJavacRejects(WithLibrary c) {
    assertFalse(game(c), () -> "game accepted:\n" + c);
    assertJavac(c.library(), c.script(), false);
  }

  @ParameterizedTest
  @MethodSource("gameIsStricterWithLibrary")
  void deliberateDeviationsInTheLibrary(WithLibrary c) {
    assertFalse(game(c), () -> "game accepted:\n" + c);
    assertJavac(c.library(), c.script(), true);
  }

  @ParameterizedTest
  @MethodSource("gameIsMoreLenientWithLibrary")
  void knownLenienciesInTheLibrary(WithLibrary c) {
    assertTrue(game(c), () -> "game rejected:\n" + c);
    assertJavac(c.library(), c.script(), false);
  }

  /** Whether the game takes both the library and the script. */
  private static boolean game(WithLibrary c) {
    LibraryResult library = Scripts.library(c.library());
    return library.succeeded() && Scripts.compile(c.script(), library.library()).succeeded();
  }

  private void assertJavac(String script, boolean shouldCompile) {
    assertJavac("", script, shouldCompile);
  }

  private void assertJavac(String library, String script, boolean shouldCompile) {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    assertNotNull(javac, "tests must run on a JDK");
    JavaFileObject source =
        new SimpleJavaFileObject(URI.create("string:///Script.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return STUB.formatted(library, script);
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
          "javac "
              + (compiled ? "accepted" : "rejected")
              + ":\n"
              + library
              + "\n"
              + script
              + "\n"
              + output);
    }
  }
}
