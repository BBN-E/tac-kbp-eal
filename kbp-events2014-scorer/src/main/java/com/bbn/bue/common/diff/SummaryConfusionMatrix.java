package com.bbn.bue.common.diff;

import java.util.Set;

import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.primitives.DoubleUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Function;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

import static com.bbn.bue.common.primitives.DoubleUtils.IsNonNegative;

import static com.google.common.base.Preconditions.checkArgument;

import static com.google.common.collect.Iterables.all;

/**
 * A confusion matrix which tracks only the number of entries in
 * each cell.  Row and column labels may not be null.
 * @author rgabbard
 *
 */
public final class SummaryConfusionMatrix {
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

	// this should be made prettier
	public String prettyPrint () {
		final StringBuilder sb = new StringBuilder();

		for (final Table.Cell<Symbol, Symbol, Double> cell : table.cellSet()) {
			sb.append(String.format("%s / %s: %6.2f\n", cell.getRowKey(), cell.getColumnKey(), cell.getValue()));
		}

		return sb.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public FMeasureCounts FMeasureVsAllOthers(final Symbol positiveSymbol) {
		final double truePositives = cell(positiveSymbol, positiveSymbol);
		final double falsePositives = DoubleUtils.sum(table.row(positiveSymbol).values())
				- truePositives;
		final double falseNegatives = DoubleUtils.sum(table.column(positiveSymbol).values()) - truePositives;

		return FMeasureCounts.from((float)truePositives, (float)falsePositives, (float)falseNegatives);
	}

	private SummaryConfusionMatrix(final Table<Symbol, Symbol, Double> table) {
		this.table = ImmutableTable.copyOf(table);
		checkArgument(all(table.values(), IsNonNegative));
	}

    /**
     * Returns accuracy, which is defined as the sum of the cells
     * of the form (X,X) over the sum of all cells.  If the sum is 0,
     * 0 is returned.  To pretty-print this you probably want to
     * multiply by 100.
     *
     * @return
     */
    public double accuracy() {
        final double total = DoubleUtils.sum(table.values());
        double matching = 0.0;
        for (final Symbol key : Sets.intersection(leftLabels(), rightLabels())) {
            if (table.contains(key, key)) {
                matching += table.get(key, key);
            }
        }
        if (total != 0.0) {
            return matching/total;
        } else {
            return 0.0;
        }
    }

    public static class Builder {
		private final Table<Symbol, Symbol, Double> table = HashBasedTable.create();

		public void accumulate(final SummaryConfusionMatrix matrix) {
			for (final Table.Cell<Symbol, Symbol, Double> cell : matrix.table.cellSet() ) {
				accumulate(cell.getRowKey(), cell.getColumnKey(), cell.getValue());
			}
		}

		public void accumulate(final Symbol row, final Symbol col, final double val) {
			final Double cur = table.get(row, col);
			final double setVal;
			if (cur != null) {
				setVal = cur + val;
			} else {
				setVal = val;
			}
			table.put(row, col, setVal);

		}

		public SummaryConfusionMatrix build() {
			return new SummaryConfusionMatrix(table);
		}

        public static final Function<Builder, SummaryConfusionMatrix> Build = new Function<SummaryConfusionMatrix.Builder, SummaryConfusionMatrix>() {
            @Override
            public SummaryConfusionMatrix apply(SummaryConfusionMatrix.Builder input) {
                return input.build();
            }
        };

		private Builder() { }
	}
}
