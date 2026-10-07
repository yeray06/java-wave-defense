package io.github.yeray06.wavedefense.game.ui;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/**
 * The scene2d skin, built in code: one monospaced font (JetBrains Mono, OFL) in two sizes and flat
 * buttons. No skin files to keep in sync.
 */
public final class UiSkin {
  /** Characters the Spanish texts use beyond ASCII. */
  private static final String EXTRA_CHARS = "áéíóúÁÉÍÓÚüÜñÑ¿¡«»×…·–—";

  private static final int FONT_SIZE = 18;
  private static final int SMALL_FONT_SIZE = 14;

  public static final Color TEXT = new Color(0.90f, 0.92f, 0.95f, 1f);
  public static final Color MUTED = new Color(0.60f, 0.64f, 0.70f, 1f);
  public static final Color WARNING = new Color(1f, 0.45f, 0.40f, 1f);
  public static final Color GOOD = new Color(0.55f, 0.90f, 0.55f, 1f);

  private UiSkin() {}

  /**
   * @param fontFile the TTF file
   */
  public static Skin create(FileHandle fontFile) {
    Skin skin = new Skin();
    FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fontFile);
    try {
      skin.add("default", font(generator, FONT_SIZE), BitmapFont.class);
      skin.add("small", font(generator, SMALL_FONT_SIZE), BitmapFont.class);
    } finally {
      generator.dispose();
    }

    Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixel.setColor(Color.WHITE);
    pixel.fill();
    Texture white = new Texture(pixel);
    pixel.dispose();
    skin.add("white", white, Texture.class);
    TextureRegion region = new TextureRegion(white);

    BitmapFont font = skin.getFont("default");
    BitmapFont small = skin.getFont("small");
    skin.add("default", new Label.LabelStyle(font, TEXT));
    skin.add("small", new Label.LabelStyle(small, MUTED));
    skin.add("warning", new Label.LabelStyle(small, WARNING));
    skin.add("good", new Label.LabelStyle(font, GOOD));

    TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
    button.up = tinted(region, 0.20f, 0.24f, 0.30f);
    button.over = tinted(region, 0.27f, 0.32f, 0.40f);
    button.down = tinted(region, 0.14f, 0.17f, 0.22f);
    button.checked = tinted(region, 0.22f, 0.36f, 0.30f);
    button.disabled = tinted(region, 0.12f, 0.13f, 0.15f);
    button.font = font;
    button.fontColor = TEXT;
    button.disabledFontColor = MUTED;
    skin.add("default", button);
    return skin;
  }

  private static BitmapFont font(FreeTypeFontGenerator generator, int size) {
    FreeTypeFontGenerator.FreeTypeFontParameter parameter =
        new FreeTypeFontGenerator.FreeTypeFontParameter();
    parameter.size = size;
    parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + EXTRA_CHARS;
    return generator.generateFont(parameter);
  }

  private static Drawable tinted(TextureRegion region, float r, float g, float b) {
    return new TextureRegionDrawable(region).tint(new Color(r, g, b, 1f));
  }
}
