package com.bbn.bue.common.diff;

import com.bbn.bue.common.collections.CollectionUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import com.google.common.collect.Table.Cell;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A confusion matrix which tracks the actual identities of all its elements.
 *
 * Row and column labels may not be null.  Cell fillers may not be null.
 * Entries in each cell are stored in the order they were added to the builder.
 * @author rgabbard
 *
 * @param <CellFiller> What sort of entry to keep in each cell.
 */
public final class ProvenancedConfusionMatrix<CellFiller> {
	private final Table<Symbol,Symbol,List<CellFiller>> table;

	/**
	 * The left-hand labels of the confusion matrix.
	 * @return
	 */
	public Set<Symbol> leftLabels() {
		return table.rowKeySet();
	}

	/**
	 * The right hand labels of the confusion matrix.
	 * @return
	 */
	public Set<Symbol> rightLabels() {
		return table.columnKeySet();
	}

	/**
	 * A list of all the entries occupying the cell {@code (left, right)} of this
	 * confusion matrix.
	 *
	 * @param left
	 * @param right
	 * @return
	 */
	public List<CellFiller> cell(final Symbol left, final Symbol right) {
		final List<CellFiller> cell = table.get(left, right);
		if (cell != null) {
			return cell;
		} else {
			if (leftLabels().contains(left) && rightLabels().contains(right)) {
				return ImmutableList.of();
			} else {
				throw new NoSuchElementException(String.format(
					"Invalid label pair (%s, %s). Valid left labels are %s. Valid right labels are %s",
					left, right, leftLabels(), rightLabels()));
			}
		}
	}

    /**
     * Returns all provenance entries in this matrix, regardless of cell.
     * @return
     */
    public Set<CellFiller> entries() {
        return FluentIterable.from(table.cellSet())
                .transformAndConcat(CollectionUtils.<List<CellFiller>>TableCellValue())
                .toSet();
    }

    /**
     * Return a new {@code ProvenancedConfusionMatrix} containing only those provenance
     * entries matching the provided predicate.
     * @param predicate
     * @return
     */
    public ProvenancedConfusionMatrix<CellFiller> filteredCopy(Predicate<CellFiller> predicate) {
        final ImmutableTable.Builder<Symbol, Symbol, List<CellFiller>> newTable = ImmutableTable.builder();

        for (final Cell<Symbol, Symbol, List<CellFiller>> curCell : table.cellSet()) {
            final List<CellFiller> newFiller = FluentIterable.from(curCell.getValue())
                    .filter(predicate).toList();
            if (!newFiller.isEmpty()) {
                newTable.put(curCell.getRowKey(), curCell.getColumnKey(), newFiller);
            }

        }

        return new ProvenancedConfusionMatrix<CellFiller>(newTable.build());
    }

	public SummaryConfusionMatrix buildSummaryMatrix() {
		final SummaryConfusionMatrix.Builder builder = SummaryConfusionMatrix.builder();

		for (final Cell<Symbol, Symbol, List<CellFiller>> cell : table.cellSet()) {
			builder.accumulate(cell.getRowKey(), cell.getColumnKey(), cell.getValue().size());
		}

		return builder.build();
	}

	public String prettyPrint() {
		final StringBuilder sb = new StringBuilder();

		for (final Table.Cell<Symbol, Symbol, List<CellFiller>> cell : table.cellSet()) {
			sb.append(String.format(" =============== %s / %s ==============\n", cell.getRowKey(), cell.getColumnKey()));
			for (final CellFiller filler : cell.getValue()) {
				sb.append("\n\t").append(filler.toString());
			}
			sb.append("\n");
		}

		return sb.toString();
	}


	/**
	 * Generate an object which will let you create a confusion matrix.
	 * @return
	 */
	public static <CellFiller> Builder<CellFiller> builder() {
		return new Builder<CellFiller>();
	}

	private ProvenancedConfusionMatrix(final Table<Symbol, Symbol, List<CellFiller>> table) {
		this.table = ImmutableTable.copyOf(table);
	}

	public static class Builder<CellFiller> {
		private Builder() {}

		/**
		 * Add the specified {@code filler} to cell {@code (left, right)} of this
		 * confusion matrix being built.
		 * @param left
		 * @param right
		 * @param filler
		 */
		public void record(final Symbol left, final Symbol right, final CellFiller filler) {
			if (!tableBuilder.contains(left, right)) {
				tableBuilder.put(left, right, Lists.<CellFiller>newArrayList());
			}
			tableBuilder.get(left, right).add(filler);
		}

		public ProvenancedConfusionMatrix<CellFiller> build() {
			return new ProvenancedConfusionMatrix<CellFiller>(tableBuilder);
		}

		private final Table<Symbol,Symbol,List<CellFiller>> tableBuilder = HashBasedTable.create();
	}


}
