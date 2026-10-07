package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Clipboard;
import io.github.yeray06.wavedefense.game.editor.CodeView;
import io.github.yeray06.wavedefense.game.i18n.ManualFacts;
import io.github.yeray06.wavedefense.game.i18n.ScriptTexts;
import io.github.yeray06.wavedefense.game.i18n.Texts;
import io.github.yeray06.wavedefense.game.ui.UiSkin;
import io.github.yeray06.wavedefense.script.CompileResult;
import io.github.yeray06.wavedefense.script.api.Requirement;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import io.github.yeray06.wavedefense.sim.data.GameData;
import io.github.yeray06.wavedefense.sim.data.ManualConfig;
import io.github.yeray06.wavedefense.sim.progress.Progress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The player's manual (phase 5b): every call, syntax piece, exception and enemy, with examples to
 * copy into the editor. Pages the player does not have yet are shown with a lock and what brings
 * them, like the shop and the map. Structure and code from {@code data/manual.json}, texts from
 * {@code manual.*}, numbers from the data ({@link ManualFacts}).
 */
public final class ManualPanel extends Table {
  private static final float WIDTH = 470;
  private static final float LOCK_SIZE = 16;
  private static final float COPIED_SECONDS = 1.5f;

  private final GameData data;
  private final Progress progress;
  private final ScriptTexts names;
  private final Texts texts;
  private final Skin skin;
  private final ManualFacts facts;
  private final List<ManualConfig.Page> pages;
  private final TextureRegion white;
  private final Clipboard clipboard;

  /** The page shown, or -1 for the index. */
  private int current = -1;

  private Unlocks shownUnlocks;

  /**
   * @param clipboard where «Copiar» puts an example, for the editor's Ctrl+V
   */
  public ManualPanel(
      GameData data, Progress progress, ScriptTexts names, Skin skin, Clipboard clipboard) {
    this.data = data;
    this.progress = progress;
    this.names = names;
    this.texts = names.texts();
    this.skin = skin;
    this.facts = new ManualFacts(data);
    this.pages = data.manual().pages();
    this.white = new TextureRegion(skin.get("white", Texture.class));
    this.clipboard = clipboard;
    showIndex();
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    if (!progress.unlocks().equals(shownUnlocks)) {
      refresh();
    }
  }

  /** Redraws the open view: locks change with purchases and waves. */
  public void refresh() {
    if (current < 0) {
      showIndex();
    } else {
      showPage(current);
    }
  }

  /** The page shown, or {@code null} on the index. */
  public ManualConfig.Page currentPage() {
    return current < 0 ? null : pages.get(current);
  }

  public void showIndex() {
    current = -1;
    shownUnlocks = progress.unlocks();
    clearChildren();
    top().left();
    add(wrapped(texts.get("manual.intro"), "small")).left().width(WIDTH).padBottom(6).row();
    for (ManualConfig.Chapter chapter : data.manual().chapters()) {
      add(new Label(texts.get("manual.chapter." + chapter.id()), skin, "title"))
          .left()
          .padTop(10)
          .row();
      for (ManualConfig.Page page : chapter.pages()) {
        add(indexEntry(page)).left().width(WIDTH).padTop(3).row();
      }
    }
    scrollToTop();
  }

  /** Opens a page by its id in {@code manual.json}. */
  public void showPage(String id) {
    for (int i = 0; i < pages.size(); i++) {
      if (pages.get(i).id().equals(id)) {
        showPage(i);
        return;
      }
    }
    throw new IllegalArgumentException("No manual page " + id);
  }

  private void showPage(int index) {
    current = index;
    shownUnlocks = progress.unlocks();
    ManualConfig.Page page = pages.get(index);
    clearChildren();
    top().left();

    Table nav = new Table();
    nav.add(button(texts.get("manual.index"), this::showIndex)).padRight(6);
    TextButton previous = button(texts.get("manual.prev"), () -> showPage(current - 1));
    previous.setDisabled(index == 0);
    nav.add(previous).padRight(6);
    TextButton next = button(texts.get("manual.next"), () -> showPage(current + 1));
    next.setDisabled(index == pages.size() - 1);
    nav.add(next);
    add(nav).left().padBottom(8).row();

    add(wrapped(title(page), "title")).left().width(WIDTH).row();
    for (String reason : lockReasons(page)) {
      add(wrapped(reason, "warning")).left().width(WIDTH).padTop(2).row();
    }
    add(wrapped(text(page, "body"), "body")).left().width(WIDTH).padTop(6).row();
    for (int i = 0; i < page.examples().size(); i++) {
      add(example(page, i)).left().width(WIDTH).padTop(10).row();
    }
    scrollToTop();
  }

  /** Why the player does not have a page yet; empty when they do. */
  public List<String> lockReasons(ManualConfig.Page page) {
    Set<String> reasons = new LinkedHashSet<>();
    Unlocks owned = progress.unlocks();
    for (ManualConfig.Topic topic : page.topics()) {
      for (Requirement requirement : data.requirements(topic)) {
        if (owned.satisfies(requirement)) {
          continue;
        }
        switch (requirement) {
          case Requirement.NeedsFeature needs ->
              reasons.add(texts.format("manual.locked.feature", names.feature(needs.feature())));
          case Requirement.NeedsWave needs ->
              reasons.add(
                  texts.format(
                      topic.enemy() != null ? "manual.locked.enemy" : "manual.locked.wave",
                      needs.wave()));
          case Requirement.Free free -> {}
        }
      }
    }
    return new ArrayList<>(reasons);
  }

  /** A page's title as the index and the page show it. */
  public String title(ManualConfig.Page page) {
    return text(page, "title");
  }

  private String text(ManualConfig.Page page, String part) {
    return facts.fill(texts.get("manual.page." + page.id() + "." + part));
  }

  private Actor indexEntry(ManualConfig.Page page) {
    TextButton entry = button(title(page), () -> showPage(page.id()));
    entry.getLabel().setAlignment(Align.left);
    entry.getLabelCell().expandX().fillX();
    if (!lockReasons(page).isEmpty()) {
      Image lock = new Image(skin.getDrawable("lock"));
      lock.setColor(UiSkin.MUTED);
      entry.add(lock).size(LOCK_SIZE).padLeft(6);
    }
    return entry;
  }

  private Table example(ManualConfig.Page page, int index) {
    ManualConfig.Example example = page.examples().get(index);
    Table box = new Table();
    box.left();
    box.add(wrapped(text(page, "example." + (index + 1)), "small"))
        .left()
        .width(WIDTH)
        .colspan(2)
        .row();
    box.add(new CodeView(example.code(), skin.getFont("small"), white))
        .left()
        .colspan(2)
        .padTop(4)
        .row();
    CompileResult compiled = compile(page, example);
    box.add(
            wrapped(
                texts.format(
                    "manual.example.memory", compiled.memoryUsed(), compiled.memoryLimit()),
                "small"))
        .left()
        .expandX()
        .fillX()
        .padTop(4);
    TextButton copy = button(texts.get("manual.copy"), () -> {});
    copy.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            clipboard.setContents(example.code());
            copy.setText(texts.get("manual.copied"));
            copy.clearActions();
            copy.addAction(
                Actions.sequence(
                    Actions.delay(COPIED_SECONDS),
                    Actions.run(() -> copy.setText(texts.get("manual.copy")))));
          }
        });
    box.add(copy).right().padTop(4).padLeft(8);
    return box;
  }

  /** The example compiled as the page's owner would: for its memory count. */
  private CompileResult compile(ManualConfig.Page page, ManualConfig.Example example) {
    List<ManualConfig.Topic> topics = new ArrayList<>(page.topics());
    topics.addAll(example.needs());
    return data.compiler()
        .compile(example.code(), data.unlocksFor(topics), data.turret().memoryLines());
  }

  private Label wrapped(String text, String style) {
    Label label = new Label(text, skin, style);
    label.setWrap(true);
    return label;
  }

  private TextButton button(String text, Runnable action) {
    TextButton button = new TextButton(text, skin);
    button.pad(4, 10, 4, 10);
    button.setProgrammaticChangeEvents(false);
    button.addListener(
        new ChangeListener() {
          @Override
          public void changed(ChangeEvent event, Actor actor) {
            button.setChecked(false);
            if (!button.isDisabled()) {
              action.run();
            }
          }
        });
    return button;
  }

  private void scrollToTop() {
    if (getParent() instanceof ScrollPane scroll) {
      scroll.layout();
      scroll.setScrollY(0);
      scroll.updateVisualScroll();
    }
  }
}
