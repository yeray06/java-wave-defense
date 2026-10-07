package io.github.yeray06.wavedefense.game.ui;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
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
  public static final Color CODE = new Color(0.85f, 0.88f, 0.70f, 1f);

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

    skin.add("panel", tinted(region, 0.10f, 0.11f, 0.14f, 0.96f), Drawable.class);
    skin.add("row", tinted(region, 0.14f, 0.15f, 0.19f, 1f), Drawable.class);
    skin.add("title", new Label.LabelStyle(font, GOOD));
    skin.add("code", new Label.LabelStyle(small, CODE));
    Window.WindowStyle window = new Window.WindowStyle(font, TEXT, skin.getDrawable("panel"));
    window.stageBackground = tinted(region, 0f, 0f, 0f, 0.55f);
    skin.add("default", window);
    ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
    scroll.vScrollKnob = tinted(region, 0.35f, 0.38f, 0.45f, 1f);
    scroll.hScrollKnob = scroll.vScrollKnob;
    skin.add("default", scroll);

    addIcon(skin, "circle", circle(ICON, false));
    addIcon(skin, "ring", circle(ICON, true));
    addIcon(skin, "lock", lock());
    addIcon(skin, "flag", flag());
    return skin;
  }

  /** Icon size in pixels; icons are white and tinted where they are used. */
  public static final int ICON = 32;

  private static void addIcon(Skin skin, String name, Pixmap pixmap) {
    Texture texture = new Texture(pixmap);
    texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    pixmap.dispose();
    skin.add(name + "-texture", texture, Texture.class);
    skin.add(name, new TextureRegionDrawable(new TextureRegion(texture)), Drawable.class);
  }

  private static Pixmap circle(int size, boolean ring) {
    Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
    pixmap.setColor(Color.WHITE);
    pixmap.fillCircle(size / 2, size / 2, size / 2 - 1);
    if (ring) {
      pixmap.setBlending(Pixmap.Blending.None);
      pixmap.setColor(0, 0, 0, 0);
      pixmap.fillCircle(size / 2, size / 2, size / 2 - 5);
    }
    return pixmap;
  }

  /** A padlock: shackle ring on top, solid body below. */
  private static Pixmap lock() {
    Pixmap pixmap = new Pixmap(ICON, ICON, Pixmap.Format.RGBA8888);
    pixmap.setColor(Color.WHITE);
    pixmap.fillCircle(ICON / 2, 12, 8);
    pixmap.setBlending(Pixmap.Blending.None);
    pixmap.setColor(0, 0, 0, 0);
    pixmap.fillCircle(ICON / 2, 12, 4);
    pixmap.setColor(Color.WHITE);
    pixmap.fillRectangle(6, 14, ICON - 12, ICON - 16);
    return pixmap;
  }

  /** A flag: pole on the left, pennant to the right. */
  private static Pixmap flag() {
    Pixmap pixmap = new Pixmap(ICON, ICON, Pixmap.Format.RGBA8888);
    pixmap.setColor(Color.WHITE);
    pixmap.fillRectangle(6, 2, 3, ICON - 4);
    pixmap.fillTriangle(9, 2, 9, 16, ICON - 4, 9);
    return pixmap;
  }

  private static BitmapFont font(FreeTypeFontGenerator generator, int size) {
    FreeTypeFontGenerator.FreeTypeFontParameter parameter =
        new FreeTypeFontGenerator.FreeTypeFontParameter();
    parameter.size = size;
    parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + EXTRA_CHARS;
    return generator.generateFont(parameter);
  }

  private static Drawable tinted(TextureRegion region, float r, float g, float b) {
    return tinted(region, r, g, b, 1f);
  }

  private static Drawable tinted(TextureRegion region, float r, float g, float b, float a) {
    return new TextureRegionDrawable(region).tint(new Color(r, g, b, a));
  }
}
