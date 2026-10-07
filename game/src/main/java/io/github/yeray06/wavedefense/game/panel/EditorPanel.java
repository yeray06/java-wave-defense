package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.yeray06.wavedefense.game.editor.CodeEditor;
import io.github.yeray06.wavedefense.game.editor.EditorDocument;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.ui.UiSkin;
import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import java.util.HashMap;
import java.util.Map;

/**
 * The code panel: one tab per built turret, each with its own {@link CodeEditor}. Every change is
 * compiled at once; code that compiles goes to the turret (the next wave, or the waiting one), code
 * with errors stays in the editor and the turret keeps its last compiling script (D-51). Shows the
 * memory counter (D-06) and the errors with their line.
 */
public final class EditorPanel extends Table {
  private static final int ERRORS_SHOWN = 4;
  private static final float WIDTH = 470;

  private final WaveLoop loop;
  private final ScriptTexts names;
  private final Skin skin;
  private final EditorDocument[] documents;
  private final CodeEditor[] editors;
  private final int[] compiledVersion;
  private final CompileResult[] results;
  private final Table tabs = new Table();
  private final ScrollPane scroll;
  private final Label memory;
  private final Label status;
  private final Label errors;
  private int slot;
  private int tabCount = -1;
  private int followedVersion = -1;
  private int followedLine = -1;
  private int followedColumn = -1;
  private int shownHover = -2;

  public EditorPanel(WaveLoop loop, ScriptTexts names, Skin skin, int slots) {
    this.loop = loop;
    this.names = names;
    this.skin = skin;
    documents = new EditorDocument[slots];
    editors = new CodeEditor[slots];
    compiledVersion = new int[slots];
    results = new CompileResult[slots];
    TextureRegion white = new TextureRegion(skin.get("white", Texture.class));
    for (int i = 0; i < slots; i++) {
      documents[i] = new EditorDocument(loop.draft(i));
      editors[i] = new CodeEditor(documents[i], skin.getFont("code"), white);
      compiledVersion[i] = -1;
    }
    scroll = new ScrollPane(editors[0], skin);
    scroll.setFadeScrollBars(false);
    memory = new Label("", skin);
    status = new Label("", skin, "small");
    status.setWrap(true);
    errors = new Label("", skin, "warning");
    errors.setWrap(true);

    top().left();
    add(tabs).left().padBottom(6).row();
    add(scroll).width(WIDTH).height(330).row();
    add(memory).left().padTop(6).row();
    add(status).left().width(WIDTH).padTop(2).row();
    add(errors).left().width(WIDTH).padTop(4).row();
    Label help = new Label(names.texts().get("editor.help"), skin, "small");
    add(help).left().padTop(6).row();
    Label rule = new Label(names.texts().get("script.memory.rule"), skin, "small");
    rule.setWrap(true);
    add(rule).left().width(WIDTH).padTop(2);
    rebuildTabs();
  }

  /** The draft of a turret slot, as the player is writing it. */
  public EditorDocument document(int slot) {
    return documents[slot];
  }

  /** The latest compilation of the open turret's draft. */
  public CompileResult result() {
    return results[slot];
  }

  /** Puts the keyboard on the open turret's editor. */
  public void focus() {
    if (getStage() != null) {
      getStage().setKeyboardFocus(editors[slot]);
    }
  }

  /** A purchase changed what compiles, or added a turret. */
  public void purchased() {
    java.util.Arrays.fill(compiledVersion, -1);
    rebuildTabs();
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    if (tabCount != loop.progress().turretCount()) {
      rebuildTabs();
    }
    for (int i = 0; i < loop.progress().turretCount(); i++) {
      if (compiledVersion[i] != documents[i].version()) {
        compile(i);
      }
    }
    follow();
    int hover = editors[slot].hoveredLine();
    if (hover != shownHover) {
      shownHover = hover;
      showErrors();
    }
  }

  private void rebuildTabs() {
    tabCount = loop.progress().turretCount();
    if (slot >= tabCount) {
      select(0);
    }
    tabs.clearChildren();
    ButtonGroup<TextButton> group = new ButtonGroup<>();
    for (int i = 0; i < tabCount; i++) {
      int index = i;
      TextButton tab = new TextButton(names.texts().format("editor.turret", i + 1), skin);
      tab.pad(4, 12, 4, 12);
      tab.setChecked(i == slot);
      group.add(tab);
      tab.addListener(
          new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
              if (tab.isChecked()) {
                select(index);
              }
            }
          });
      tabs.add(tab).padRight(6);
    }
  }

  private void select(int index) {
    slot = index;
    scroll.setActor(editors[index]);
    shownHover = -2;
    compile(index);
    focus();
  }

  private void compile(int index) {
    EditorDocument document = documents[index];
    compiledVersion[index] = document.version();
    CompileResult result = loop.setScript(index, document.text());
    results[index] = result;
    Map<Integer, Integer> errorColumns = new HashMap<>();
    for (CompileError error : result.errors()) {
      errorColumns.putIfAbsent(error.line(), error.column());
    }
    editors[index].setDiagnostics(result.memoryByLine(), errorColumns);
    if (index == slot) {
      showStatus();
      showErrors();
    }
  }

  private void showStatus() {
    CompileResult result = results[slot];
    memory.setText(
        names.texts().format("editor.memory", result.memoryUsed(), result.memoryLimit()));
    memory.setColor(
        result.memoryUsed() > result.memoryLimit()
            ? UiSkin.WARNING
            : com.badlogic.gdx.graphics.Color.WHITE);
    if (result.succeeded()) {
      status.setText(
          names
              .texts()
              .get(
                  loop.state() == WaveLoop.State.AWAITING_DEPLOY
                      ? "editor.okWaiting"
                      : "editor.ok"));
    } else {
      status.setText(names.texts().format("editor.errors", result.errors().size()));
    }
  }

  /** The errors on the hovered line first (the tooltip of ARCHITECTURE §6), then the rest. */
  private void showErrors() {
    CompileResult result = results[slot];
    if (result == null || result.errors().isEmpty()) {
      errors.setText("");
      return;
    }
    int hovered = editors[slot].hoveredLine() + 1;
    StringBuilder text = new StringBuilder();
    int shown = 0;
    for (int pass = 0; pass < 2 && shown < ERRORS_SHOWN; pass++) {
      for (CompileError error : result.errors()) {
        boolean onHover = error.line() == hovered;
        if ((pass == 0) != onHover || shown >= ERRORS_SHOWN) {
          continue;
        }
        if (shown > 0) {
          text.append('\n');
        }
        text.append(names.texts().format("editor.error", error.line(), names.error(error)));
        shown++;
      }
    }
    errors.setText(text);
  }

  /** Keeps the cursor in view after it moves. */
  private void follow() {
    EditorDocument document = documents[slot];
    if (document.version() == followedVersion
        && document.cursorLine() == followedLine
        && document.cursorColumn() == followedColumn) {
      return;
    }
    followedVersion = document.version();
    followedLine = document.cursorLine();
    followedColumn = document.cursorColumn();
    scroll.layout();
    var bounds = editors[slot].cursorBounds();
    scroll.scrollTo(bounds.x, bounds.y, bounds.width, bounds.height);
  }
}
