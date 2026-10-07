package io.github.yeray06.wavedefense.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.game.loop.FixedStepClock;
import io.github.yeray06.wavedefense.game.panel.BriefingPanel;
import io.github.yeray06.wavedefense.game.panel.EditorPanel;
import io.github.yeray06.wavedefense.game.panel.ManualPanel;
import io.github.yeray06.wavedefense.game.panel.MapPanel;
import io.github.yeray06.wavedefense.game.panel.ReportLines;
import io.github.yeray06.wavedefense.game.panel.ReportPanel;
import io.github.yeray06.wavedefense.game.panel.SettingsPanel;
import io.github.yeray06.wavedefense.game.panel.ShopPanel;
import io.github.yeray06.wavedefense.game.playtest.ReloadReactionLog;
import io.github.yeray06.wavedefense.game.render.WorldRenderer;
import io.github.yeray06.wavedefense.game.ui.SpeedSelector;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.economy.Money;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.progress.WaveOutcome;
import io.github.yeray06.wavedefense.sim.world.Battle;
import io.github.yeray06.wavedefense.sim.world.PathTrack;
import io.github.yeray06.wavedefense.sim.world.Turret;

/**
 * The battlefield: runs the wave loop on a fixed step, draws the world through {@link
 * WorldRenderer} and lays a scene2d HUD on top. Clicking a turret reloads it by hand (D-03).
 *
 * <p>The HUD shows the wave, the core, the money, the speed and auto-advance switches and a
 * one-line result of the last wave. A waiting wave shows its briefing; the toolbar opens the code,
 * shop, map, report and manual panels on the right.
 */
public final class BattleScreen extends ScreenAdapter {
  private static final Color BACKGROUND = new Color(0.07f, 0.08f, 0.10f, 1f);

  /** Ticks a late frame may run per speed unit (ARCHITECTURE §5). */
  private static final int MAX_TICKS_PER_FRAME = 5;

  private static final float LABEL_OFFSET = 46;
  private static final float PAD = 10;
  private static final float BUTTON_PAD = 12;
  private static final float SIDE_WIDTH = 500;
  private static final float NOTICE_SECONDS = 12;

  private final GameData data;
  private final Texts texts;
  private final WaveLoop loop;
  private final WorldRenderer world;
  private final ReloadReactionLog reactions;
  private final FixedStepClock clock;
  private final Stage stage;
  private final Vector2 point = new Vector2();

  private final Label waveLabel;
  private final Label coreLabel;
  private final Label moneyLabel;
  private final Label resultLabel;
  private final SpeedSelector speedSelector;
  private final TextButton autoButton;
  private final BriefingPanel briefing;
  private final EditorPanel editorPanel;
  private final ShopPanel shopPanel;
  private final MapPanel mapPanel;
  private final ReportPanel reportPanel;
  private final ManualPanel manualPanel;
  private final SettingsPanel settingsPanel;
  private final Label noticeLabel;
  private float noticeSeconds;
  private final ScriptTexts names;
  private final Container<Actor> side;
  private final ScrollPane shopScroll;
  private final ScrollPane reportScroll;
  private final ScrollPane manualScroll;
  private final ScrollPane settingsScroll;
  private final double pathLength;
  private Actor openPanel;
  private Cell<Container<Actor>> sideCell;
  private final Label[] turretLabels;
  private final int[] turretLabelState;

  private int shownFinished = -1;
  private int shownWave = -1;
  private int shownLives = -1;
  private long shownMoney = -1;
  private int shownAuto = -1;
  private int shownBriefing = -1;
  private double seconds;

  public BattleScreen(
      GameData data,
      Texts texts,
      Skin skin,
      WaveLoop loop,
      WorldRenderer world,
      ReloadReactionLog reactions,
      GameControls controls,
      float uiScale) {
    this.data = data;
    this.texts = texts;
    this.loop = loop;
    this.world = world;
    this.reactions = reactions;
    this.clock = new FixedStepClock(data.balance().ticksPerSecond(), MAX_TICKS_PER_FRAME);
    ScreenViewport viewport = new ScreenViewport();
    // D-56: the interface keeps its layout in units and is drawn bigger.
    viewport.setUnitsPerPixel(1 / uiScale);
    this.stage = new Stage(viewport);
    this.names = new ScriptTexts(texts, data);
    this.pathLength = new PathTrack(data.map().path()).length();

    waveLabel = new Label("", skin);
    coreLabel = new Label("", skin);
    moneyLabel = new Label("", skin);
    resultLabel = new Label("", skin, "small");
    autoButton = new TextButton("", skin);
    Label hint = new Label(texts.get("hud.hint.reload"), skin, "small");
    speedSelector =
        new SpeedSelector(
            data.balance().speeds(), skin, speed -> texts.format("hud.speed", speed), BUTTON_PAD);
    autoButton.pad(4, BUTTON_PAD, 4, BUTTON_PAD);
    int slots = data.map().turretSlots().size();
    briefing = new BriefingPanel(data, names, skin, loop::deploy);
    editorPanel = new EditorPanel(loop, names, skin, slots);
    shopPanel = new ShopPanel(data, loop.progress(), names, skin, this::purchased);
    mapPanel = new MapPanel(data, loop.progress(), texts, skin, this::chooseWave);
    reportPanel = new ReportPanel(skin, new Label(texts.get("report.none"), skin, "small"));
    shopScroll = new ScrollPane(shopPanel, skin);
    shopScroll.setFadeScrollBars(false);
    reportScroll = new ScrollPane(reportPanel, skin);
    reportScroll.setFadeScrollBars(false);
    manualPanel = new ManualPanel(data, loop.progress(), names, skin, Gdx.app.getClipboard());
    manualScroll = new ScrollPane(manualPanel, skin);
    manualScroll.setFadeScrollBars(false);
    settingsPanel = new SettingsPanel(controls, texts, skin);
    settingsScroll = new ScrollPane(settingsPanel, skin);
    settingsScroll.setFadeScrollBars(false);
    noticeLabel = new Label("", skin, "warning");
    noticeLabel.setWrap(true);
    noticeLabel.setAlignment(Align.center);
    side = new Container<>();
    side.setBackground(skin.getDrawable("panel"));
    side.pad(PAD);
    side.top().left();
    side.setVisible(false);

    Table top = new Table();
    top.setFillParent(true);
    top.top().pad(PAD);
    top.add(waveLabel).left().padRight(30);
    top.add(coreLabel).left().padRight(30);
    top.add(moneyLabel).left().expandX();
    top.add(speedSelector).right().padRight(PAD);
    top.add(autoButton).right();
    top.row();
    Table toolbar = new Table();
    toolbar.add(panelButton(skin, "hud.panel.code", editorPanel)).padRight(6);
    toolbar.add(panelButton(skin, "hud.panel.shop", shopScroll)).padRight(6);
    toolbar.add(panelButton(skin, "hud.panel.map", mapPanel)).padRight(6);
    toolbar.add(panelButton(skin, "hud.panel.report", reportScroll)).padRight(6);
    toolbar.add(panelButton(skin, "hud.panel.manual", manualScroll)).padRight(6);
    toolbar.add(panelButton(skin, "hud.panel.settings", settingsScroll));
    top.add(toolbar).colspan(5).right().padTop(6);
    stage.addActor(top);

    Table middle = new Table();
    middle.setFillParent(true);
    middle.padTop(100).padBottom(54);
    middle.add(briefing).expand().center();
    sideCell = middle.add(side).width(0).expandY().fillY().right();
    stage.addActor(middle);

    Table bottom = new Table();
    bottom.setFillParent(true);
    bottom.bottom().pad(PAD);
    bottom.add(noticeLabel).width(700).row();
    bottom.add(resultLabel).row();
    bottom.add(hint);
    stage.addActor(bottom);

    turretLabels = new Label[slots];
    turretLabelState = new int[slots];
    for (int i = 0; i < slots; i++) {
      turretLabels[i] = new Label("", skin, "warning");
      turretLabelState[i] = -1;
      stage.addActor(turretLabels[i]);
    }

    autoButton.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            loop.setAutoAdvance(autoButton.isChecked());
          }
        });
  }

  private TextButton panelButton(Skin skin, String key, Actor panel) {
    TextButton button = new TextButton(texts.get(key), skin);
    button.pad(4, BUTTON_PAD, 4, BUTTON_PAD);
    button.setProgrammaticChangeEvents(false);
    button.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            button.setChecked(false);
            toggle(panel);
          }
        });
    return button;
  }

  /** Opens a side panel, or closes it if it is the one open. */
  void toggle(Actor panel) {
    open(openPanel == panel ? null : panel);
  }

  private void open(Actor panel) {
    openPanel = panel;
    side.setActor(panel);
    side.setVisible(panel != null);
    sideCell.width(panel != null ? SIDE_WIDTH : 0).padRight(panel != null ? PAD : 0);
    sideCell.getTable().invalidateHierarchy();
    if (panel == shopScroll) {
      shopPanel.rebuild();
    }
    if (panel == manualScroll) {
      manualPanel.refresh();
    }
    if (panel == settingsScroll) {
      settingsPanel.rebuild();
    }
    if (panel == editorPanel) {
      editorPanel.focus();
    } else {
      stage.setKeyboardFocus(null);
    }
  }

  /** The side panel open now, or {@code null}. */
  Actor openPanel() {
    return openPanel;
  }

  EditorPanel editorPanel() {
    return editorPanel;
  }

  ShopPanel shopPanel() {
    return shopPanel;
  }

  MapPanel mapPanel() {
    return mapPanel;
  }

  ManualPanel manualPanel() {
    return manualPanel;
  }

  ScrollPane manualScroll() {
    return manualScroll;
  }

  ScrollPane settingsScroll() {
    return settingsScroll;
  }

  /** Opens the settings panel: after the screen is rebuilt for new sizes, it stays in view. */
  public void openSettings() {
    open(settingsScroll);
  }

  /** A message for the player above the result line: a recovered save, a failed write… */
  public void showNotice(String text) {
    noticeLabel.setText(text);
    noticeSeconds = NOTICE_SECONDS;
  }

  /** The notice shown now, or "". */
  String notice() {
    return noticeLabel.getText().toString();
  }

  private void purchased(Object item) {
    loop.purchased();
    editorPanel.purchased();
  }

  private void chooseWave(int wave) {
    if (loop.selectWave(wave)) {
      open(null);
    }
  }

  private void waveFinished() {
    reportPanel.show(
        ReportLines.build(
            loop.lastReport(),
            loop.lastOutcome(),
            names,
            data.balance().ticksPerSecond(),
            pathLength));
    if (openPanel == null) {
      open(reportScroll);
    }
  }

  @Override
  public void show() {
    Gdx.input.setInputProcessor(
        new InputMultiplexer(
            stage,
            new InputAdapter() {
              @Override
              public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                return button == Input.Buttons.LEFT && clickWorld(screenX, screenY);
              }
            }));
  }

  /** A click on the battlefield: reloads the turret under it by hand. */
  boolean clickWorld(int screenX, int screenY) {
    Battle battle = loop.battle();
    int turret = world.turretAt(battle, screenX, screenY);
    if (turret < 0) {
      return false;
    }
    if (loop.manualReload(turret)) {
      reactions.manualReload(battle, turret, seconds);
    }
    return true;
  }

  @Override
  public void render(float delta) {
    seconds += delta;
    speedSelector.setFastAllowed(loop.speedUpAllowed());
    int speed = speedSelector.speed();
    if (loop.state() == WaveLoop.State.RUNNING) {
      int ticks = clock.advance(delta, speed);
      for (int i = 0; i < ticks && loop.state() == WaveLoop.State.RUNNING; i++) {
        int finished = loop.wavesFinished();
        loop.tick();
        if (loop.wavesFinished() != finished) {
          reactions.waveFinished(loop.lastOutcome().wave());
          waveFinished();
        }
        reactions.afterTick(loop.battle(), seconds);
      }
    } else {
      clock.reset();
    }

    if (noticeSeconds > 0) {
      noticeSeconds -= delta;
      if (noticeSeconds <= 0) {
        noticeLabel.setText("");
      }
    }
    ScreenUtils.clear(BACKGROUND);
    world.render(loop.battle(), clock.alpha(), delta);
    updateHud();
    stage.act(delta);
    // The world letterboxes the GL viewport; the HUD covers the whole window, where clicks land.
    stage.getViewport().apply();
    stage.draw();
  }

  private void updateHud() {
    Battle battle = loop.battle();
    Progress progress = loop.progress();
    if (battle.wave() != shownWave) {
      shownWave = battle.wave();
      waveLabel.setText(texts.format("hud.wave", shownWave, data.balance().lastWave()));
    }
    if (battle.coreLives() != shownLives) {
      shownLives = battle.coreLives();
      coreLabel.setText(
          texts.format("hud.core", Math.max(0, shownLives), data.balance().coreLivesPerWave()));
    }
    long money = Money.wholeUnits(progress.moneyCents());
    if (money != shownMoney) {
      shownMoney = money;
      moneyLabel.setText(texts.format("hud.money", money));
    }
    int auto = progress.autoAdvance() ? 1 : 0;
    if (auto != shownAuto) {
      shownAuto = auto;
      autoButton.setProgrammaticChangeEvents(false);
      autoButton.setChecked(progress.autoAdvance());
      autoButton.setProgrammaticChangeEvents(true);
      autoButton.setText(texts.get(progress.autoAdvance() ? "hud.auto.on" : "hud.auto.off"));
    }
    int waiting = loop.state() == WaveLoop.State.AWAITING_DEPLOY ? battle.wave() : 0;
    if (waiting != shownBriefing) {
      shownBriefing = waiting;
      briefing.setVisible(waiting > 0);
      if (waiting > 0) {
        briefing.show(waiting);
      }
    }
    if (loop.wavesFinished() != shownFinished) {
      shownFinished = loop.wavesFinished();
      resultLabel.setText(result(loop.lastOutcome()));
    }
    for (int i = 0; i < turretLabels.length; i++) {
      updateTurretLabel(battle, i);
    }
  }

  private String result(WaveOutcome outcome) {
    if (outcome == null) {
      return "";
    }
    long paid = Money.wholeUnits(outcome.totalCents());
    if (!outcome.won()) {
      return texts.format("hud.result.lost", outcome.wave(), outcome.nextWave(), paid);
    }
    return texts.format(
        outcome.perfect() ? "hud.result.perfect" : "hud.result.won", outcome.wave(), paid);
  }

  /** Text above a turret: the exception that hung it, a reload, or an empty magazine. */
  private void updateTurretLabel(Battle battle, int i) {
    Label label = turretLabels[i];
    if (i >= battle.turretCount()) {
      label.setVisible(false);
      turretLabelState[i] = -1;
      return;
    }
    Turret turret = battle.turret(i);
    int state;
    if (turret.hung()) {
      state = 1000 + turret.lastFault().ordinal() * 100 + Math.min(99, turret.lastFaultLine());
    } else if (turret.reloading()) {
      state = 2;
    } else if (turret.ammo() == 0 && loop.state() == WaveLoop.State.RUNNING) {
      state = 1;
    } else {
      state = 0;
    }
    if (state != turretLabelState[i]) {
      turretLabelState[i] = state;
      label.setVisible(state != 0);
      if (state >= 1000) {
        label.setText(
            texts.format(
                "hud.turret.fault", names.fault(turret.lastFault()), turret.lastFaultLine()));
      } else if (state == 2) {
        label.setText(texts.get("hud.turret.reloading"));
      } else if (state == 1) {
        label.setText(texts.get("hud.turret.empty"));
      }
      label.pack();
    }
    if (label.isVisible()) {
      world.project(turret.x(), turret.y(), point);
      stage.screenToStageCoordinates(point);
      label.setPosition(point.x - label.getWidth() / 2, point.y + LABEL_OFFSET);
    }
  }

  @Override
  public void resize(int width, int height) {
    world.resize(width, height);
    stage.getViewport().update(width, height, true);
  }

  @Override
  public void dispose() {
    stage.dispose();
  }
}
