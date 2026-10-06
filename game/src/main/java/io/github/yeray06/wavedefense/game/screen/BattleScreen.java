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
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.game.loop.FixedStepClock;
import io.github.yeray06.wavedefense.game.playtest.ReloadReactionLog;
import io.github.yeray06.wavedefense.game.render.WorldRenderer;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.economy.Money;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.progress.WaveOutcome;
import io.github.yeray06.wavedefense.sim.world.Battle;
import io.github.yeray06.wavedefense.sim.world.Turret;

/**
 * The battlefield: runs the wave loop on a fixed step, draws the world through {@link
 * WorldRenderer} and lays a scene2d HUD on top. Clicking a turret reloads it by hand (D-03).
 *
 * <p>The briefing, the full report, the shop and the editor arrive in phase 5; here the HUD shows
 * the wave, the core, the money, the speed and auto-advance switches, a deploy button for new waves
 * and a one-line result of the last wave.
 */
public final class BattleScreen extends ScreenAdapter {
  private static final Color BACKGROUND = new Color(0.07f, 0.08f, 0.10f, 1f);

  /** Ticks a late frame may run per speed unit (ARCHITECTURE §5). */
  private static final int MAX_TICKS_PER_FRAME = 5;

  private static final int FAST = 3;
  private static final float LABEL_OFFSET = 46;
  private static final float PAD = 10;
  private static final float BUTTON_PAD = 12;

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
  private final TextButton speedButton;
  private final TextButton autoButton;
  private final TextButton deployButton;
  private final Label[] turretLabels;
  private final int[] turretLabelState;

  private boolean fastRequested;
  private int shownFinished = -1;
  private int shownWave = -1;
  private int shownLives = -1;
  private long shownMoney = -1;
  private int shownSpeed = -1;
  private int shownAuto = -1;
  private int shownDeploy = -1;
  private double seconds;

  public BattleScreen(
      GameData data,
      Texts texts,
      Skin skin,
      WaveLoop loop,
      WorldRenderer world,
      ReloadReactionLog reactions) {
    this.data = data;
    this.texts = texts;
    this.loop = loop;
    this.world = world;
    this.reactions = reactions;
    this.clock = new FixedStepClock(data.balance().ticksPerSecond(), MAX_TICKS_PER_FRAME);
    this.stage = new Stage(new ScreenViewport());

    waveLabel = new Label("", skin);
    coreLabel = new Label("", skin);
    moneyLabel = new Label("", skin);
    resultLabel = new Label("", skin, "small");
    speedButton = new TextButton("", skin);
    autoButton = new TextButton("", skin);
    deployButton = new TextButton("", skin);
    Label hint = new Label(texts.get("hud.hint.reload"), skin, "small");
    speedButton.pad(4, BUTTON_PAD, 4, BUTTON_PAD);
    autoButton.pad(4, BUTTON_PAD, 4, BUTTON_PAD);
    deployButton.pad(BUTTON_PAD, BUTTON_PAD * 2, BUTTON_PAD, BUTTON_PAD * 2);

    Table top = new Table();
    top.setFillParent(true);
    top.top().pad(PAD);
    top.add(waveLabel).left().padRight(30);
    top.add(coreLabel).left().padRight(30);
    top.add(moneyLabel).left().expandX();
    top.add(speedButton).right().padRight(PAD).minWidth(70);
    top.add(autoButton).right();
    stage.addActor(top);

    Table center = new Table();
    center.setFillParent(true);
    center.add(deployButton).pad(PAD * 2);
    stage.addActor(center);

    Table bottom = new Table();
    bottom.setFillParent(true);
    bottom.bottom().pad(PAD);
    bottom.add(resultLabel).row();
    bottom.add(hint);
    stage.addActor(bottom);

    int slots = data.map().turretSlots().size();
    turretLabels = new Label[slots];
    turretLabelState = new int[slots];
    for (int i = 0; i < slots; i++) {
      turretLabels[i] = new Label("", skin, "warning");
      turretLabelState[i] = -1;
      stage.addActor(turretLabels[i]);
    }

    speedButton.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            fastRequested = speedButton.isChecked();
          }
        });
    autoButton.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            loop.progress().setAutoAdvance(autoButton.isChecked());
          }
        });
    deployButton.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            loop.deploy();
          }
        });
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
    int speed = speed();
    if (loop.state() == WaveLoop.State.RUNNING) {
      int ticks = clock.advance(delta, speed);
      for (int i = 0; i < ticks && loop.state() == WaveLoop.State.RUNNING; i++) {
        int finished = loop.wavesFinished();
        loop.tick();
        if (loop.wavesFinished() != finished) {
          reactions.waveFinished(loop.lastOutcome().wave());
        }
        reactions.afterTick(loop.battle(), seconds);
      }
    } else {
      clock.reset();
    }

    ScreenUtils.clear(BACKGROUND);
    world.render(loop.battle(), clock.alpha(), delta);
    updateHud(speed);
    stage.act(delta);
    stage.draw();
  }

  private int speed() {
    return fastRequested && loop.speedUpAllowed() ? FAST : 1;
  }

  private void updateHud(int speed) {
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
    int speedState = (loop.speedUpAllowed() ? 2 : 0) + (speed == FAST ? 1 : 0);
    if (speedState != shownSpeed) {
      shownSpeed = speedState;
      speedButton.setText(texts.get(speed == FAST ? "hud.speed.fast" : "hud.speed.normal"));
      speedButton.setDisabled(!loop.speedUpAllowed());
    }
    int auto = progress.autoAdvance() ? 1 : 0;
    if (auto != shownAuto) {
      shownAuto = auto;
      autoButton.setProgrammaticChangeEvents(false);
      autoButton.setChecked(progress.autoAdvance());
      autoButton.setProgrammaticChangeEvents(true);
      autoButton.setText(texts.get(progress.autoAdvance() ? "hud.auto.on" : "hud.auto.off"));
    }
    int deploy = loop.state() == WaveLoop.State.AWAITING_DEPLOY ? battle.wave() : 0;
    if (deploy != shownDeploy) {
      shownDeploy = deploy;
      deployButton.setVisible(deploy > 0);
      deployButton.setText(texts.format("hud.deploy", deploy));
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
                "hud.turret.fault",
                texts.get("script.fault." + turret.lastFault().name()),
                turret.lastFaultLine()));
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
