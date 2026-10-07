package io.github.yeray06.wavedefense.game.editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Align;
import io.github.yeray06.wavedefense.game.editor.Highlighter.Span;
import java.util.List;

/**
 * Read-only code with the editor's colours, for the manual's examples. The text never changes, so
 * it is highlighted once.
 */
public final class CodeView extends Widget {
  private static final float PAD = 8;

  private final String[] lines;
  private final List<List<Span>> spans;
  private final BitmapFont font;
  private final TextureRegion white;
  private final float charWidth;
  private final float lineHeight;
  private final int longestLine;
  private final Color batchColor = new Color();

  public CodeView(String code, BitmapFont font, TextureRegion white) {
    this.lines = code.split("\n", -1);
    this.spans = Highlighter.highlight(code);
    this.font = font;
    this.white = white;
    this.charWidth = font.getData().getGlyph('M').xadvance * font.getScaleX();
    this.lineHeight = font.getLineHeight();
    int longest = 0;
    for (String line : lines) {
      longest = Math.max(longest, line.length());
    }
    this.longestLine = longest;
  }

  @Override
  public float getPrefWidth() {
    return longestLine * charWidth + 2 * PAD;
  }

  @Override
  public float getPrefHeight() {
    return lines.length * lineHeight + 2 * PAD;
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    batchColor.set(batch.getColor());
    Color background = CodeColors.BACKGROUND;
    batch.setColor(background.r, background.g, background.b, background.a * parentAlpha);
    batch.draw(white, getX(), getY(), getWidth(), getHeight());
    batch.setColor(batchColor);
    for (int line = 0; line < lines.length; line++) {
      float top = getY() + getHeight() - PAD - line * lineHeight;
      float baseline = top - (lineHeight - font.getCapHeight()) / 2;
      for (Span span : spans.get(line)) {
        font.setColor(CodeColors.of(span.style()));
        font.draw(
            batch,
            lines[line],
            getX() + PAD + span.start() * charWidth,
            baseline,
            span.start(),
            span.end(),
            0,
            Align.left,
            false);
      }
    }
  }
}
