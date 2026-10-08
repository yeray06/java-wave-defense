package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.script.api.PrintKind;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import io.github.yeray06.wavedefense.sim.world.Battle;
import io.github.yeray06.wavedefense.sim.world.Enemy;
import io.github.yeray06.wavedefense.sim.world.OutputLog;
import io.github.yeray06.wavedefense.sim.world.Turret;
import java.util.ArrayDeque;

/**
 * The output window (D-67): what the turrets wrote with {@code imprimir(x)} and the exceptions that
 * hung them, newest at the bottom, each with its turret and line. Keeps the last {@value #KEPT}
 * lines across waves, with a separator at each new wave.
 */
public final class OutputPanel extends Table {
  static final int KEPT = 200;

  private final WaveLoop loop;
  private final ScriptTexts names;
  private final Texts texts;
  private final Label text;
  private final ScrollPane scroll;
  private final ArrayDeque<String> lines = new ArrayDeque<>();
  private Battle battle;
  private long seen;
  private boolean[] hung = new boolean[0];
  private boolean changed = true;
  private boolean separated;

  public OutputPanel(WaveLoop loop, ScriptTexts names, Skin skin) {
    this.loop = loop;
    this.names = names;
    this.texts = names.texts();
    text = new Label("", skin, "code");
    text.setWrap(true);
    text.setAlignment(com.badlogic.gdx.utils.Align.topLeft);
    scroll = new ScrollPane(text, skin);
    scroll.setFadeScrollBars(false);
    add(scroll).grow();
  }

  /** The lines shown now, oldest first. */
  public String shown() {
    return text.getText().toString();
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    read();
    if (changed) {
      changed = false;
      text.setText(lines.isEmpty() ? texts.get("output.empty") : String.join("\n", lines));
      scroll.layout();
      scroll.setScrollPercentY(1);
    }
  }

  /** Takes what the battle wrote since the last frame; a finished battle is read to its end. */
  public void read() {
    Battle current = loop.battle();
    if (current != battle) {
      if (battle != null) {
        readFrom(battle);
      }
      battle = current;
      seen = 0;
      separated = false;
      hung = new boolean[current.turretCount()];
    }
    if (loop.state() == WaveLoop.State.RUNNING) {
      readFrom(current);
    }
  }

  private void readFrom(Battle source) {
    if (!separated && source.tickCount() > 0) {
      separated = true;
      add(texts.format("output.wave", source.wave()));
    }
    OutputLog output = source.output();
    for (long entry = Math.max(seen, output.oldest()); entry < output.written(); entry++) {
      add(
          texts.format(
              output.inLibrary(entry) ? "output.whereLibrary" : "output.where",
              turret(output.turret(entry)),
              output.line(entry),
              value(source, output.kind(entry), output.value(entry))));
    }
    seen = output.written();
    for (int i = 0; i < source.turretCount() && i < hung.length; i++) {
      Turret turret = source.turret(i);
      if (turret.hung() && !hung[i]) {
        add(
            texts.format(
                turret.lastFaultInLibrary() ? "output.faultLibrary" : "output.fault",
                turret(i),
                names.fault(turret.lastFault()),
                turret.lastFaultLine()));
      }
      hung[i] = turret.hung();
    }
  }

  private String turret(int slot) {
    return TurretNames.of(loop.career(), texts, slot);
  }

  private String value(Battle current, PrintKind kind, long value) {
    return switch (kind) {
      case INT -> Long.toString(value);
      case BOOLEAN -> value != 0 ? "true" : "false";
      case NULL -> "null";
      case LIST -> texts.format("output.list", value);
      case ENEMY -> {
        Enemy enemy = current.enemies().resolve(value);
        yield enemy == null
            ? texts.get("output.enemyGone")
            : texts.format(
                "output.enemy",
                names.enemyName(enemy.kind()),
                (int) StrictMath.ceil(enemy.health()));
      }
    };
  }

  private void add(String line) {
    lines.addLast(line);
    while (lines.size() > KEPT) {
      lines.removeFirst();
    }
    changed = true;
  }

  /** Empties the window («Borrar»). */
  public void clear() {
    lines.clear();
    changed = true;
  }
}
