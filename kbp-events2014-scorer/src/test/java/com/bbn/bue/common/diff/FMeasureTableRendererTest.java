package com.bbn.bue.common.diff;

import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class FMeasureTableRendererTest {
    @Test
    public void testFMeasureTableRenderer() {
        final FMeasureTableRenderer renderer = FMeasureTableRenderer.create();

        final Map<String, FMeasureCounts> data = ImmutableMap.of(
                "foo", FMeasureCounts.from(1, 2, 3),
                "bar", FMeasureCounts.from(4, 5, 6));

        final String expected=
                "Name                            TP       FP       FN        P        R       F1\n"+
                "===============================================================================\n"+
                "foo                            1.0      2.0      3.0    33.33    25.00    28.57\n"+
                "bar                            4.0      5.0      6.0    44.44    40.00    42.11\n";
        final String result = renderer.render(data);
        assertEquals(expected, result);
    }
}
