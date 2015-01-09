package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.symbols.Symbol;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Calculates Cohen's kappa coefficient from a confusion matrix
 */
@MoveToBUECommon
public final class KappaCalculator {
    private KappaCalculator() {
    }

    public static KappaCalculator create() {
        return new KappaCalculator();
    }

    /**
     * Computes Cohen's kappa coefficient on the provided confusion matrix. If it is undefined,
     * returns {@code NaN}.
     */
    public double computeKappa(SummaryConfusionMatrix confusionMatrix) {
        final ImmutableSet<Symbol> labels =
                Sets.union(confusionMatrix.leftLabels(), confusionMatrix.rightLabels()).immutableCopy();

        final double n = confusionMatrix.sumOfallCells();
        if (n<=0.0) {
            return Double.NaN;
        }
        double proportionalAgreement = 0.0;
        double expectedAgreementByChance = 0.0;

        for (final Symbol label : labels) {
            proportionalAgreement += confusionMatrix.cell(label, label);
            expectedAgreementByChance += confusionMatrix.rowSum(label) * confusionMatrix.columnSum(label);
        }
        proportionalAgreement /= n;
        expectedAgreementByChance /= n * n;

        if (expectedAgreementByChance == 1.0) {
            return Double.NaN;
        } else {
            return (proportionalAgreement - expectedAgreementByChance)/ (1.0 - expectedAgreementByChance);
        }
    }
}
