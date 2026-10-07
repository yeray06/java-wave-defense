package io.github.yeray06.wavedefense.game.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The code editor's text, cursor and selection, without any drawing: everything the keyboard and
 * mouse do to the script lives here so it can be tested on its own.
 *
 * <p>Positions are 0-based (line, column). The selection runs from an anchor to the cursor; it is
 * empty when both are equal. Typing replaces the selection. Enter keeps the line's indentation and
 * adds a level after <code>{</code>; a <code>}</code> typed on a blank line takes one level back.
 * Every change can be undone; consecutive typing undoes as one step.
 */
public final class EditorDocument {
  /** Spaces per indentation level, as in the plan's examples. */
  public static final int INDENT = 2;

  private static final int UNDO_LIMIT = 200;

  private final List<StringBuilder> lines = new ArrayList<>();
  private final Deque<Snapshot> undo = new ArrayDeque<>();
  private final Deque<Snapshot> redo = new ArrayDeque<>();
  private int line;
  private int column;
  private int anchorLine;
  private int anchorColumn;
  private int version;
  private boolean typing;

  private record Snapshot(String text, int line, int column) {}

  public EditorDocument(String text) {
    load(text);
  }

  // ---------------------------------------------------------------- reading

  public String text() {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < lines.size(); i++) {
      if (i > 0) {
        out.append('\n');
      }
      out.append(lines.get(i));
    }
    return out.toString();
  }

  public int lineCount() {
    return lines.size();
  }

  public String line(int index) {
    return lines.get(index).toString();
  }

  /** A line without copying it, for drawing; valid until the next change. */
  public CharSequence lineChars(int index) {
    return lines.get(index);
  }

  /** Length of a line, without copying it. */
  public int lineLength(int index) {
    return lines.get(index).length();
  }

  public int cursorLine() {
    return line;
  }

  public int cursorColumn() {
    return column;
  }

  /** Grows on every change; the editor recompiles when it moves. */
  public int version() {
    return version;
  }

  public boolean hasSelection() {
    return line != anchorLine || column != anchorColumn;
  }

  /** Start of the selection (or the cursor), as {line, column}. */
  public int[] selectionStart() {
    return before(anchorLine, anchorColumn, line, column)
        ? new int[] {anchorLine, anchorColumn}
        : new int[] {line, column};
  }

  /** End of the selection (or the cursor), as {line, column}. */
  public int[] selectionEnd() {
    return before(anchorLine, anchorColumn, line, column)
        ? new int[] {line, column}
        : new int[] {anchorLine, anchorColumn};
  }

  public String selectedText() {
    int[] start = selectionStart();
    int[] end = selectionEnd();
    if (start[0] == end[0]) {
      return lines.get(start[0]).substring(start[1], end[1]);
    }
    StringBuilder out = new StringBuilder(lines.get(start[0]).substring(start[1]));
    for (int i = start[0] + 1; i < end[0]; i++) {
      out.append('\n').append(lines.get(i));
    }
    return out.append('\n').append(lines.get(end[0]), 0, end[1]).toString();
  }

  // ---------------------------------------------------------------- editing

  /** Replaces the whole text (no undo step); the cursor goes to the start. */
  public void load(String text) {
    lines.clear();
    for (String part : text.replace("\r", "").replace("\t", " ".repeat(INDENT)).split("\n", -1)) {
      lines.add(new StringBuilder(part));
    }
    line = column = anchorLine = anchorColumn = 0;
    undo.clear();
    redo.clear();
    typing = false;
    version++;
  }

  /** Typed characters or pasted text; replaces the selection. */
  public void insert(String text) {
    String clean = text.replace("\r", "").replace("\t", " ".repeat(INDENT));
    if (clean.isEmpty()) {
      return;
    }
    boolean single = clean.length() == 1 && clean.charAt(0) != '\n';
    remember(single);
    deleteSelection();
    if (clean.equals("}") && lines.get(line).toString().isBlank()) {
      int indent = Math.max(0, lines.get(line).length() - INDENT);
      lines.get(line).setLength(indent);
      column = indent;
    }
    String[] parts = clean.split("\n", -1);
    StringBuilder current = lines.get(line);
    String tail = current.substring(column);
    current.setLength(column);
    current.append(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      StringBuilder next = new StringBuilder(parts[i]);
      lines.add(++line, next);
      current = next;
    }
    column = current.length();
    current.append(tail);
    collapse();
    version++;
  }

  /** Enter: a new line with this line's indentation, one level more after an opening brace. */
  public void newline() {
    String before = lines.get(line).substring(0, Math.min(column, lines.get(line).length()));
    int indent = 0;
    while (indent < before.length() && before.charAt(indent) == ' ') {
      indent++;
    }
    if (before.stripTrailing().endsWith("{")) {
      indent += INDENT;
    }
    remember(false);
    insertRaw("\n" + " ".repeat(indent));
  }

  /** Tab: spaces up to the next indentation stop. */
  public void tab() {
    insert(" ".repeat(INDENT - column % INDENT));
  }

  public void backspace() {
    if (hasSelection()) {
      remember(false);
      deleteSelection();
      version++;
      return;
    }
    if (line == 0 && column == 0) {
      return;
    }
    remember(false);
    if (column > 0) {
      lines.get(line).deleteCharAt(--column);
    } else {
      StringBuilder previous = lines.get(line - 1);
      column = previous.length();
      previous.append(lines.remove(line));
      line--;
    }
    collapse();
    version++;
  }

  public void delete() {
    if (hasSelection()) {
      backspace();
      return;
    }
    if (line == lines.size() - 1 && column == lines.get(line).length()) {
      return;
    }
    remember(false);
    if (column < lines.get(line).length()) {
      lines.get(line).deleteCharAt(column);
    } else {
      lines.get(line).append(lines.remove(line + 1));
    }
    collapse();
    version++;
  }

  /** Cut: returns the selection and removes it. */
  public String cut() {
    String text = selectedText();
    if (hasSelection()) {
      backspace();
    }
    return text;
  }

  public boolean undo() {
    if (undo.isEmpty()) {
      return false;
    }
    redo.push(snapshot());
    restore(undo.pop());
    return true;
  }

  public boolean redo() {
    if (redo.isEmpty()) {
      return false;
    }
    undo.push(snapshot());
    restore(redo.pop());
    return true;
  }

  // --------------------------------------------------------------- movement

  public void left(boolean select) {
    if (!select && hasSelection()) {
      int[] start = selectionStart();
      moveTo(start[0], start[1], false);
      return;
    }
    if (column > 0) {
      moveTo(line, column - 1, select);
    } else if (line > 0) {
      moveTo(line - 1, lines.get(line - 1).length(), select);
    }
  }

  public void right(boolean select) {
    if (!select && hasSelection()) {
      int[] end = selectionEnd();
      moveTo(end[0], end[1], false);
      return;
    }
    if (column < lines.get(line).length()) {
      moveTo(line, column + 1, select);
    } else if (line < lines.size() - 1) {
      moveTo(line + 1, 0, select);
    }
  }

  public void up(boolean select) {
    moveTo(Math.max(0, line - 1), column, select);
  }

  public void down(boolean select) {
    moveTo(Math.min(lines.size() - 1, line + 1), column, select);
  }

  /** Home: to the first non-blank character, or to column 0 if already there. */
  public void home(boolean select) {
    String text = lines.get(line).toString();
    int first = text.length() - text.stripLeading().length();
    moveTo(line, column == first ? 0 : first, select);
  }

  public void end(boolean select) {
    moveTo(line, lines.get(line).length(), select);
  }

  public void selectAll() {
    anchorLine = 0;
    anchorColumn = 0;
    line = lines.size() - 1;
    column = lines.get(line).length();
    typing = false;
  }

  /** Puts the cursor at a position, clamped to the text; extends the selection if asked. */
  public void moveTo(int toLine, int toColumn, boolean select) {
    line = Math.max(0, Math.min(lines.size() - 1, toLine));
    column = Math.max(0, Math.min(lines.get(line).length(), toColumn));
    if (!select) {
      collapse();
    }
    typing = false;
  }

  // ---------------------------------------------------------------- helpers

  private void insertRaw(String text) {
    deleteSelection();
    String[] parts = text.split("\n", -1);
    StringBuilder current = lines.get(line);
    String tail = current.substring(column);
    current.setLength(column);
    current.append(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      StringBuilder next = new StringBuilder(parts[i]);
      lines.add(++line, next);
      current = next;
    }
    column = current.length();
    current.append(tail);
    collapse();
    version++;
  }

  private void deleteSelection() {
    if (!hasSelection()) {
      return;
    }
    int[] start = selectionStart();
    int[] end = selectionEnd();
    StringBuilder first = lines.get(start[0]);
    String tail = lines.get(end[0]).substring(end[1]);
    first.setLength(start[1]);
    first.append(tail);
    for (int i = end[0]; i > start[0]; i--) {
      lines.remove(i);
    }
    line = start[0];
    column = start[1];
    collapse();
  }

  /** Saves an undo step, except while the player keeps typing characters. */
  private void remember(boolean typed) {
    if (!(typed && typing)) {
      undo.push(snapshot());
      if (undo.size() > UNDO_LIMIT) {
        undo.removeLast();
      }
      redo.clear();
    }
    typing = typed;
  }

  private Snapshot snapshot() {
    return new Snapshot(text(), line, column);
  }

  private void restore(Snapshot snapshot) {
    lines.clear();
    for (String part : snapshot.text().split("\n", -1)) {
      lines.add(new StringBuilder(part));
    }
    moveTo(snapshot.line(), snapshot.column(), false);
    version++;
  }

  private void collapse() {
    anchorLine = line;
    anchorColumn = column;
  }

  private static boolean before(int l1, int c1, int l2, int c2) {
    return l1 < l2 || (l1 == l2 && c1 <= c2);
  }
}
