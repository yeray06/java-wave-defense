package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.script.api.EnemyHandle;
import io.github.yeray06.wavedefense.script.check.DebugVar;
import io.github.yeray06.wavedefense.script.runtime.Tracer;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.world.Enemy;
import java.util.List;

/**
 * The debugger window (D-62): when a turret reaches a breakpoint the wave pauses and this shows
 * that turret's cycle step by step, with the line marked in the editor and the value of every
 * variable at that step. The cycle already ran; stepping walks through its recording, so the world
 * stays exactly as it was.
 */
public final class DebuggerPanel extends Table {
  private static final float WIDTH = 380;

  private final WaveLoop loop;
  private final ScriptTexts names;
  private final Texts texts;
  private final EditorPanel editor;
  private final Label status;
  private final Label stepLabel;
  private final Label variables;
  private final Label hover;
  private final TextButton back;
  private final TextButton forward;
  private final TextButton resume;
  private int turret = -1;
  private int step;
  private int shownTurret = -2;
  private int shownStep = -1;
  private String shownHover = "";

  public DebuggerPanel(WaveLoop loop, ScriptTexts names, Skin skin, EditorPanel editor) {
    this.loop = loop;
    this.names = names;
    this.texts = names.texts();
    this.editor = editor;
    status = wrapped(skin, "body");
    stepLabel = new Label("", skin, "small");
    variables = new Label("", skin, "code");
    variables.setWrap(true);
    hover = wrapped(skin, "small");
    back = button(skin, "debug.back", () -> go(step - 1));
    forward = button(skin, "debug.forward", () -> go(step + 1));
    resume = button(skin, "debug.resume", this::resume);
    TextButton next = button(skin, "debug.nextCycle", this::nextCycle);
    top().left();
    add(status).width(WIDTH).left().row();
    Table buttons = new Table();
    buttons.add(back).padRight(4);
    buttons.add(forward).padRight(10);
    buttons.add(resume).padRight(4);
    buttons.add(next);
    add(buttons).left().padTop(6).row();
    add(stepLabel).left().padTop(4).row();
    add(new Label(texts.get("debug.variables"), skin, "title")).left().padTop(8).row();
    add(variables).width(WIDTH).left().padTop(2).row();
    add(hover).width(WIDTH).left().padTop(8).row();
  }

  private static Label wrapped(Skin skin, String style) {
    Label label = new Label("", skin, style);
    label.setWrap(true);
    return label;
  }

  private static TextButton button(Skin skin, String key, Runnable action) {
    TextButton button = new TextButton("", skin);
    button.setName(key);
    button.pad(3, 10, 3, 10);
    button.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            button.setChecked(false);
            if (!button.isDisabled()) {
              action.run();
            }
          }
        });
    return button;
  }

  /** The step shown, or -1 while the wave runs. */
  public int step() {
    return turret < 0 ? -1 : step;
  }

  /** The variables as shown, one per line. */
  public String variablesShown() {
    return variables.getText().toString();
  }

  /** Shows another step of the recording; out of range is ignored. */
  public void go(int to) {
    Tracer trace = trace();
    if (trace != null && to >= 0 && to < trace.steps()) {
      step = to;
    }
  }

  /** «Continuar»: the wave runs on until the next breakpoint. */
  public void resume() {
    loop.resumeDebug();
  }

  /** «Siguiente ciclo»: runs on and pauses after this turret's next cycle. */
  public void nextCycle() {
    int stopped = turret < 0 ? 0 : turret;
    loop.stepCycle(stopped);
  }

  private Tracer trace() {
    return turret < 0 ? null : loop.battle().trace(turret);
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    int paused = loop.debugTurret();
    if (paused != turret) {
      turret = paused;
      Tracer trace = trace();
      step = trace == null ? 0 : Math.max(0, trace.firstBreak());
      shownStep = -1;
    }
    if (turret != shownTurret || step != shownStep) {
      shownTurret = turret;
      shownStep = step;
      show();
    }
    String word =
        turret < 0
            ? ""
            : editor.hoveredWord(trace().inLibrary(step) ? editor.librarySlot() : turret);
    if (!word.equals(shownHover)) {
      shownHover = word;
      String value = word.isEmpty() ? null : value(word);
      hover.setText(value == null ? "" : texts.format("debug.hover", word, value));
    }
  }

  private void show() {
    back.setText(texts.get("debug.back"));
    forward.setText(texts.get("debug.forward"));
    resume.setText(texts.get("debug.resume"));
    Tracer trace = trace();
    boolean paused = trace != null && trace.steps() > 0;
    back.setDisabled(!paused || step == 0);
    forward.setDisabled(!paused || step >= trace.steps() - 1);
    resume.setDisabled(turret < 0);
    if (!paused) {
      status.setText(texts.get("debug.idle"));
      stepLabel.setText("");
      variables.setText("");
      editor.showDebugLine(0, false, -1);
      return;
    }
    String who = TurretNames.of(loop.career(), texts, turret);
    String function = trace.function(step).name();
    status.setText(
        trace.inLibrary(step)
            ? texts.format("debug.atLibrary", who, trace.line(step), function)
            : texts.format("debug.at", who, trace.line(step)));
    stepLabel.setText(
        texts.format("debug.step", step + 1, trace.steps())
            + (trace.truncated() ? texts.get("debug.truncated") : ""));
    StringBuilder text = new StringBuilder();
    for (DebugVar v : visible(trace)) {
      if (!text.isEmpty()) {
        text.append('\n');
      }
      text.append(v.name()).append(" = ").append(value(trace, v));
    }
    variables.setText(text.isEmpty() ? texts.get("debug.noVariables") : text);
    editor.showDebugLine(turret, trace.inLibrary(step), trace.line(step));
  }

  /** The variables of the function running at this step, declared on or before its line. */
  private List<DebugVar> visible(Tracer trace) {
    String function = trace.function(step).name();
    List<DebugVar> all = trace.inLibrary(step) ? loop.libraryDebugVars() : loop.debugVars(turret);
    int line = trace.line(step);
    return all.stream().filter(v -> v.function().equals(function) && v.line() <= line).toList();
  }

  private String value(String name) {
    Tracer trace = trace();
    if (trace == null || trace.steps() == 0) {
      return null;
    }
    String found = null;
    for (DebugVar v : visible(trace)) {
      if (v.name().equals(name)) {
        found = value(trace, v);
      }
    }
    return found;
  }

  private String value(Tracer trace, DebugVar v) {
    return switch (v.kind()) {
      case INT -> Integer.toString(trace.intValue(step, v.slot()));
      case BOOLEAN -> trace.intValue(step, v.slot()) != 0 ? "true" : "false";
      case LIST ->
          trace.refValue(step, v.slot()) == EnemyHandle.NONE ? "null" : texts.get("debug.list");
      case ENEMY -> {
        long handle = trace.refValue(step, v.slot());
        if (handle == EnemyHandle.NONE) {
          yield "null";
        }
        Enemy enemy = loop.battle().enemies().resolve(handle);
        yield enemy == null
            ? texts.get("output.enemyGone")
            : texts.format(
                "output.enemy",
                names.enemyName(enemy.kind()),
                (int) StrictMath.ceil(enemy.health()));
      }
    };
  }
}
