package com.bbn.bue.common.diff;

import com.bbn.bue.common.collections.CollectionUtils;
import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events2014.scorer.observers.breakdowns.BrokenDownProvenancedConfusionMatrix;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A confusion matrix which tracks the actual identities of all its elements.
 *
 * Row and column labels may not be null.  Cell fillers may not be null. Entries in each cell are
 * stored in the order they were added to the builder.
 *
 * @param <CellFiller> What sort of entry to keep in each cell.
 * @author rgabbard
 */
public final class ProvenancedConfusionMatrix<CellFiller> {

  private final Table<Symbol, Symbol, List<CellFiller>> table;

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

  /**
   * A list of all the entries occupying the cell {@code (left, right)} of this confusion matrix.
   */
  public List<CellFiller> cell(final Symbol left, final Symbol right) {
    final List<CellFiller> cell = table.get(left, right);
    if (cell != null) {
      return cell;
    } else {
      return ImmutableList.of();
    }
  }

  /**
   * Returns all provenance entries in this matrix, regardless of cell.
   */
  public Set<CellFiller> entries() {
    return FluentIterable.from(table.cellSet())
        .transformAndConcat(CollectionUtils.<List<CellFiller>>TableCellValue())
        .toSet();
  }

  /**
   * Return a new {@code ProvenancedConfusionMatrix} containing only those provenance entries
   * matching the provided predicate.
   */
  public ProvenancedConfusionMatrix<CellFiller> filteredCopy(Predicate<CellFiller> predicate) {
    final ImmutableTable.Builder<Symbol, Symbol, List<CellFiller>> newTable =
        ImmutableTable.builder();

    for (final Cell<Symbol, Symbol, List<CellFiller>> curCell : table.cellSet()) {
      final List<CellFiller> newFiller = FluentIterable.from(curCell.getValue())
          .filter(predicate).toList();
      if (!newFiller.isEmpty()) {
        newTable.put(curCell.getRowKey(), curCell.getColumnKey(), newFiller);
      }

    }

    return new ProvenancedConfusionMatrix<CellFiller>(newTable.build());
  }

  /**
   * Allows generating "breakdowns" of a provenanced confusion matrix according to some criteria.
   * For example, a confusion matrix for an event detection task could be further broken down into
   * separate confusion matrices for each event type.
   *
   * To do this, you specify a signature function mapping from each provenance to some signature
   * (e.g. to event types, to genres, etc.).  The output will be an {@link
   * com.google.common.collect.ImmutableMap} from all observed signatures to {@link
   * com.bbn.bue.common.diff.ProvenancedConfusionMatrix}es consisting of only those provenances with
   * the corresponding signature under the provided function.
   *
   * The signature function may never return a signature of {@code null}.
   *
   * {@code keyOrder} is the order the keys should be in the iteration order of the resulting map.
   */
  public <SignatureType> BrokenDownProvenancedConfusionMatrix<SignatureType, CellFiller>
  breakdown(Function<? super CellFiller, SignatureType> signatureFunction,
      Ordering<SignatureType> keyOrdering) {
    final Map<SignatureType, Builder<CellFiller>> ret = Maps.newHashMap();

    // a more efficient implementation should be used if the confusion matrix is
    // large and sparse, but this is unlikely. ~ rgabbard
    for (final Symbol leftLabel : leftLabels()) {
      for (final Symbol rightLabel : rightLabels()) {
        for (final CellFiller provenance : cell(leftLabel, rightLabel)) {
          final SignatureType signature = signatureFunction.apply(provenance);
          checkNotNull(signature, "Provenance function may never return null");
          if (!ret.containsKey(signature)) {
            ret.put(signature, ProvenancedConfusionMatrix.<CellFiller>builder());
          }
          ret.get(signature).record(leftLabel, rightLabel, provenance);
        }
      }
    }

    final ImmutableMap.Builder<SignatureType, ProvenancedConfusionMatrix<CellFiller>> trueRet =
        ImmutableMap.builder();
    // to get consistent output, we make sure to sort by the keys
    for (final Map.Entry<SignatureType, Builder<CellFiller>> entry :
        MapUtils.<SignatureType, Builder<CellFiller>>byKeyOrdering(keyOrdering).sortedCopy(
            ret.entrySet())) {
      trueRet.put(entry.getKey(), entry.getValue().build());
    }
    return BrokenDownProvenancedConfusionMatrix.fromMap(trueRet.build());
  }

  public SummaryConfusionMatrix buildSummaryMatrix() {
    final SummaryConfusionMatrix.Builder builder = SummaryConfusionMatrix.builder();

    for (final Cell<Symbol, Symbol, List<CellFiller>> cell : table.cellSet()) {
      builder.accumulate(cell.getRowKey(), cell.getColumnKey(), cell.getValue().size());
    }

    return builder.build();
  }

  private String prettyPrint(Ordering<Symbol> labelOrdering, Optional<Ordering<CellFiller>> fillerOrdering) {
    final StringBuilder sb = new StringBuilder();

    final List<Symbol> sortedColumns = labelOrdering.sortedCopy(table.columnKeySet());
    for (final Symbol rowLabel : labelOrdering.sortedCopy(table.rowKeySet())) {
      for (final Symbol colLabel : sortedColumns) {
        if (table.contains(rowLabel, colLabel)) {
          sb.append(String.format(" =============== %s / %s ==============\n", rowLabel, colLabel));
          final Iterable<CellFiller> orderedFillers;
          if (fillerOrdering.isPresent()) {
            orderedFillers = fillerOrdering.get().sortedCopy(table.get(rowLabel, colLabel));
          } else {
            orderedFillers = table.get(rowLabel, colLabel);
          }

          for (final CellFiller filler : orderedFillers) {
            sb.append("\n\t").append(filler.toString());
          }
          sb.append("\n");
        }
      }
    }

    return sb.toString();
  }

  public String prettyPrint() {
    return prettyPrint(SymbolUtils.byStringOrdering(), Optional.<Ordering<CellFiller>>absent());
  }


  /**
   * Generate an object which will let you create a confusion matrix.
   */
  public static <CellFiller> Builder<CellFiller> builder() {
    return new Builder<CellFiller>();
  }

  private ProvenancedConfusionMatrix(final Table<Symbol, Symbol, List<CellFiller>> table) {
    final ImmutableTable.Builder<Symbol, Symbol, List<CellFiller>> builder =
        ImmutableTable.builder();

    for (final Cell<Symbol, Symbol, List<CellFiller>> cell : table.cellSet()) {
      builder.put(cell.getRowKey(), cell.getColumnKey(), ImmutableList.copyOf(cell.getValue()));
    }
    this.table = builder.build();
  }

  public static <CellFiller> Function<ProvenancedConfusionMatrix<CellFiller>,
      SummaryConfusionMatrix> ToSummaryMatrix() {
    return new Function<ProvenancedConfusionMatrix<CellFiller>, SummaryConfusionMatrix>() {
      @Override
      public SummaryConfusionMatrix apply(ProvenancedConfusionMatrix<CellFiller> input) {
        return input.buildSummaryMatrix();
      }
    };
  }

  public static class Builder<CellFiller> {

    private Builder() {
    }

    /**
     * Add the specified {@code filler} to cell {@code (left, right)} of this confusion matrix being
     * built.
     */
    public void record(final Symbol left, final Symbol right, final CellFiller filler) {
      if (!tableBuilder.contains(left, right)) {
        tableBuilder.put(left, right, Lists.<CellFiller>newArrayList());
      }
      tableBuilder.get(left, right).add(filler);
    }

    /**
     * This is an alias for {@link #record(com.bbn.bue.common.symbols.Symbol,
     * com.bbn.bue.common.symbols.Symbol, Object)} you can use to make your code clearer, since the
     * predicted value is assumed to be on the rows for F-Measure calculations, etc.
     */
    public void recordPredictedGold(final Symbol left, final Symbol right,
        final CellFiller filler) {
      record(left, right, filler);
    }

    public ProvenancedConfusionMatrix<CellFiller> build() {
      return new ProvenancedConfusionMatrix<CellFiller>(tableBuilder);
    }

    private final Table<Symbol, Symbol, List<CellFiller>> tableBuilder = HashBasedTable.create();

    public void accumulate(ProvenancedConfusionMatrix<CellFiller> matrix) {
      for (final Table.Cell<Symbol, Symbol, List<CellFiller>> cell : matrix.table.cellSet()) {
        if (!tableBuilder.contains(cell.getRowKey(), cell.getColumnKey())) {
          tableBuilder.put(cell.getRowKey(), cell.getColumnKey(), Lists.<CellFiller>newArrayList());
        }
        tableBuilder.get(cell.getRowKey(), cell.getColumnKey()).addAll(cell.getValue());
      }
    }
  }


}
