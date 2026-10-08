package io.github.yeray06.wavedefense.sim.data;

import io.github.yeray06.wavedefense.script.api.ApiFunction;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.diag.FaultType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code data/manual.json}: the in-game manual's chapters and pages, what each page is about, what
 * it needs before the player has it, and its code examples. The texts live in {@code
 * i18n/messages.properties} under {@code manual.*}; code examples live here because they are full
 * of braces, and {@link GameData} compiles every one of them at load.
 */
public record ManualConfig(List<String> notFromPlan, List<Chapter> chapters) {

  public record Chapter(String id, List<Page> pages) {
    public Chapter {
      Check.present("chapter id", id);
      pages = List.copyOf(Check.notEmpty("pages of " + id, pages));
    }
  }

  /**
   * @param about the call, syntax piece, enemy or exception the page explains; {@link Topic#NONE}
   *     for a general page. It also locks the page until the player has it.
   * @param needs anything else the page uses (a recipe with {@code while} and {@code
   *     usarMunicion()})
   */
  public record Page(String id, Topic about, List<Topic> needs, List<Example> examples) {
    public Page {
      Check.present("page id", id);
      about = about == null ? Topic.NONE : about;
      needs = needs == null ? List.of() : List.copyOf(needs);
      examples = examples == null ? List.of() : List.copyOf(examples);
      for (Topic need : needs) {
        if (need.fault() != null || need.equals(Topic.NONE)) {
          throw new IllegalArgumentException(id + ": needs lists calls, pieces or enemies only");
        }
      }
    }

    /** Everything the page asks of the player: its subject and its needs. */
    public List<Topic> topics() {
      List<Topic> topics = new ArrayList<>(needs);
      topics.add(about);
      return topics;
    }
  }

  /** At most one of the fields; none at all is {@link #NONE}. */
  public record Topic(ApiFunction call, Feature feature, EnemyKind enemy, FaultType fault) {
    public static final Topic NONE = new Topic(null, null, null, null);

    public Topic {
      int set =
          (call != null ? 1 : 0)
              + (feature != null ? 1 : 0)
              + (enemy != null ? 1 : 0)
              + (fault != null ? 1 : 0);
      if (set > 1) {
        throw new IllegalArgumentException("a topic is one call, piece, enemy or exception");
      }
    }
  }

  /**
   * @param code a script that compiles with what its page needs plus {@code needs}, calling the
   *     methods of the page's library examples
   * @param needs what this example uses beyond its page
   * @param library whether the code goes in the library rather than in a turret; absent is false
   */
  public record Example(String code, List<Topic> needs, Boolean library) {
    public Example {
      Check.present("example code", code);
      needs = needs == null ? List.of() : List.copyOf(needs);
      library = library != null && library;
    }

    public boolean inLibrary() {
      return library;
    }
  }

  public ManualConfig {
    notFromPlan = List.copyOf(Check.present("notFromPlan", notFromPlan));
    chapters = List.copyOf(Check.notEmpty("chapters", chapters));
    Set<String> ids = new HashSet<>();
    for (Chapter chapter : chapters) {
      if (!ids.add(chapter.id())) {
        throw new IllegalArgumentException("id used twice: " + chapter.id());
      }
      for (Page page : chapter.pages()) {
        if (!ids.add(page.id())) {
          throw new IllegalArgumentException("id used twice: " + page.id());
        }
      }
    }
  }

  /** Every page, in reading order. */
  public List<Page> pages() {
    return chapters.stream().flatMap(c -> c.pages().stream()).toList();
  }
}
