package io.github.yeray06.wavedefense.game.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.yeray06.wavedefense.game.GameTestData;
import io.github.yeray06.wavedefense.game.Headless;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.game.ui.FloatingWindow;
import io.github.yeray06.wavedefense.game.ui.UiSkin;
import io.github.yeray06.wavedefense.game.ui.Windows;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.Shop;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 9b: windows, output, debugger, career, trials and the manual's extras. */
class Phase9bPanelsTest {
  private interface Body {
    void run(GameData data, ScriptTexts names, Skin skin, Stage stage);
  }

  private static void withUi(Body body) {
    Headless.run(
        () -> {
          GameData data = GameTestData.load();
          Texts texts = Texts.load(Gdx.files.internal("i18n/messages"));
          Skin skin = UiSkin.create(Gdx.files.internal("fonts/JetBrainsMono-Regular.ttf"));
          Stage stage = new Stage(new ScreenViewport());
          body.run(data, new ScriptTexts(texts, data), skin, stage);
          stage.dispose();
          skin.dispose();
        });
  }

  private static List<Actor> all(Group root) {
    List<Actor> out = new ArrayList<>();
    for (Actor child : root.getChildren()) {
      out.add(child);
      if (child instanceof Group group) {
        out.addAll(all(group));
      }
    }
    return out;
  }

  private static TextButton button(Group root, String text) {
    for (Actor actor : all(root)) {
      if (actor instanceof TextButton b && b.getText().toString().equals(text)) {
        return b;
      }
    }
    return null;
  }

  private static String labels(Group root) {
    StringBuilder text = new StringBuilder();
    for (Actor actor : all(root)) {
      if (actor instanceof Label label && label.isVisible()) {
        text.append(label.getText()).append('\n');
      }
    }
    return text.toString();
  }

  private static void click(TextButton button) {
    button.fire(new ChangeListener.ChangeEvent());
  }

  /** Runs the wave until it ends or the debugger pauses it. */
  private static void run(WaveLoop loop, int ticks) {
    for (int i = 0; i < ticks && !loop.debugPaused(); i++) {
      if (loop.battle().turret(0).ammo() == 0) {
        loop.manualReload(0);
      }
      loop.tick();
    }
  }

  @Test
  void windowsOpenOncePerKeyFoldAndClose() {
    withUi(
        (data, names, skin, stage) -> {
          Windows windows = new Windows(stage, skin);
          FloatingWindow first = windows.open("a", "A", () -> new Label("x", skin), 300, 200);
          FloatingWindow again = windows.open("a", "A", () -> new Label("y", skin), 300, 200);
          assertEquals(first, again, "the same key brings the same window back");
          first.setFolded(true);
          assertTrue(first.getHeight() < 100);
          first.setFolded(false);
          assertEquals(200, first.getHeight(), 0.01);
          first.close();
          assertFalse(windows.isOpen("a"));
          assertNull(first.getStage());
        });
  }

  @Test
  void theOutputShowsWhatTheTurretsPrintAndTheirExceptions() {
    withUi(
        (data, names, skin, stage) -> {
          Progress progress = Progress.newExpertGame(data);
          WaveLoop loop = new WaveLoop(data, progress);
          assertTrue(
              loop.setScript(0, "imprimir(oleada());\nEnemigo e = null;\napuntar(e);").succeeded());
          OutputPanel output = new OutputPanel(loop, names, skin);
          stage.addActor(output);
          loop.deploy();
          for (int i = 0; i < 5; i++) {
            loop.tick();
            output.read();
          }
          stage.act(0.1f);
          String shown = output.shown();
          assertTrue(shown.contains("— Oleada 1 —"), shown);
          assertTrue(shown.contains("Torreta 1 · línea 1: 1"), shown);
          assertTrue(shown.contains("NullPointerException en la línea 3"), shown);
        });
  }

  @Test
  void theDebuggerWalksTheRecordedCycleAndShowsVariables() {
    withUi(
        (data, names, skin, stage) -> {
          WaveLoop loop = new WaveLoop(data, Progress.newExpertGame(data));
          assertTrue(loop.setScript(0, "int a = 4;\nint b = a * 2;\ndisparar();").succeeded());
          EditorPanel editor = new EditorPanel(loop, names, skin, data.map().turretSlots().size());
          DebuggerPanel debugger = new DebuggerPanel(loop, names, skin, editor);
          stage.addActor(editor);
          stage.addActor(debugger);
          stage.act(0);
          editor.editor(0).toggleBreakpoint(3);
          loop.deploy();
          run(loop, 1000);
          assertTrue(loop.debugPaused());
          stage.act(0);
          assertTrue(labels(debugger).contains("se ha parado en la línea 3"), labels(debugger));
          assertTrue(debugger.variablesShown().contains("a = 4"), debugger.variablesShown());
          assertTrue(debugger.variablesShown().contains("b = 8"), debugger.variablesShown());
          debugger.go(0);
          stage.act(0);
          assertEquals(
              "",
              debugger.variablesShown().contains("b =") ? "b" : "",
              "b is not declared at line 1");
          click(button(debugger, "Continuar"));
          assertFalse(loop.debugPaused());
          editor.editor(0).toggleBreakpoint(3);
          assertTrue(loop.breakpoints(0).isEmpty(), "a second click clears it");
        });
  }

  @Test
  void theCareerPanelListsAchievementsAndTheWorkshopNamesAndHats() {
    withUi(
        (data, names, skin, stage) -> {
          WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
          CareerPanel panel = new CareerPanel(data, loop, names, skin);
          stage.addActor(panel);
          stage.act(0);
          String text = labels(panel);
          assertTrue(text.contains("Primer día"), text);
          assertTrue(text.contains("Llevas 0 de 1"), text);
          assertTrue(text.contains("Taller"), text);
          loop.deploy();
          run(loop, 200_000);
          stage.act(0);
          assertTrue(loop.career().unlocked("FIRST_WAVE"));
          TextButton cap = button(panel, "Gorra");
          assertNotNull(cap, "an unlocked hat can be worn");
          cap.setChecked(true);
          assertEquals("FIRST_WAVE", loop.career().hat(0));
        });
  }

  @Test
  void probarShowsATrialReportWithoutChangingTheGame() {
    withUi(
        (data, names, skin, stage) -> {
          WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
          BriefingPanel briefing = new BriefingPanel(data, loop.progress(), names, skin, () -> {});
          List<Integer> tried = new ArrayList<>();
          briefing.setTrial(tried::add);
          briefing.setCareer(loop.career());
          briefing.show(1);
          click(button(briefing, "Probar"));
          assertEquals(List.of(1), tried);
          List<ReportLines.Line> lines =
              ReportLines.trial(loop.trial(1), names, data.balance().ticksPerSecond(), 1000);
          assertTrue(lines.get(0).text().startsWith("Prueba de la oleada 1"), lines.get(0).text());
        });
  }

  @Test
  void manualPagesHaveStoriesHintsAndLinks() {
    withUi(
        (data, names, skin, stage) -> {
          Progress progress = Progress.newGame(data);
          new Shop(data).buy(progress.copyWithStars(100), Feature.IF_ELSE);
          ManualPanel manual = new ManualPanel(data, progress, names, skin, Gdx.app.getClipboard());
          Windows windows = new Windows(stage, skin);
          manual.setWindows(windows);
          stage.addActor(manual);
          manual.showPage("SYNTAX_IF_ELSE");
          String text = labels(manual);
          assertTrue(text.contains("gasta balas contra el aire"), "the story");
          assertFalse(text.contains("Pista:"), "the hint waits for its button");
          click(button(manual, "Mostrar pista"));
          assertTrue(labels(manual).contains("Pista:"));
          TextButton link = button(manual, names.texts().get("manual.page.SYNTAX_LOGIC_OPS.title"));
          assertNotNull(link, "see also");
          click(button(manual, "Abrir en ventana"));
          assertTrue(windows.isOpen("manual-SYNTAX_IF_ELSE"));
          click(link);
          assertEquals("SYNTAX_LOGIC_OPS", manual.currentPage().id());
        });
  }

  @Test
  void aPoppedOutEditorSharesTheTextAndTheBreakpoints() {
    withUi(
        (data, names, skin, stage) -> {
          WaveLoop loop = new WaveLoop(data, Progress.newGame(data));
          EditorPanel editor = new EditorPanel(loop, names, skin, data.map().turretSlots().size());
          Windows windows = new Windows(stage, skin);
          editor.setWindows(windows);
          stage.addActor(editor);
          stage.act(0);
          click(button(editor, "Separar en ventana"));
          assertTrue(windows.isOpen("code-0"));
          assertNotNull(editor.popped(0));
          editor.popped(0).toggleBreakpoint(1);
          BitSet expected = new BitSet();
          expected.set(1);
          assertEquals(expected, loop.breakpoints(0));
          assertEquals(expected, editor.editor(0).breakpoints(), "the tab shows it too");
          windows.close("code-0");
          assertNull(editor.popped(0));
        });
  }

  @Test
  void everyAchievementAndHatHasItsTexts() {
    withUi(
        (data, names, skin, stage) -> {
          for (var a : data.career().achievements()) {
            assertTrue(names.texts().has("achievement." + a.id() + ".name"), a.id());
            assertTrue(names.texts().has("achievement." + a.id() + ".description"), a.id());
          }
          for (var shape : io.github.yeray06.wavedefense.sim.data.CareerConfig.HatShape.values()) {
            assertTrue(names.texts().has("hat." + shape), shape.toString());
          }
        });
  }
}
