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
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.progress.Goal;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.progress.WaveOutcome;
import io.github.yeray06.wavedefense.sim.world.WaveReport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The side panels and the briefing, on the headless backend. */
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
          Progress progress = Progress.newGame(data).copyWithStars(100);
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
          Progress progress = Progress.newGame(data).copyWithStars(100);
          List<Object> bought = new ArrayList<>();
          ShopPanel shop = new ShopPanel(data, progress, names, skin, bought::add);
          stage.addActor(shop);
          shop.rebuild();
          var prices = new io.github.yeray06.wavedefense.sim.progress.Shop(data);
          TextButton buyIf =
              button(
                  shop,
                  names.texts().format("shop.buy", names.stars(prices.price(Feature.IF_ELSE))));
          assertFalse(buyIf.isDisabled());
          assertTrue(
              all(shop).stream()
                  .anyMatch(
                      a ->
                          a instanceof Label l
                              && l.getText()
                                  .toString()
                                  .equals(
                                      names
                                          .texts()
                                          .format(
                                              "shop.wallet", names.stars(100), names.trophies(0)))),
              "the wallet shows stars and trophies");
          TextButton buyWhile =
              button(
                  shop, names.texts().format("shop.buy", names.stars(prices.price(Feature.WHILE))));
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
  void theShopGivesBackAnUpgradeForWhatItCost() {
    withUi(
        (data, names, skin, stage) -> {
          Progress progress = Progress.newGame(data).copyWithStars(10);
          var prices = new io.github.yeray06.wavedefense.sim.progress.Shop(data);
          prices.buy(progress, Feature.IF_ELSE);
          List<Object> changed = new ArrayList<>();
          ShopPanel shop = new ShopPanel(data, progress, names, skin, changed::add);
          stage.addActor(shop);
          shop.rebuild();
          String refund =
              names
                  .texts()
                  .format("shop.refund", data.shop().hardware(HardwareKind.DAMAGE).prices().get(0));
          assertTrue(
              all(shop).stream()
                  .noneMatch(
                      a -> a instanceof TextButton b && b.getText().toString().equals(refund)),
              "nothing to give back yet");
          int before = progress.stars();
          prices.buy(progress, HardwareKind.DAMAGE);
          shop.rebuild();
          button(shop, refund).fire(new ChangeListener.ChangeEvent());
          assertEquals(0, progress.level(HardwareKind.DAMAGE));
          assertEquals(before, progress.stars());
          assertEquals(List.of(HardwareKind.DAMAGE), changed, "the game hears about it");
        });
  }

  @Test
  void theShopKeepsItsScrollWhenItIsRebuilt() {
    withUi(
        (data, names, skin, stage) -> {
          Progress progress = Progress.newGame(data).copyWithStars(1_000);
          ShopPanel shop = new ShopPanel(data, progress, names, skin, item -> {});
          com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll =
              new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(shop, skin);
          scroll.setSize(500, 200);
          stage.addActor(scroll);
          shop.rebuild();
          scroll.layout();
          scroll.setScrollY(scroll.getMaxY());
          scroll.updateVisualScroll();
          float bottom = scroll.getScrollY();
          assertTrue(bottom > 0, "the shop is taller than its window");
          button(
                  shop,
                  names
                      .texts()
                      .format(
                          "shop.buy",
                          names.stars(
                              new io.github.yeray06.wavedefense.sim.progress.Shop(data)
                                  .price(Feature.IF_ELSE))))
              .fire(new ChangeListener.ChangeEvent());
          // The bought piece's row got shorter, so the bottom is a little higher now.
          assertEquals(
              Math.min(bottom, scroll.getMaxY()),
              scroll.getScrollY(),
              0.5f,
              "a purchase keeps the scroll");
          assertTrue(scroll.getScrollY() > 0, "not back at the top");
          float after = scroll.getScrollY();
          shop.rebuild();
          assertEquals(after, scroll.getScrollY(), 0.5f, "so do stars coming in");
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
          int actSize = data.balance().boss().everyWaves();
          assertEquals(actSize - 1, locks, "act 1 is shown and only wave 1 is reached");
          long flags =
              all(map).stream()
                  .filter(a -> a instanceof Image i && i.getDrawable() == skin.getDrawable("flag"))
                  .count();
          assertEquals(1, flags);
          // Act 2 shows its own waves, all locked, with their act-wave names.
          button(map, names.texts().format("map.act.button", 2))
              .fire(new ChangeListener.ChangeEvent());
          assertTrue(
              all(map).stream()
                  .anyMatch(
                      a ->
                          a instanceof Label l
                              && l.getText()
                                  .toString()
                                  .equals(names.texts().format("map.node", 2, 1))),
              "wave 11 is 2-1");
          float[] first = map.position(1);
          float[] boss = map.position(data.balance().lastWave());
          assertTrue(boss[1] > first[1], "the road goes up to the boss");
        });
  }

  @Test
  void theManualListsEveryPageLocksWhatIsNotOwnedAndCopiesItsExamples() {
    withUi(
        (data, names, skin, stage) -> {
          Progress progress = Progress.newGame(data).copyWithStars(100);
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
  void theSettingsApplyAtOnceAndANewGameAsksFirst() {
    withUi(
        (data, names, skin, stage) -> {
          var controls = new io.github.yeray06.wavedefense.game.screen.FakeControls();
          SettingsPanel settings = new SettingsPanel(controls, names.texts(), skin);
          stage.addActor(settings);
          button(settings, names.texts().format("settings.percent", 150)).toggle();
          assertEquals(150, controls.settings.uiScalePercent());
          button(settings, "18").toggle();
          assertEquals(18, controls.settings.codeFontSize());
          button(settings, names.texts().get("settings.fullscreen.off"))
              .fire(new ChangeListener.ChangeEvent());
          assertTrue(controls.settings.fullscreen());
          assertNotNull(button(settings, names.texts().get("settings.fullscreen.on")));

          button(settings, names.texts().get("settings.newGame"))
              .fire(new ChangeListener.ChangeEvent());
          assertEquals(0, controls.newGames, "only after confirming");
          Dialog dialog =
              (Dialog)
                  all(stage.getRoot()).stream()
                      .filter(a -> a instanceof Dialog)
                      .findFirst()
                      .orElseThrow();
          button(dialog, names.texts().get("settings.newGame.yes"))
              .fire(new ChangeListener.ChangeEvent());
          assertEquals(1, controls.newGames);
        });
  }

  @Test
  void theBriefingListsEachKindWithItsCount() {
    withUi(
        (data, names, skin, stage) -> {
          List<Integer> deployed = new ArrayList<>();
          BriefingPanel briefing =
              new BriefingPanel(data, Progress.newGame(data), names, skin, () -> deployed.add(1));
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
          assertTrue(
              texts.contains(
                  names.goalLine(Goal.CHALLENGE, data.waves().wave(10).challenge(), false)),
              "the briefing shows the challenge, not met yet");
          button(briefing, names.texts().format("hud.deploy", 10))
              .fire(new ChangeListener.ChangeEvent());
          assertEquals(List.of(1), deployed);
        });
  }

  @Test
  void theReportExplainsStarsLeaksAndExceptions() {
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
                  2_700,
                  List.of(
                      new WaveReport.TurretReport(
                          30,
                          6,
                          2,
                          3,
                          0,
                          360,
                          List.of(new WaveReport.FaultCount(FaultType.NO_AMMO, 1, 2)))));
          WaveOutcome outcome = new WaveOutcome(4, false, false, false, 30, List.of(), 0, 3);
          var challenge =
              new io.github.yeray06.wavedefense.sim.data.Challenge(
                  io.github.yeray06.wavedefense.sim.data.Challenge.Type.MAX_SHOTS, 20);
          List<ReportLines.Line> lines =
              ReportLines.build(
                  report, outcome, Progress.newGame(data), challenge, names, 60, 2_500);
          List<String> texts = lines.stream().map(ReportLines.Line::text).toList();
          assertEquals(names.texts().format("report.title.lost", 4), texts.get(0));
          assertTrue(texts.contains(names.texts().get("report.stars.none")));
          assertTrue(
              texts.contains(
                  names
                      .texts()
                      .format(
                          "report.challenge.got",
                          names.goalLine(Goal.CHALLENGE, challenge, false),
                          30)),
              "a missed challenge says how far off it was");
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

  @Test
  void aWonWaveReportsItsNewStarsAndTheMapShowsThem() {
    withUi(
        (data, names, skin, stage) -> {
          Progress progress = Progress.newGame(data);
          WaveReport report =
              new WaveReport(
                  1,
                  true,
                  8,
                  8,
                  List.of(),
                  false,
                  false,
                  1_200,
                  List.of(new WaveReport.TurretReport(8, 0, 0, 1, 0, 0, List.of())));
          WaveOutcome outcome =
              new io.github.yeray06.wavedefense.sim.progress.Progression(data)
                  .apply(progress, report, 4);
          var challenge = data.waves().wave(1).challenge();
          List<String> texts =
              ReportLines.build(report, outcome, progress, challenge, names, 60, 2_500).stream()
                  .map(ReportLines.Line::text)
                  .toList();
          assertTrue(texts.contains(names.texts().format("report.stars", names.stars(3))));
          for (Goal goal : Goal.values()) {
            assertTrue(
                texts.contains(
                    names.texts().format("report.goal.new", names.goalLine(goal, challenge, true))),
                texts.toString());
          }

          MapPanel map = new MapPanel(data, progress, names.texts(), skin, wave -> {});
          stage.addActor(map);
          map.act(0);
          String all = names.texts().get("map.star.on").repeat(3);
          assertTrue(
              all(map).stream()
                  .anyMatch(a -> a instanceof Label l && l.getText().toString().equals(all)),
              "wave 1 shows its three stars");
        });
  }
}
