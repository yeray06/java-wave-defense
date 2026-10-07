package io.github.yeray06.wavedefense.game.panel;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import java.util.List;

/** The after-wave report (plan, "Informe"), from {@link ReportLines}. */
public final class ReportPanel extends Table {
  private static final float DETAIL_INDENT = 18;
  private static final float WIDTH = 470;

  private final Skin skin;

  public ReportPanel(Skin skin, Label emptyMessage) {
    this.skin = skin;
    top().left();
    add(emptyMessage).left();
  }

  public void show(List<ReportLines.Line> lines) {
    clearChildren();
    top().left();
    for (ReportLines.Line line : lines) {
      String style =
          switch (line.kind()) {
            case TITLE -> "title";
            case GOOD -> line.detail() ? "small" : "good";
            case WARNING -> "warning";
            case TEXT -> line.detail() ? "small" : "default";
          };
      Label label = new Label(line.text(), skin, style);
      label.setWrap(true);
      add(label)
          .left()
          .width(WIDTH - (line.detail() ? DETAIL_INDENT : 0))
          .padLeft(line.detail() ? DETAIL_INDENT : 0)
          .padTop(line.kind() == ReportLines.Kind.TITLE ? 0 : 3)
          .row();
    }
  }
}
