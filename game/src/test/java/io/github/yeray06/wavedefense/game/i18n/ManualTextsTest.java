package io.github.yeray06.wavedefense.game.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import io.github.yeray06.wavedefense.game.GameTestData;
import io.github.yeray06.wavedefense.game.Headless;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.ManualConfig;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Every manual page has its texts, and every number they quote comes from the data. */
class ManualTextsTest {
  @Test
  void everyPageChapterAndExampleHasItsTextAndNoTextIsLeftOver() {
    Headless.run(
        () -> {
          GameData data = GameTestData.load();
          Properties properties = new Properties();
          try (Reader reader = Gdx.files.internal("i18n/messages.properties").reader("UTF-8")) {
            properties.load(reader);
          } catch (IOException e) {
            throw new AssertionError(e);
          }
          Set<String> expected = new HashSet<>();
          for (ManualConfig.Chapter chapter : data.manual().chapters()) {
            expected.add("manual.chapter." + chapter.id());
            for (ManualConfig.Page page : chapter.pages()) {
              String base = "manual.page." + page.id() + ".";
              expected.add(base + "title");
              expected.add(base + "body");
              for (int i = 1; i <= page.examples().size(); i++) {
                expected.add(base + "example." + i);
              }
            }
          }
          Set<String> present = new HashSet<>();
          for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("manual.page.") || key.startsWith("manual.chapter.")) {
              present.add(key);
            }
          }
          Set<String> missing = new HashSet<>(expected);
          missing.removeAll(present);
          assertTrue(missing.isEmpty(), "missing: " + missing);
          // Optional parts (phase 9b): a page's story and hint, checked like the rest.
          for (ManualConfig.Page page : data.manual().pages()) {
            for (String part : new String[] {"story", "hint"}) {
              String key = "manual.page." + page.id() + "." + part;
              if (present.contains(key)) {
                expected.add(key);
              }
            }
          }
          Set<String> leftOver = new HashSet<>(present);
          leftOver.removeAll(expected);
          assertTrue(leftOver.isEmpty(), "not used by any page: " + leftOver);

          ManualFacts facts = new ManualFacts(data);
          for (String key : expected) {
            String text = properties.getProperty(key);
            assertFalse(text.isBlank(), key);
            for (String name : ManualFacts.placeholders(text)) {
              assertTrue(facts.has(name), key + " quotes an unknown number: " + name);
            }
            assertFalse(facts.fill(text).matches("(?s).*\\{[A-Za-z_.]+}.*"), key);
          }
        });
  }

  @Test
  void numbersComeFromTheDataInSpanish() {
    Headless.run(
        () -> {
          ManualFacts facts = new ManualFacts(GameTestData.load());
          assertEquals(
              "12 balas, 0,3 de vida", facts.fill("{cargador} balas, {vida.SWARM} de vida"));
          assertEquals("+25 %", facts.fill("+{nivel.DAMAGE} %"));
          assertEquals("oleada 6", facts.fill("oleada {oleadaMunicion}"));
          assertEquals("oleada 8", facts.fill("oleada {oleada.SWARM}"));
          assertEquals("{ }", facts.fill("{ }"), "braces that are not a name stay");
        });
  }
}
