package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.HardwareKind;
import io.github.yeray06.wavedefense.sim.data.ShopConfig;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import io.github.yeray06.wavedefense.sim.progress.PurchaseCheck;
import io.github.yeray06.wavedefense.sim.progress.Shop;
import java.util.function.Consumer;

/**
 * The shop (plan, "Árbol de sintaxis" and "Reglas de la tienda"): the whole syntax tree with its
 * prices from the start, each piece saying what it takes off the player's hands; the hardware with
 * its level and the syntax caps (D-34). Buying a piece opens its card with a working example.
 */
public final class ShopPanel extends Table {
  private static final float WIDTH = 470;
  private static final float TEXT_WIDTH = WIDTH - 20;

  private final GameData data;
  private final Shop shop;
  private final Progress progress;
  private final ScriptTexts names;
  private final Texts texts;
  private final Skin skin;
  private final Consumer<Object> onPurchase;
  private long shownWallet = -1;

  /**
   * @param onPurchase called after a purchase or a refund (D-59) with the {@link Feature} or {@link
   *     HardwareKind}
   */
  public ShopPanel(
      GameData data, Progress progress, ScriptTexts names, Skin skin, Consumer<Object> onPurchase) {
    this.data = data;
    this.shop = new Shop(data);
    this.progress = progress;
    this.names = names;
    this.texts = names.texts();
    this.skin = skin;
    this.onPurchase = onPurchase;
    top().left();
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    if (wallet() != shownWallet) {
      rebuild();
    }
  }

  /** Rebuilds every row: prices, levels and what can be bought now. */
  public void rebuild() {
    // Rebuilt on every purchase and every star earned: keep the player where they were reading.
    ScrollPane scroll = getParent() instanceof ScrollPane pane ? pane : null;
    float scrolledTo = scroll == null ? 0 : scroll.getScrollY();
    shownWallet = wallet();
    clearChildren();
    top().left();
    add(new Label(
            texts.format(
                "shop.wallet", names.stars(progress.stars()), names.trophies(progress.trophies())),
            skin,
            "good"))
        .left()
        .padBottom(8)
        .row();
    add(new Label(texts.get("shop.syntax"), skin, "title")).left().row();
    for (ShopConfig.SyntaxItem item : data.shop().syntax()) {
      Feature feature = item.feature();
      PurchaseCheck check = shop.check(progress, feature);
      String button =
          progress.owns(feature)
              ? texts.get("shop.owned")
              : texts.format("shop.buy", names.stars(shop.price(feature)));
      row(
          names.feature(feature),
          texts.get("shop.relief." + feature.name()),
          button,
          check,
          names.check(check, shop, feature),
          () -> {
            if (shop.buy(progress, feature) == PurchaseCheck.OK) {
              onPurchase.accept(feature);
              showCard(feature);
            }
          },
          null,
          null);
    }
    add(new Label(texts.get("shop.hardwareTitle"), skin, "title")).left().padTop(10).row();
    for (ShopConfig.HardwareItem item : data.shop().hardware()) {
      if (!item.inMvp()) {
        continue;
      }
      HardwareKind kind = item.kind();
      PurchaseCheck check = shop.check(progress, kind);
      String title =
          kind == HardwareKind.EXTRA_TURRET
              ? texts.format("shop.turrets", progress.turretCount())
              : texts.format(
                  "shop.level", texts.get("shop.item." + kind.name()), progress.level(kind));
      row(
          title,
          texts.format("shop.item." + kind.name() + ".effect", effect(item)),
          check == PurchaseCheck.MAX_LEVEL
              ? texts.get("shop.maxed")
              : texts.format("shop.buy", names.stars(shop.price(progress, kind))),
          check,
          names.check(check, shop, progress, kind),
          () -> {
            if (shop.buy(progress, kind) == PurchaseCheck.OK) {
              onPurchase.accept(kind);
            }
          },
          texts.format("shop.refund", shop.refundValue(progress, kind)),
          shop.canRefund(progress, kind)
              ? () -> {
                if (shop.refund(progress, kind)) {
                  onPurchase.accept(kind);
                }
              }
              : null);
    }
    add(new Label(texts.get("shop.trophies.title"), skin, "title")).left().padTop(10).row();
    Label trophies = new Label(texts.get("shop.trophies.text"), skin, "small");
    trophies.setWrap(true);
    add(trophies).left().width(WIDTH).row();
    if (scroll != null) {
      scroll.layout();
      scroll.setScrollY(scrolledTo);
      scroll.updateVisualScroll();
    }
  }

  /** Stars and trophies in one number, to notice when either changes. */
  private long wallet() {
    return (long) progress.stars() << 32 | progress.trophies();
  }

  private void row(
      String title,
      String detail,
      String buttonText,
      PurchaseCheck check,
      String reason,
      Runnable buy,
      String refundText,
      Runnable refund) {
    Table row = new Table();
    row.setBackground(skin.getDrawable("row"));
    row.pad(6, 8, 6, 8);
    Table text = new Table();
    text.left();
    text.add(new Label(title, skin)).left().row();
    Label detailLabel = new Label(detail, skin, "small");
    detailLabel.setWrap(true);
    text.add(detailLabel).left().width(TEXT_WIDTH).row();
    if (check != PurchaseCheck.OK && check != PurchaseCheck.ALREADY_OWNED) {
      Label reasonLabel = new Label(reason, skin, "warning");
      reasonLabel.setWrap(true);
      text.add(reasonLabel).left().width(TEXT_WIDTH);
    }
    // The button goes under the text: a price in stars ("Comprar: 2 estrellas") and a long piece
    // name do not fit side by side.
    row.add(text).left().expandX().fillX().row();
    TextButton button = new TextButton(buttonText, skin);
    button.pad(4, 10, 4, 10);
    button.setDisabled(check != PurchaseCheck.OK);
    button.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            if (!button.isDisabled()) {
              buy.run();
              rebuild();
            }
          }
        });
    Table buttons = new Table();
    if (refund != null) {
      TextButton back = new TextButton(refundText, skin);
      back.pad(4, 10, 4, 10);
      back.addListener(
          new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
              refund.run();
              rebuild();
            }
          });
      buttons.add(back).padRight(8);
    }
    buttons.add(button);
    row.add(buttons).right().padTop(4);
    add(row).width(WIDTH).padTop(4).row();
  }

  /** The number in a hardware item's effect text, as the player reads it. */
  private Object effect(ShopConfig.HardwareItem item) {
    double value = item.effectPerLevel();
    if (item.kind() == HardwareKind.DAMAGE) {
      value *= 100;
    }
    return value == Math.rint(value) ? (long) value : value;
  }

  /** Plan: "Cada compra abre una tarjeta con un ejemplo de cinco líneas". */
  private void showCard(Feature feature) {
    Stage stage = getStage();
    if (stage == null) {
      return;
    }
    Dialog card = new Dialog(texts.format("shop.card.title", names.feature(feature)), skin);
    card.pad(40, 20, 16, 20);
    Table content = card.getContentTable();
    Label relief = new Label(texts.get("shop.relief." + feature.name()), skin);
    relief.setWrap(true);
    content.add(relief).width(420).left().row();
    content.add(new Label(texts.get("shop.card.example"), skin, "small")).left().padTop(10).row();
    Table code = new Table();
    code.setBackground(skin.getDrawable("row"));
    code.pad(8);
    code.add(new Label(data.shop().syntax(feature).example(), skin, "code")).left();
    content.add(code).left().fillX().padTop(4);
    TextButton ok = new TextButton(texts.get("shop.card.close"), skin);
    ok.pad(6, 16, 6, 16);
    card.button(ok);
    card.show(stage);
  }
}
