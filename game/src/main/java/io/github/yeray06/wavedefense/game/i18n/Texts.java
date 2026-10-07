package io.github.yeray06.wavedefense.game.i18n;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;
import java.text.NumberFormat;
import java.util.Locale;

/** Every visible text, from {@code i18n/messages.properties} (Spanish by default). */
public final class Texts {
  private static final Locale SPANISH = Locale.of("es");

  private final I18NBundle bundle;

  private Texts(I18NBundle bundle) {
    this.bundle = bundle;
  }

  /**
   * @param base the bundle without extension, e.g. {@code i18n/messages}
   */
  public static Texts load(FileHandle base) {
    I18NBundle bundle = I18NBundle.createBundle(base, SPANISH, "UTF-8");
    return new Texts(bundle);
  }

  public String get(String key) {
    return bundle.get(key);
  }

  /**
   * Fills a text's {@code {0}}, {@code {1}}… Numbers are written the Spanish way here: the bundle
   * would format them for the base file's root locale ("1,234").
   */
  public String format(String key, Object... args) {
    Object[] shown = args.clone();
    for (int i = 0; i < shown.length; i++) {
      if (shown[i] instanceof Number number) {
        shown[i] = NumberFormat.getIntegerInstance(SPANISH).format(number);
      }
    }
    return bundle.format(key, shown);
  }
}
