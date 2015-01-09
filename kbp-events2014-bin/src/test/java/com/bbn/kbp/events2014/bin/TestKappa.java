package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.symbols.Symbol;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestKappa {
    private static final Symbol YES = Symbol.from("YES");
    private static final Symbol NO = Symbol.from("NO");

    private final SummaryConfusionMatrix matrix = SummaryConfusionMatrix.builder()
            .accumulate(YES, YES, 20.0)
            .accumulate(YES, NO, 5.0)
            .accumulate(NO, YES, 10.0)
            .accumulate(NO, NO, 15.0).build();

    private final SummaryConfusionMatrix matrix2 = SummaryConfusionMatrix.builder()
            .accumulate(YES, YES, 45.0)
            .accumulate(YES, NO, 6.0)
            .accumulate(NO, YES, 9.0)
            .accumulate(NO, NO, 40.0).build();

    @Test
    public void testKappa() {
        assertEquals(0.40, KappaCalculator.create().computeKappa(matrix), .001);
        assertEquals(0.70, KappaCalculator.create().computeKappa(matrix2), .001);
    }
}
