package com.bbn.bue.common.diff;

import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.evaluation.FMeasureCounts;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Ordering;

import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public final class FMeasureTableRenderer {

  private int nameFieldLength = 25;
  private Ordering<Map.Entry<String, FMeasureCounts>> ordering =
      MapUtils.byKeyDescendingOrdering();

  public static FMeasureTableRenderer create() {
    return new FMeasureTableRenderer();
  }

  public FMeasureTableRenderer setNameFieldLength(int nameFieldLength) {
    checkArgument(nameFieldLength > 0);
    this.nameFieldLength = nameFieldLength;
    return this;
  }

  private static final int TP_FIELD_LENGTH = 8;
  private static final int FP_FIELD_LENGTH = 8;
  private static final int FN_FIELD_LENGTH = 8;
  private static final int P_FIELD_LENGTH = 8;
  private static final int R_FIELD_LENGTH = 8;
  private static final int F1_FIELD_LENGTH = 8;

  public String render(Map<String, FMeasureCounts> fMeasureCounts) {
    final StringBuilder sb = new StringBuilder();

    final String titleFormatString = String.format(
        "%%-%ds %%%ds %%%ds %%%ds %%%ds %%%ds %%%ds\n",
        nameFieldLength, TP_FIELD_LENGTH, FP_FIELD_LENGTH,
        FN_FIELD_LENGTH,
        P_FIELD_LENGTH, R_FIELD_LENGTH, F1_FIELD_LENGTH);

    //"Name", "TP", "FP", "FN", "P", "R", "F1");
    final String lineFormatString = String.format(
        "%%-%ds %%%d.1f %%%d.1f %%%d.1f %%%d.2f %%%d.2f %%%d.2f\n",
        nameFieldLength, TP_FIELD_LENGTH, FP_FIELD_LENGTH,
        FN_FIELD_LENGTH,
        P_FIELD_LENGTH, R_FIELD_LENGTH, F1_FIELD_LENGTH);

    final int lineLength = nameFieldLength + TP_FIELD_LENGTH + FP_FIELD_LENGTH
        + FN_FIELD_LENGTH + P_FIELD_LENGTH + R_FIELD_LENGTH + F1_FIELD_LENGTH
        // all but last followed by space
        + 6;

    sb.append(String.format(titleFormatString, "Name", "TP", "FP", "FN", "P", "R", "F1"));
    sb.append(Strings.repeat("=", lineLength)).append("\n");

    for (final Map.Entry<String, FMeasureCounts> countsEntry :
        ordering.sortedCopy(fMeasureCounts.entrySet())) {
      final String name = countsEntry.getKey();
      final FMeasureCounts counts = countsEntry.getValue();

      sb.append(String.format(lineFormatString, name, counts.truePositives(),
          counts.falsePositives(), counts.falseNegatives(), 100.0 * counts.precision(),
          100.0 * counts.recall(), 100.0 * counts.F1()));
    }

    return sb.toString();
  }

  private FMeasureTableRenderer() {
  }

  public FMeasureTableRenderer sortByErrorCountDescending() {
    this.ordering = BY_ERROR_COUNT_DESCENDING;
    return this;
  }

  public static final Function<FMeasureCounts, Float> ERROR_COUNT =
      new Function<FMeasureCounts, Float>() {
        @Override
        public Float apply(FMeasureCounts input) {
          return (float) (input.falsePositives() + input.falseNegatives());
        }
      };

  public static final Ordering<Map.Entry<String, FMeasureCounts>> BY_ERROR_COUNT_DESCENDING =
      MapUtils.byValueOrdering(Ordering.natural().onResultOf(ERROR_COUNT));
}
