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
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.yeray06.wavedefense.game.GameTestData;
import io.github.yeray06.wavedefense.game.Headless;
import io.github.yeray06.wavedefense.game.editor.CodeView;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.game.ui.UiSkin;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.data.EnemyKind;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.progress.WaveOutcome;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 5 panels: editor, shop, map, briefing, report and manual, on the headless backend. */
class PanelsTest {
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
    throw new AssertionError("no button " + text);
  }

  @Test
  void theEditorCompilesAsYouTypeAndOnlyCompilingCodeReachesTheTurret() {
    withUi(
        (data, names, skin, stage) -> {
          Progress progress = Progress.newGame(data).copyWithMoney(100_00);
          WaveLoop loop = new WaveLoop(data, progress);
          EditorPanel editor = new EditorPanel(loop, names, skin, 2);
          stage.addActor(editor);
          editor.act(0);
          assertTrue(editor.result().succeeded());
          assertEquals(1, editor.result().memoryUsed());

          editor.document(0).selectAll();
          editor.document(0).insert("if (hayEnemigos()) disparar();");
          editor.act(0);
          assertFalse(editor.result().succeeded(), "if/else is not bought");
          assertEquals("disparar();", loop.script(0), "the turret keeps its code");

          new io.github.yeray06.wavedefense.sim.progress.Shop(data).buy(progress, Feature.IF_ELSE);
          editor.purchased();
          editor.act(0);
          assertTrue(editor.result().succeeded());
          assertEquals("if (hayEnemigos()) disparar();", loop.script(0));
        });
  }

  @Test
  void theShopSellsTheNextPieceShowsItsCardAndKeepsTheRestLocked() {
    withUi(
        (data, names, skin, stage) -> {
          Progress progress = Progress.newGame(data).copyWithMoney(100_00);
          List<Object> bought = new ArrayList<>();
          ShopPanel shop = new ShopPanel(data, progress, names, skin, bought::add);
          stage.addActor(shop);
          shop.rebuild();
          TextButton buyIf = button(shop, names.texts().format("shop.buy", 25));
          assertFalse(buyIf.isDisabled());
          TextButton buyWhile = button(shop, names.texts().format("shop.buy", 200));
          assertTrue(buyWhile.isDisabled(), "while needs && first");

          buyIf.fire(new ChangeListener.ChangeEvent());
          assertTrue(progress.owns(Feature.IF_ELSE));
          assertEquals(List.of(Feature.IF_ELSE), bought);
          assertTrue(
              all(stage.getRoot()).stream().anyMatch(a -> a instanceof Dialog),
              "the purchase card is open");
          assertNotNull(button(shop, names.texts().get("shop.owned")));
        });
  }

  @Test
  void theMapLocksWavesNotReachedAndChoosesReachedOnes() {
    withUi(
        (data, names, skin, stage) -> {
          Progress progress = Progress.newGame(data);
          List<Integer> chosen = new ArrayList<>();
          MapPanel map = new MapPanel(data, progress, names.texts(), skin, chosen::add);
          stage.addActor(map);
          map.act(0);
          long locks =
              all(map).stream()
                  .filter(a -> a instanceof Image i && i.getDrawable() == skin.getDrawable("lock"))
                  .count();
          assertEquals(data.balance().lastWave() - 1, locks, "only wave 1 is reached");
          long flags =
              all(map).stream()
                  .filter(a -> a instanceof Image i && i.getDrawable() == skin.getDrawable("flag"))
                  .count();
          assertEquals(1, flags);
          float[] first = map.position(1);
          float[] boss = map.position(data.balance().lastWave());
          assertTrue(boss[1] > first[1], "the road goes up to the boss");
        });
  }

  @Test
  void theManualListsEveryPageLocksWhatIsNotOwnedAndCopiesItsExamples() {
    withUi(
        (data, names, skin, stage) -> {
          Progress progress = Progress.newGame(data).copyWithMoney(100_00);
          String[] copied = {null};
          Clipboard clipboard =
              new Clipboard() {
                @Override
                public boolean hasContents() {
                  return copied[0] != null;
                }

                @Override
                public String getContents() {
                  return copied[0];
                }

                @Override
                public void setContents(String content) {
                  copied[0] = content;
                }
              };
          ManualPanel manual = new ManualPanel(data, progress, names, skin, clipboard);
          stage.addActor(manual);
          manual.act(0);
          int pages = data.manual().pages().size();
          for (var page : data.manual().pages()) {
            assertNotNull(button(manual, manual.title(page)), page.id());
          }
          long locks = locks(manual, skin);
          assertTrue(locks > 0 && locks < pages, "some pages locked: " + locks);

          var aim = page(data, "CALL_AMMO");
          assertEquals(
              List.of(
                  names.texts().format("manual.locked.feature", names.feature(Feature.IF_ELSE))),
              manual.lockReasons(aim));
          new io.github.yeray06.wavedefense.sim.progress.Shop(data).buy(progress, Feature.IF_ELSE);
          manual.act(0);
          assertTrue(manual.lockReasons(aim).isEmpty(), "bought: unlocked");
          assertTrue(locks(manual, skin) < locks, "the index follows purchases");
          assertEquals(
              List.of(names.texts().format("manual.locked.enemy", 8)),
              manual.lockReasons(page(data, "ENEMY_SWARM")));

          button(manual, manual.title(aim)).fire(new ChangeListener.ChangeEvent());
          assertEquals("CALL_AMMO", manual.currentPage().id());
          button(manual, names.texts().get("manual.copy")).fire(new ChangeListener.ChangeEvent());
          assertEquals(aim.examples().get(0).code(), clipboard.getContents());
          assertNotNull(button(manual, names.texts().get("manual.copied")));

          button(manual, names.texts().get("manual.next")).fire(new ChangeListener.ChangeEvent());
          assertEquals("CALL_HEAT", manual.currentPage().id());
          button(manual, names.texts().get("manual.prev")).fire(new ChangeListener.ChangeEvent());
          assertEquals("CALL_AMMO", manual.currentPage().id());
          button(manual, names.texts().get("manual.index")).fire(new ChangeListener.ChangeEvent());
          assertNull(manual.currentPage());
        });
  }

  @Test
  void everyManualPageOpensAndItsCodeFitsThePanel() {
    withUi(
        (data, names, skin, stage) -> {
          ManualPanel manual = new ManualPanel(data, Progress.newGame(data), names, skin, null);
          stage.addActor(manual);
          for (var page : data.manual().pages()) {
            manual.showPage(page.id());
            manual.validate();
            for (Actor actor : all(manual)) {
              if (actor instanceof CodeView code) {
                assertTrue(
                    code.getPrefWidth() <= manual.getPrefWidth(),
                    page.id() + " has code wider than the panel");
              }
              if (actor instanceof Label label) {
                assertFalse(label.getText().toString().contains("???"), page.id());
              }
            }
          }
        });
  }

  private static long locks(Group root, Skin skin) {
    return all(root).stream()
        .filter(a -> a instanceof Image i && i.getDrawable() == skin.getDrawable("lock"))
        .count();
  }

  private static io.github.yeray06.wavedefense.sim.data.ManualConfig.Page page(
      GameData data, String id) {
    return data.manual().pages().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
  }

  @Test
  void theBriefingListsEachKindWithItsCount() {
    withUi(
        (data, names, skin, stage) -> {
          List<Integer> deployed = new ArrayList<>();
          BriefingPanel briefing = new BriefingPanel(data, names, skin, () -> deployed.add(1));
          briefing.show(10);
          List<String> texts = new ArrayList<>();
          for (Actor actor : all(briefing)) {
            if (actor instanceof Label label) {
              texts.add(label.getText().toString());
            }
          }
          assertTrue(
              texts.contains(
                  names.texts().format("briefing.group", 1, names.enemyName(EnemyKind.BOSS))),
              texts.toString());
          assertTrue(texts.stream().anyMatch(t -> t.contains(names.enemyInfo(EnemyKind.ARMORED))));
          button(briefing, names.texts().format("hud.deploy", 10))
              .fire(new ChangeListener.ChangeEvent());
          assertEquals(List.of(1), deployed);
        });
  }

  @Test
  void theReportExplainsMoneyLeaksAndExceptions() {
    withUi(
        (data, names, skin, stage) -> {
          WaveReport report =
              new WaveReport(
                  4,
                  false,
                  14,
                  9,
                  List.of(
                      new WaveReport.Leak(EnemyKind.REGENERATOR, 0.4, 500),
                      new WaveReport.Leak(EnemyKind.REGENERATOR, 0.2, 500),
                      new WaveReport.Leak(EnemyKind.BASIC, 1, -1)),
                  false,
                  false,
                  1_234,
                  2_700,
                  List.of(
                      new WaveReport.TurretReport(
                          30,
                          6,
                          2,
                          3,
                          360,
                          List.of(new WaveReport.FaultCount(FaultType.NO_AMMO, 1, 2)))));
          WaveOutcome outcome = new WaveOutcome(4, false, false, 1_234, 0, 0, 3);
          List<ReportLines.Line> lines = ReportLines.build(report, outcome, names, 60, 2_500);
          List<String> texts = lines.stream().map(ReportLines.Line::text).toList();
          assertEquals(names.texts().format("report.title.lost", 4), texts.get(0));
          assertTrue(texts.contains(names.texts().format("report.money", 12)));
          assertTrue(texts.contains(names.texts().format("report.next.lost", 3)));
          assertTrue(texts.contains(names.texts().format("report.leaks", 3)));
          assertTrue(
              texts.contains(
                  names
                      .texts()
                      .format(
                          "report.leak.where.many", names.texts().format("report.leak.at", 20), 2)),
              texts.toString());
          assertTrue(
              texts.contains(
                  names
                      .texts()
                      .format("report.leak.where", names.texts().get("report.leak.never"))));
          assertTrue(texts.contains(names.texts().format("report.turret.hung", "6")));
          assertTrue(
              texts.contains(
                  names.texts().format("report.turret.fault", "SinMunicionException", 1, 2)));
          ReportPanel panel = new ReportPanel(skin, new Label("", skin));
          panel.show(lines);
          assertEquals(lines.size(), panel.getChildren().size);
        });
  }
}
