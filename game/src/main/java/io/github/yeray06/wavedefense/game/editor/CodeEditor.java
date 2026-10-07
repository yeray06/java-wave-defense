package io.github.yeray06.wavedefense.game.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Align;
import io.github.yeray06.wavedefense.game.editor.Highlighter.Span;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The script editor widget (ARCHITECTURE §6): line numbers, colours from the game's lexer, the
 * memory each line takes (D-06), a red mark and underline on lines with errors, cursor, selection,
 * clipboard and undo. The text itself lives in {@link EditorDocument}; this class only draws it and
 * turns keys and clicks into document operations. Put it in a {@code ScrollPane}.
 */
public final class CodeEditor extends Widget {
  private static final float PAD = 6;
  private static final float GUTTER_CHARS = 5;
  private static final float REPEAT_DELAY = 0.4f;
  private static final float REPEAT_INTERVAL = 0.035f;
  private static final float BLINK = 0.53f;
  private static final int MEMORY_BARS = 3;

  private static final Color GUTTER = new Color(0.12f, 0.13f, 0.16f, 1f);
  private static final Color LINE_NUMBER = new Color(0.45f, 0.48f, 0.54f, 1f);
  private static final Color MEMORY = new Color(0.55f, 0.75f, 0.95f, 1f);
  private static final Color SELECTION = new Color(0.25f, 0.35f, 0.55f, 0.6f);
  private static final Color ERROR_LINE = new Color(0.55f, 0.15f, 0.15f, 0.35f);
  private static final Color ERROR_MARK = new Color(0.95f, 0.35f, 0.35f, 1f);
  private static final Color CURSOR = new Color(0.95f, 0.95f, 0.95f, 1f);

  private final EditorDocument document;
  private final BitmapFont font;
  private final TextureRegion white;
  private final float charWidth;
  private final float lineHeight;
  private final float gutterWidth;
  private final String[] numbers = new String[200];
  private final Rectangle cursorBounds = new Rectangle();
  private final Color batchColor = new Color();

  private List<List<Span>> spans = List.of();
  private int spansVersion = -1;
  private SortedMap<Integer, Integer> memoryByLine = new TreeMap<>();
  private Map<Integer, Integer> errorColumns = Map.of();
  private int hoveredLine = -1;
  private float blink;
  private int repeatKey = -1;
  private float repeatTimer;
  private int longestLine;
  private int lineCount;

  public CodeEditor(EditorDocument document, BitmapFont font, TextureRegion white) {
    this.document = document;
    this.font = font;
    this.white = white;
    this.charWidth = font.getData().getGlyph('M').xadvance * font.getScaleX();
    this.lineHeight = font.getLineHeight();
    this.gutterWidth = GUTTER_CHARS * charWidth + PAD;
    addListener(new Keys());
    measure();
  }

  public EditorDocument document() {
    return document;
  }

  /**
   * The latest compilation's view of the script.
   *
   * @param memory memory lines per 1-based source line
   * @param errors 1-based columns of the first error on each 1-based line
   */
  public void setDiagnostics(SortedMap<Integer, Integer> memory, Map<Integer, Integer> errors) {
    this.memoryByLine = memory;
    this.errorColumns = errors;
  }

  /** The 0-based line under the mouse, or -1. */
  public int hoveredLine() {
    return hoveredLine;
  }

  /** Where the cursor is, in the widget's coordinates, for the scroll pane to follow. */
  public Rectangle cursorBounds() {
    float x = textX() + document.cursorColumn() * charWidth;
    float y = lineTop(document.cursorLine()) - lineHeight;
    return cursorBounds.set(x - charWidth, y - lineHeight, charWidth * 3, lineHeight * 3);
  }

  // ----------------------------------------------------------------- layout

  @Override
  public float getPrefWidth() {
    return textX() + (longestLine + 2) * charWidth + PAD;
  }

  @Override
  public float getPrefHeight() {
    return lineCount * lineHeight + 2 * PAD;
  }

  private void measure() {
    int longest = 0;
    for (int i = 0; i < document.lineCount(); i++) {
      longest = Math.max(longest, document.lineLength(i));
    }
    if (longest != longestLine || document.lineCount() != lineCount) {
      longestLine = longest;
      lineCount = document.lineCount();
      invalidateHierarchy();
    }
  }

  private float textX() {
    return gutterWidth + PAD;
  }

  private float lineTop(int line) {
    return getHeight() - PAD - line * lineHeight;
  }

  private int lineAt(float y) {
    return Math.max(
        0, Math.min(document.lineCount() - 1, (int) ((getHeight() - PAD - y) / lineHeight)));
  }

  private int columnAt(float x) {
    return Math.max(0, Math.round((x - textX()) / charWidth));
  }

  // ------------------------------------------------------------------- draw

  @Override
  public void act(float delta) {
    super.act(delta);
    blink = (blink + delta) % (BLINK * 2);
    if (repeatKey >= 0) {
      repeatTimer -= delta;
      if (repeatTimer <= 0) {
        repeatTimer = REPEAT_INTERVAL;
        move(repeatKey);
      }
    }
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    if (spansVersion != document.version()) {
      spans = Highlighter.highlight(document.text());
      spansVersion = document.version();
      measure();
    }
    float x = getX();
    float y = getY();
    batchColor.set(batch.getColor());
    rect(batch, CodeColors.BACKGROUND, x, y, getWidth(), getHeight(), parentAlpha);
    rect(batch, GUTTER, x, y, gutterWidth, getHeight(), parentAlpha);
    drawSelection(batch, x, y, parentAlpha);
    for (int line = 0; line < document.lineCount(); line++) {
      float top = y + lineTop(line);
      Integer errorColumn = errorColumns.get(line + 1);
      if (errorColumn != null) {
        rect(
            batch,
            ERROR_LINE,
            x + gutterWidth,
            top - lineHeight,
            getWidth() - gutterWidth,
            lineHeight,
            parentAlpha);
        rect(batch, ERROR_MARK, x, top - lineHeight, 3, lineHeight, parentAlpha);
      }
      float baseline = top - (lineHeight - font.getCapHeight()) / 2;
      font.setColor(LINE_NUMBER);
      font.draw(
          batch, number(line + 1), x, baseline, gutterWidth - 3 * charWidth, Align.right, false);
      Integer memory = memoryByLine.get(line + 1);
      if (memory != null && memory > 0) {
        // A blue bar per instruction: this line takes memory (D-06).
        for (int m = 0; m < Math.min(memory, MEMORY_BARS); m++) {
          rect(
              batch,
              MEMORY,
              x + gutterWidth - 4 - m * 4,
              top - lineHeight + 3,
              2,
              lineHeight - 6,
              parentAlpha);
        }
      }
      CharSequence text = document.lineChars(line);
      if (line < spans.size()) {
        for (Span span : spans.get(line)) {
          if (span.end() <= span.start() || span.end() > text.length()) {
            continue;
          }
          font.setColor(CodeColors.of(span.style()));
          font.draw(
              batch,
              text,
              x + textX() + span.start() * charWidth,
              baseline,
              span.start(),
              span.end(),
              0,
              Align.left,
              false);
        }
        if (errorColumn != null) {
          int from = Math.max(0, Math.min(text.length(), errorColumn - 1));
          int to = errorEnd(line, from, text.length());
          rect(
              batch,
              ERROR_MARK,
              x + textX() + from * charWidth,
              top - lineHeight + 1,
              Math.max(1, to - from) * charWidth,
              2,
              parentAlpha);
        }
      }
    }
    if (hasKeyboardFocus() && blink < BLINK) {
      rect(
          batch,
          CURSOR,
          x + textX() + document.cursorColumn() * charWidth - 1,
          y + lineTop(document.cursorLine()) - lineHeight,
          2,
          lineHeight,
          parentAlpha);
    }
    batch.setColor(batchColor);
  }

  /** The underline runs to the end of the coloured run the error starts in. */
  private int errorEnd(int line, int from, int length) {
    for (Span span : spans.get(line)) {
      if (from >= span.start() && from < span.end()) {
        return span.end();
      }
    }
    return Math.min(length, from + 1);
  }

  private void drawSelection(Batch batch, float x, float y, float parentAlpha) {
    if (!document.hasSelection()) {
      return;
    }
    int[] start = document.selectionStart();
    int[] end = document.selectionEnd();
    for (int line = start[0]; line <= end[0]; line++) {
      int from = line == start[0] ? start[1] : 0;
      int to = line == end[0] ? end[1] : document.lineLength(line) + 1;
      rect(
          batch,
          SELECTION,
          x + textX() + from * charWidth,
          y + lineTop(line) - lineHeight,
          Math.max(0, to - from) * charWidth,
          lineHeight,
          parentAlpha);
    }
  }

  private void rect(Batch batch, Color color, float x, float y, float w, float h, float alpha) {
    batch.setColor(color.r, color.g, color.b, color.a * alpha);
    batch.draw(white, x, y, w, h);
  }

  private String number(int value) {
    if (value >= numbers.length) {
      return String.valueOf(value);
    }
    if (numbers[value] == null) {
      numbers[value] = String.valueOf(value);
    }
    return numbers[value];
  }

  // ------------------------------------------------------------------ input

  private void move(int keycode) {
    boolean shift = UIUtils.shift();
    switch (keycode) {
      case Input.Keys.LEFT -> document.left(shift);
      case Input.Keys.RIGHT -> document.right(shift);
      case Input.Keys.UP -> document.up(shift);
      case Input.Keys.DOWN -> document.down(shift);
      case Input.Keys.HOME -> document.home(shift);
      case Input.Keys.END -> document.end(shift);
      default -> {
        return;
      }
    }
    blink = 0;
  }

  /** Keyboard and mouse, turned into document operations. */
  private final class Keys extends InputListener {
    @Override
    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
      if (getStage() != null) {
        getStage().setKeyboardFocus(CodeEditor.this);
      }
      document.moveTo(lineAt(y), columnAt(x), UIUtils.shift());
      blink = 0;
      return true;
    }

    @Override
    public void touchDragged(InputEvent event, float x, float y, int pointer) {
      document.moveTo(lineAt(y), columnAt(x), true);
    }

    @Override
    public boolean mouseMoved(InputEvent event, float x, float y) {
      hoveredLine = lineAt(y);
      return false;
    }

    @Override
    public void exit(
        InputEvent event,
        float x,
        float y,
        int pointer,
        com.badlogic.gdx.scenes.scene2d.Actor toActor) {
      if (pointer == -1) {
        hoveredLine = -1;
      }
    }

    @Override
    public boolean keyDown(InputEvent event, int keycode) {
      if (UIUtils.ctrl()) {
        switch (keycode) {
          case Input.Keys.A -> document.selectAll();
          case Input.Keys.C -> copy();
          case Input.Keys.X -> {
            String cut = document.cut();
            if (!cut.isEmpty()) {
              Gdx.app.getClipboard().setContents(cut);
            }
          }
          case Input.Keys.V -> {
            String pasted = Gdx.app.getClipboard().getContents();
            if (pasted != null) {
              document.insert(pasted);
            }
          }
          case Input.Keys.Z -> {
            if (UIUtils.shift()) {
              document.redo();
            } else {
              document.undo();
            }
          }
          case Input.Keys.Y -> document.redo();
          default -> move(keycode);
        }
        return true;
      }
      move(keycode);
      if (keycode == Input.Keys.LEFT
          || keycode == Input.Keys.RIGHT
          || keycode == Input.Keys.UP
          || keycode == Input.Keys.DOWN) {
        repeatKey = keycode;
        repeatTimer = REPEAT_DELAY;
      }
      return true;
    }

    @Override
    public boolean keyUp(InputEvent event, int keycode) {
      if (keycode == repeatKey) {
        repeatKey = -1;
      }
      return true;
    }

    @Override
    public boolean keyTyped(InputEvent event, char character) {
      if (UIUtils.ctrl()) {
        return true;
      }
      switch (character) {
        case '\b' -> document.backspace();
        case 127 -> document.delete();
        case '\r', '\n' -> document.newline();
        case '\t' -> document.tab();
        default -> {
          if (character >= ' ') {
            document.insert(String.valueOf(character));
          }
        }
      }
      blink = 0;
      return true;
    }

    private void copy() {
      if (document.hasSelection()) {
        Gdx.app.getClipboard().setContents(document.selectedText());
      }
    }
  }
}
