package io.github.yeray06.wavedefense.sim.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.diag.ErrorCode;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import io.github.yeray06.wavedefense.sim.progress.PurchaseCheck;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Every code the interpreter can produce has Spanish text, and the plan's message is exact. */
class ScriptMessagesTest {
  private static final Properties MESSAGES = new Properties();

  @BeforeAll
  static void load() throws IOException {
    try (Reader reader =
        Files.newBufferedReader(Path.of("i18n/messages.properties"), StandardCharsets.UTF_8)) {
      MESSAGES.load(reader);
    }
  }

  private static String text(String key) {
    String value = MESSAGES.getProperty(key);
    assertNotNull(value, "missing i18n key " + key);
    return value;
  }

  @Test
  void everyErrorFaultAndFeatureHasText() {
    for (ErrorCode code : ErrorCode.values()) {
      text("script.error." + code.name());
    }
    for (FaultType type : FaultType.values()) {
      text("script.fault." + type.name());
    }
    for (Feature feature : Feature.values()) {
      text("script.feature." + feature.name());
    }
    text("script.memory.rule");
    for (HardwareKind kind : HardwareKind.values()) {
      text("shop.hardware." + kind.name());
    }
    for (PurchaseCheck check : PurchaseCheck.values()) {
      if (check != PurchaseCheck.OK) {
        text("shop.check." + check.name());
      }
    }
  }

  @Test
  void hardwareCapMessageReadsAsApproved() {
    String message =
        new MessageFormat(text("shop.check.NEEDS_SYNTAX"), Locale.ROOT)
            .format(new Object[] {text("script.feature.VARIABLES"), text("shop.hardware.DAMAGE")});
    assertEquals("Necesitas variables para seguir mejorando el daño", message);
  }

  @Test
  void lockedSyntaxShowsThePlansExactMessage() {
    String message =
        new MessageFormat(text("script.error.FEATURE_LOCKED"), Locale.ROOT)
            .format(new Object[] {text("script.feature.WHILE")});
    assertEquals("Aún no has desbloqueado while", message);
  }

  @Test
  void exceptionNamesMatchThePlan() {
    assertEquals("SinMunicionException", text("script.fault.NO_AMMO"));
    assertEquals("SobrecalentamientoException", text("script.fault.OVERHEAT"));
    assertEquals("NullPointerException", text("script.fault.NULL_POINTER"));
    assertEquals("BucleInfinitoException", text("script.fault.INFINITE_LOOP"));
  }

  @Test
  void messagesAreValidPatternsWithoutQuotePitfalls() {
    for (String key : MESSAGES.stringPropertyNames()) {
      String value = MESSAGES.getProperty(key);
      // A single quote silently swallows text in MessageFormat (and in libGDX's I18NBundle).
      assertFalse(value.contains("'"), key);
      new MessageFormat(value, Locale.ROOT).format(new Object[] {"a", "b", "c"});
    }
  }

  @Test
  void everyShopPieceSaysWhatItTakesOffYourHands() {
    for (Feature feature : Feature.values()) {
      text("shop.relief." + feature.name());
    }
  }

  @Test
  void everyEnemyHasANameAndADescription() {
    for (EnemyKind kind : EnemyKind.values()) {
      text("enemy." + kind.name() + ".name");
      text("enemy." + kind.name() + ".info");
    }
  }
}
