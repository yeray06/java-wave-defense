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
import io.github.yeray06.wavedefense.game.editor.Vocabulary;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.ui.FloatingWindow;
import io.github.yeray06.wavedefense.game.ui.UiSkin;
import io.github.yeray06.wavedefense.game.ui.Windows;
import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.LibraryResult;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.diag.CompileError;
import io.github.yeray06.wavedefense.sim.progress.WaveLoop;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * The code panel: one tab per built turret, each with its own {@link CodeEditor}, and once methods
 * are bought, one more for the library they all share. Every change is compiled at once; code that
 * compiles goes to the turret (the next wave, or the waiting one), code with errors stays in the
 * editor and the turret keeps its last compiling script (D-51). Shows the memory counter (D-06) and
 * the errors with their line. A change to the library recompiles every turret against it.
 *
 * <p>Phase 9b: «Separar» opens the open tab's code in a floating window (D-67) that edits the same
 * text; closing it puts it back. Breakpoints set in either go to the wave loop, and the debugger
 * shows its line in both. Every editor autocompletes with what the player owns (D-66).
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

  /** Index of the library's document and editor, after the turrets'. */
  private final int library;

  private LibraryResult libraryResult;
  private boolean libraryShown;
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

  /** Editors in floating windows, by tab; {@code null} where the tab is not popped out. */
  private final CodeEditor[] popped;

  private Windows windows;
  private final TextureRegion white;
  private String vocabularyLibrary;
  private int vocabularyFeatures = -1;

  public EditorPanel(WaveLoop loop, ScriptTexts names, Skin skin, int slots) {
    this.loop = loop;
    this.names = names;
    this.skin = skin;
    library = slots;
    documents = new EditorDocument[slots + 1];
    editors = new CodeEditor[slots + 1];
    compiledVersion = new int[slots + 1];
    results = new CompileResult[slots];
    white = new TextureRegion(skin.get("white", Texture.class));
    popped = new CodeEditor[slots + 1];
    for (int i = 0; i <= slots; i++) {
      documents[i] = new EditorDocument(i == library ? loop.libraryDraft() : loop.draft(i));
      editors[i] = newEditor(i);
      compiledVersion[i] = -1;
    }
    scroll = new ScrollPane(editors[0], skin);
    scroll.setFadeScrollBars(false);
    memory = new Label("", skin);
    memory.setWrap(true);
    status = new Label("", skin, "small");
    status.setWrap(true);
    errors = new Label("", skin, "warning");
    errors.setWrap(true);

    top().left();
    add(tabs).left().padBottom(6).row();
    add(scroll).width(WIDTH).height(290).row();
    add(memory).left().width(WIDTH).padTop(6).row();
    add(status).left().width(WIDTH).padTop(2).row();
    add(errors).left().width(WIDTH).padTop(4).row();
    Table actions = new Table();
    actions.add(button("editor.popOut", () -> popOut(slot))).padRight(6);
    actions.add(button("editor.stepCycle", this::stepCycle)).padRight(6);
    add(actions).left().padTop(6).row();
    Label help = new Label(names.texts().get("editor.help"), skin, "small");
    help.setWrap(true);
    add(help).left().width(WIDTH).padTop(6).row();
    Label rule = new Label(names.texts().get("script.memory.rule"), skin, "small");
    rule.setWrap(true);
    add(rule).left().width(WIDTH).padTop(2);
    rebuildTabs();
  }

  private CodeEditor newEditor(int index) {
    CodeEditor editor = new CodeEditor(documents[index], skin.getFont("code"), white);
    editor.onBreakpointsChanged(() -> breakpointsChanged(index, editor));
    return editor;
  }

  private TextButton button(String key, Runnable action) {
    TextButton button = new TextButton(names.texts().get(key), skin, "default");
    button.pad(3, 10, 3, 10);
    button.getLabel().setStyle(skin.get("body", Label.LabelStyle.class));
    button.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            button.setChecked(false);
            action.run();
          }
        });
    return button;
  }

  /** Where popped-out code goes; without it «Separar» does nothing (tests). */
  public void setWindows(Windows windows) {
    this.windows = windows;
  }

  /** Opens a tab's code in a floating window, or brings that window to the front. */
  public void popOut(int index) {
    if (windows == null) {
      return;
    }
    String title =
        index == library
            ? names.texts().get("editor.library.tab")
            : names.texts().format("editor.turret", index + 1);
    boolean created = !windows.isOpen("code-" + index);
    FloatingWindow window =
        windows.open(
            "code-" + index,
            title,
            () -> {
              CodeEditor editor = newEditor(index);
              editor.setBreakpoints(editors[index].breakpoints());
              editor.setVocabulary(vocabulary());
              popped[index] = editor;
              compiledVersion[index] = -1;
              ScrollPane pane = new ScrollPane(editor, skin);
              pane.setFadeScrollBars(false);
              return pane;
            },
            WIDTH,
            360);
    if (created) {
      window.onClose(() -> popped[index] = null);
    }
  }

  /** The editor in a tab's floating window, or {@code null}. */
  public CodeEditor popped(int index) {
    return popped[index];
  }

  /** The tab's editor in the panel. */
  public CodeEditor editor(int index) {
    return editors[index];
  }

  public int librarySlot() {
    return library;
  }

  private void breakpointsChanged(int index, CodeEditor from) {
    java.util.BitSet lines = from.breakpoints();
    editors[index].setBreakpoints(lines);
    if (popped[index] != null) {
      popped[index].setBreakpoints(lines);
    }
    if (index == library) {
      loop.setLibraryBreakpoints(lines);
    } else {
      loop.setBreakpoints(index, lines);
    }
  }

  /** «Pausar en el siguiente ciclo» for the open turret, or the first one from the library. */
  private void stepCycle() {
    loop.stepCycle(slot == library ? 0 : slot);
  }

  /**
   * The debugger shows a line: the turret's tab (or the library's) opens and the line is marked in
   * yellow; -1 clears the mark everywhere.
   */
  public void showDebugLine(int turret, boolean inLibrary, int line) {
    for (int i = 0; i < editors.length; i++) {
      editors[i].setDebugLine(-1);
      if (popped[i] != null) {
        popped[i].setDebugLine(-1);
      }
    }
    if (line < 0) {
      return;
    }
    int index = inLibrary ? library : turret;
    if (index != slot && (index < tabCount || index == library && libraryShown)) {
      select(index);
    }
    editors[index].setDebugLine(line);
    if (popped[index] != null) {
      popped[index].setDebugLine(line);
    }
    if (index == slot) {
      float lineHeight = skin.getFont("code").getLineHeight();
      scroll.layout();
      scroll.scrollTo(0, editors[index].getHeight() - (line + 1) * lineHeight, 1, lineHeight * 3);
    }
  }

  /** The word under the mouse in any editor, or "": the debugger shows its value. */
  public String hoveredWord(int index) {
    String word = editors[index].hoveredWord();
    if (word.isEmpty() && popped[index] != null) {
      word = popped[index].hoveredWord();
    }
    return word;
  }

  private Vocabulary vocabulary() {
    return Vocabulary.of(names.api(), loop.progress().unlocks(), loop.library());
  }

  /** Keeps every editor's suggestions in step with what is owned and the library's methods. */
  private void refreshVocabulary() {
    int features =
        loop.progress().features().hashCode() * 31 + loop.progress().highestWaveReached();
    if (features == vocabularyFeatures && loop.library().equals(vocabularyLibrary)) {
      return;
    }
    vocabularyFeatures = features;
    vocabularyLibrary = loop.library();
    Vocabulary vocabulary = vocabulary();
    for (int i = 0; i < editors.length; i++) {
      editors[i].setVocabulary(vocabulary);
      if (popped[i] != null) {
        popped[i].setVocabulary(vocabulary);
      }
    }
  }

  /** The draft of a turret slot, as the player is writing it. */
  public EditorDocument document(int slot) {
    return documents[slot];
  }

  /** The library's draft, as the player is writing it. */
  public EditorDocument libraryDocument() {
    return documents[library];
  }

  /** The latest compilation of the open turret's draft; {@code null} while the library is open. */
  public CompileResult result() {
    return slot == library ? null : results[slot];
  }

  /** The latest compilation of the library. */
  public LibraryResult libraryResult() {
    return libraryResult;
  }

  /** Opens the library's tab, if methods are owned. */
  public void openLibrary() {
    if (libraryShown) {
      select(library);
    }
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
    refreshVocabulary();
    if (tabCount != loop.progress().turretCount() || libraryShown != ownsMethods()) {
      rebuildTabs();
    }
    // The library first: the turrets compile against it.
    if (libraryShown && compiledVersion[library] != documents[library].version()) {
      compile(library);
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

  private boolean ownsMethods() {
    return loop.progress().unlocks().has(Feature.METHODS);
  }

  private void rebuildTabs() {
    tabCount = loop.progress().turretCount();
    libraryShown = ownsMethods();
    if (slot >= tabCount && !(slot == library && libraryShown)) {
      select(0);
    }
    tabs.clearChildren();
    ButtonGroup<TextButton> group = new ButtonGroup<>();
    for (int i = 0; i < tabCount; i++) {
      int index = i;
      TextButton tab = new TextButton(names.texts().format("editor.turret", i + 1), skin);
      // Four turret tabs must fit the panel's width.
      tab.pad(4, 8, 4, 8);
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
      tabs.add(tab).padRight(4);
    }
    if (libraryShown) {
      // Its own row: shared by every turret, not one more of them.
      tabs.row();
      TextButton tab = new TextButton(names.texts().get("editor.library.tab"), skin);
      tab.pad(4, 8, 4, 8);
      tab.setChecked(slot == library);
      group.add(tab);
      tab.addListener(
          new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
              if (tab.isChecked()) {
                select(library);
              }
            }
          });
      tabs.add(tab).colspan(Math.max(1, tabCount)).left().padTop(4);
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
    List<CompileError> found;
    SortedMap<Integer, Integer> memoryByLine;
    if (index == library) {
      String before = loop.library();
      libraryResult = loop.setLibrary(document.text());
      found = libraryResult.errors();
      memoryByLine = java.util.Collections.emptySortedMap();
      if (!loop.library().equals(before)) {
        // Every turret's code is checked again against the new library.
        java.util.Arrays.fill(compiledVersion, 0, library, -1);
      }
    } else {
      CompileResult result = loop.setScript(index, document.text());
      results[index] = result;
      found = result.errors();
      memoryByLine = result.memoryByLine();
    }
    Map<Integer, Integer> errorColumns = new HashMap<>();
    for (CompileError error : found) {
      errorColumns.putIfAbsent(error.line(), error.column());
    }
    editors[index].setDiagnostics(memoryByLine, errorColumns);
    if (popped[index] != null) {
      popped[index].setDiagnostics(memoryByLine, errorColumns);
    }
    if (index == slot) {
      showStatus();
      showErrors();
    }
  }

  /** The errors of the open tab's latest compilation. */
  private List<CompileError> shownErrors() {
    if (slot == library) {
      return libraryResult == null ? List.of() : libraryResult.errors();
    }
    return results[slot] == null ? List.of() : results[slot].errors();
  }

  private void showStatus() {
    if (slot == library) {
      memory.setText(names.texts().get("editor.library.hint"));
      memory.setColor(com.badlogic.gdx.graphics.Color.WHITE);
      List<CompileError> found = shownErrors();
      status.setText(
          found.isEmpty()
              ? names.texts().get("editor.library.ok")
              : names.texts().format("editor.errors", found.size()));
      return;
    }
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
    List<CompileError> found = shownErrors();
    if (found.isEmpty()) {
      errors.setText("");
      return;
    }
    int hovered = editors[slot].hoveredLine() + 1;
    StringBuilder text = new StringBuilder();
    int shown = 0;
    for (int pass = 0; pass < 2 && shown < ERRORS_SHOWN; pass++) {
      for (CompileError error : found) {
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
