package io.github.yeray06.wavedefense.game.editor;

import io.github.yeray06.wavedefense.script.api.ApiFunction;
import io.github.yeray06.wavedefense.script.api.Feature;
import io.github.yeray06.wavedefense.script.api.ScriptApi;
import io.github.yeray06.wavedefense.script.api.Unlocks;
import java.util.ArrayList;
import java.util.List;

/**
 * What the {@link Completer} offers a player: the calls and syntax they own, nothing they have not
 * bought yet. Calls with no parameters come as {@code name()}, the rest as {@code name(}.
 *
 * @param words what can be written anywhere
 * @param members what can be written after a dot: the enemy's calls and the list's
 */
public record Vocabulary(List<String> words, List<String> members) {

  public Vocabulary {
    words = List.copyOf(words);
    members = List.copyOf(members);
  }

  /** The words for these unlocks; {@code library} adds its methods. */
  public static Vocabulary of(ScriptApi api, Unlocks unlocks, String library) {
    List<String> words = new ArrayList<>();
    List<String> members = new ArrayList<>();
    for (ApiFunction call : ApiFunction.values()) {
      if (!unlocks.satisfies(api.requirement(call))) {
        continue;
      }
      String word = api.name(call) + (call.parameters().isEmpty() ? "()" : "(");
      if (call.receiver() == ApiFunction.Receiver.TURRET) {
        words.add(word);
      } else {
        members.add(word);
      }
    }
    if (unlocks.satisfies(api.requirement(ApiFunction.USE_AMMO))) {
      words.addAll(api.ammoConstantNames());
    }
    if (unlocks.has(Feature.IF_ELSE)) {
      words.addAll(List.of("if", "else", "true", "false", "null"));
    }
    if (unlocks.has(Feature.VARIABLES)) {
      words.addAll(List.of("int", "boolean", api.enemyTypeName()));
    }
    if (unlocks.has(Feature.WHILE)) {
      words.add("while");
    }
    if (unlocks.has(Feature.FOR)) {
      words.addAll(List.of("for", "List<" + api.enemyTypeName() + ">"));
      members.addAll(List.of("size()", "get(", "isEmpty()"));
    }
    if (unlocks.has(Feature.METHODS)) {
      words.addAll(List.of("void", "return"));
      for (String name : Completer.declared(library)) {
        if (name.endsWith("(")) {
          words.add(name);
        }
      }
    }
    return new Vocabulary(words, members);
  }
}
