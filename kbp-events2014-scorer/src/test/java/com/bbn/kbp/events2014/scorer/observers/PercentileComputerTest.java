package com.bbn.kbp.events2014.scorer.observers;

import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.List;

public class PercentileComputerTest extends TestCase {
    // sample data from NIST's Handbook of Engineering Statistics
    // article on percentiles
    // http://www.itl.nist.gov/div898/handbook/prc/section2/prc252.htm
    private static final List<Double> nistData = ImmutableList.copyOf(
            new Double[] {
        95.1772, 95.1567, 95.1937, 95.1959, 95.1442,
        95.0610, 95.1591, 95.1195, 95.1065, 95.0925,
        95.1990, 95.1682 });
    private static final List<Double> wikipediaData = ImmutableList.copyOf(
            new Double[] {
                    15.0, 20.0, 35.0, 40.0, 50.0
            });

    @Test
    public void testNIST() {
        final PercentileComputer nistComputer = PercentileComputer.nistPercentileComputer();
        final PercentileComputer.Percentiles nistPercentiles = nistComputer.calculatePercentiles(nistData);
        assertEquals(95.1981, nistPercentiles.percentile(0.9), 0.0001);
        assertEquals(12, nistPercentiles.numObservedValues());
        assertEquals(95.1990, nistPercentiles.max(), 0.0001);
        assertEquals(95.0610, nistPercentiles.min(), 0.0001);
        assertEquals(95.1579, nistPercentiles.median(), 0.0001);

        final PercentileComputer.Percentiles wikiPercentiles = nistComputer.calculatePercentiles(wikipediaData);
        assertEquals(26.0, wikiPercentiles.percentile(0.4), 0.0001);
        assertEquals(35.0, wikiPercentiles.median(), 0.0001);
    }

    @Test
    public void testExcel() {
        final PercentileComputer excelComputer = PercentileComputer.excelPercentileComputer();
        final PercentileComputer.Percentiles wikiPercentiles = excelComputer.calculatePercentiles(wikipediaData);
        assertEquals(29, wikiPercentiles.percentile(0.4), 0.0001);
    }
}