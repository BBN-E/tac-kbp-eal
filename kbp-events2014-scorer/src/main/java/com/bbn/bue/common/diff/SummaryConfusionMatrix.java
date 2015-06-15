package com.bbn.bue.common.diff;

import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.primitives.DoubleUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

import java.util.Iterator;
import java.util.Set;

import static com.bbn.bue.common.primitives.DoubleUtils.IsNonNegative;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.all;

/**
 * A confusion matrix which tracks only the number of entries in each cell.  Row and column labels
 * may not be null.
 *
 * @author rgabbard
 */
public abstract class SummaryConfusionMatrix {

  private SummaryConfusionMatrix() {
  }

  public abstract double cell(final Symbol row, final Symbol col);

  /**
   * The left-hand labels of the confusion matrix.
   */
  public abstract Set<Symbol> leftLabels();

  /**
   * The right hand labels of the confusion matrix.
   */
  public abstract Set<Symbol> rightLabels();

  // this should be made prettier
  public final String prettyPrint(Ordering<Symbol> labelOrdering) {
    final StringBuilder sb = new StringBuilder();

    for (final Symbol key1 : labelOrdering.sortedCopy(leftLabels())) {
      for (final Symbol key2 : labelOrdering.sortedCopy(rightLabels())) {
        sb.append(String.format("%s / %s: %6.2f\n", key1, key2, cell(key1, key2)));
      }
    }

    return sb.toString();
  }

  public final String prettyPrint() {
    return prettyPrint(SymbolUtils.byStringOrdering());
  }

  public static Builder builder() {
    return new Builder();
  }

  public final FMeasureCounts FMeasureVsAllOthers(final Symbol positiveSymbol) {
    return FMeasureVsAllOthers(ImmutableSet.of(positiveSymbol));
  }

  public final FMeasureCounts FMeasureVsAllOthers(final Set<Symbol> positiveSymbols) {
    double truePositives = 0;

    for (final Symbol goodSymbol : positiveSymbols) {
      for (final Symbol goodSymbol2 : positiveSymbols) {
        truePositives += cell(goodSymbol, goodSymbol2);
      }
    }

    double falsePositives = -truePositives;
    double falseNegatives = -truePositives;

    for (final Symbol goodSymbol : positiveSymbols) {
      falsePositives += rowSum(goodSymbol);
      falseNegatives += columnSum(goodSymbol);
    }

    return FMeasureCounts.from((float) truePositives, (float) falsePositives,
        (float) falseNegatives);
  }

  /**
   * Returns accuracy, which is defined as the sum of the cells of the form (X,X) over the sum of
   * all cells.  If the sum is 0, 0 is returned.  To pretty-print this you probably want to multiply
   * by 100.
   */
  public final double accuracy() {
    final double total = sumOfallCells();
    double matching = 0.0;
    for (final Symbol key : Sets.intersection(leftLabels(), rightLabels())) {
      matching += cell(key, key);
    }
    if (total != 0.0) {
      return matching / total;
    } else {
      return 0.0;
    }
  }

  public abstract double sumOfallCells();

  public abstract double rowSum(Symbol row);

  public abstract double columnSum(Symbol column);

  public abstract SummaryConfusionMatrix filteredCopy(CellFilter filter);

  public abstract SummaryConfusionMatrix copyWithTransformedLabels(Function<Symbol, Symbol> f);

  protected abstract void accumulateTo(Builder builder);

  public interface CellFilter {

    boolean keepCell(Symbol row, Symbol column);
  }

  public static class Builder {

    private final Table<Symbol, Symbol, Double> table = HashBasedTable.create();

    public Builder accumulate(final SummaryConfusionMatrix matrix) {
      matrix.accumulateTo(this);
      return this;
    }

    public Builder accumulate(final Symbol row, final Symbol col, final double val) {
      final Double cur = table.get(row, col);
      final double setVal;
      if (cur != null) {
        setVal = cur + val;
      } else {
        setVal = val;
      }
      table.put(row, col, setVal);
      return this;
    }

    /**
     * This is just an alias for accumulate. However, since the F-measure functions assume the
     * predictions are on the rows and the gold-standard on the columns, using this method in such
     * cases and make the code clearer and reduce errors.
     */
    public Builder accumulatePredictedGold(final Symbol row, final Symbol col, final double val) {
      accumulate(row, col, val);
      return this;
    }

    public SummaryConfusionMatrix build() {
      // first attemtp the more efficient implementation for the common binary case
      final Optional<BinarySummaryConfusionMatrix> binaryImp =
          BinarySummaryConfusionMatrix.attemptCreate(table);
      if (binaryImp.isPresent()) {
        return binaryImp.get();
      } else {
        return new TableBasedSummaryConfusionMatrix(table);
      }
    }

    public static final Function<Builder, SummaryConfusionMatrix> Build =
        new Function<SummaryConfusionMatrix.Builder, SummaryConfusionMatrix>() {
          @Override
          public SummaryConfusionMatrix apply(SummaryConfusionMatrix.Builder input) {
            return input.build();
          }
        };

    private Builder() {
    }
  }

  private static class TableBasedSummaryConfusionMatrix extends SummaryConfusionMatrix {

    private final Table<Symbol, Symbol, Double> table;

    public double cell(final Symbol row, final Symbol col) {
      final Double ret = table.get(row, col);
      if (ret != null) {
        return ret;
      } else {
        return 0.0;
      }
    }

    /**
     * The left-hand labels of the confusion matrix.
     */
    public Set<Symbol> leftLabels() {
      return table.rowKeySet();
    }

    /**
     * The right hand labels of the confusion matrix.
     */
    public Set<Symbol> rightLabels() {
      return table.columnKeySet();
    }


    private TableBasedSummaryConfusionMatrix(final Table<Symbol, Symbol, Double> table) {
      this.table = ImmutableTable.copyOf(table);
      checkArgument(all(table.values(), IsNonNegative));
    }

    public double sumOfallCells() {
      return DoubleUtils.sum(table.values());
    }

    public double rowSum(Symbol rowSymbol) {
      return DoubleUtils.sum(table.row(rowSymbol).values());
    }

    public double columnSum(Symbol columnSymbol) {
      return DoubleUtils.sum(table.column(columnSymbol).values());
    }

    @Override
    public SummaryConfusionMatrix filteredCopy(CellFilter filter) {
      final SummaryConfusionMatrix.Builder ret = SummaryConfusionMatrix.builder();
      for (final Table.Cell<Symbol, Symbol, Double> cell : table.cellSet()) {
        if (filter.keepCell(cell.getRowKey(), cell.getColumnKey())) {
          ret.accumulate(cell.getRowKey(), cell.getColumnKey(), cell.getValue());
        }
      }
      return ret.build();
    }

    @Override
    public SummaryConfusionMatrix copyWithTransformedLabels(Function<Symbol, Symbol> f) {
      final SummaryConfusionMatrix.Builder ret = SummaryConfusionMatrix.builder();
      for (final Table.Cell<Symbol, Symbol, Double> cell : table.cellSet()) {
        ret.accumulate(f.apply(cell.getRowKey()), f.apply(cell.getColumnKey()), cell.getValue());
      }
      return ret.build();
    }

    protected void accumulateTo(Builder builder) {
      for (final Table.Cell<Symbol, Symbol, Double> cell : table.cellSet()) {
        builder.accumulate(cell.getRowKey(), cell.getColumnKey(), cell.getValue());
      }
    }
  }

  /**
   * The special case where there are only two labels is very common, so we provide a much more
   * efficient implementation for it.  This makes a noticeable difference when e.g. doing bootstrap
   * sampling with many different score breakdowns.
   */
  private static class BinarySummaryConfusionMatrix extends SummaryConfusionMatrix {

    private final Symbol key0;
    private final Symbol key1;
    private final double[] data;

    private static final int NOT_PRESENT = -1;

    private BinarySummaryConfusionMatrix(Symbol key0, Symbol key1, double[] data) {
      checkArgument(key0 != key1);
      checkArgument(data.length == 4);
      this.key0 = checkNotNull(key0);
      this.key1 = checkNotNull(key1);
      // no defensive copy because we control where this comes from
      this.data = checkNotNull(data);
    }

    public static boolean canUseFor(Table<Symbol, Symbol, Double> table) {
      return table.rowKeySet().size() == 2 &&
          table.rowKeySet().equals(table.columnKeySet());
    }

    public static Optional<BinarySummaryConfusionMatrix> attemptCreate(
        Table<Symbol, Symbol, Double> table) {
      if (canUseFor(table)) {
        final Iterator<Symbol> keyIt = table.rowKeySet().iterator();
        final Symbol key0 = keyIt.next();
        final Symbol key1 = keyIt.next();
        return Optional.of(new BinarySummaryConfusionMatrix(key0, key1,
            new double[]{cell(table, key0, key0), cell(table, key0, key1),
                cell(table, key1, key0), cell(table, key1, key1)}));
      } else {
        return Optional.absent();
      }
    }

    private static double cell(Table<Symbol, Symbol, Double> table, Symbol row, Symbol col) {
      final Double val = table.get(row, col);
      if (val != null) {
        return val;
      } else {
        return 0.0;
      }
    }

    @Override
    public double cell(Symbol row, Symbol col) {
      int rowIdx = keyIndex(row);
      int colIdx = keyIndex(col);
      if (rowIdx == NOT_PRESENT || colIdx == NOT_PRESENT) {
        return 0.0;
      }
      return data[2 * rowIdx + colIdx];
    }

    protected void accumulateTo(Builder builder) {
      builder.accumulate(key0, key0, data[0]);
      builder.accumulate(key0, key1, data[1]);
      builder.accumulate(key1, key0, data[2]);
      builder.accumulate(key1, key1, data[3]);
    }

    private int keyIndex(Symbol sym) {
      if (sym == key0) {
        return 0;
      } else if (sym == key1) {
        return 1;
      } else {
        return NOT_PRESENT;
      }
    }

    @Override
    public Set<Symbol> leftLabels() {
      return ImmutableSet.of(key0, key1);
    }

    @Override
    public Set<Symbol> rightLabels() {
      return ImmutableSet.of(key0, key1);
    }

    @Override
    public double sumOfallCells() {
      return DoubleUtils.sum(data);
    }

    @Override
    public double rowSum(Symbol row) {
      int rowIdx = keyIndex(row);
      if (NOT_PRESENT == rowIdx) {
        return 0.0;
      }
      return data[2 * rowIdx] + data[2 * rowIdx + 1];
    }

    @Override
    public double columnSum(Symbol column) {
      int colIdx = keyIndex(column);
      if (NOT_PRESENT == colIdx) {
        return 0.0;
      }
      return data[colIdx] + data[colIdx + 2];
    }

    @Override
    public SummaryConfusionMatrix filteredCopy(CellFilter filter) {
      final SummaryConfusionMatrix.Builder builder = SummaryConfusionMatrix.builder();
      for (final Symbol left : leftLabels()) {
        for (final Symbol right : rightLabels()) {
          if (filter.keepCell(left, right)) {
            builder.accumulate(left, right, cell(left, right));
          }
        }
      }
      return builder.build();
    }

    @Override
    public SummaryConfusionMatrix copyWithTransformedLabels(Function<Symbol, Symbol> f) {
      final SummaryConfusionMatrix.Builder builder = SummaryConfusionMatrix.builder();
      for (final Symbol left : leftLabels()) {
        for (final Symbol right : rightLabels()) {
          builder.accumulate(f.apply(left), f.apply(right), cell(left, right));
        }
      }
      return builder.build();
    }

  }
}
