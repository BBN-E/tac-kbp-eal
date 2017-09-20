package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.collections.CollectionUtils;
import com.bbn.bue.gnuplot.Axis;
import com.bbn.bue.gnuplot.BarChart;

import com.google.common.collect.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Create a histogram of how many times each response was found by a system.
 */
final class SystemCountHistogramBuilder {

  private static final Logger log = LoggerFactory.getLogger(SystemCountHistogramBuilder.class);

  public BarChart createSystemCountHistogram(Map<String, ImmutableSet<String>> systemToEquivIDs,
      int prefixLength, Set<String> humanSystems) {

    final SetMultimap<String, String> responseIDToSystemFindingIt =
        mapResponsesToWhichSystemsFoundThem(systemToEquivIDs, prefixLength, humanSystems);

    final ImmutableMultiset<Integer> countHistogram = ImmutableMultiset.copyOf(
        FluentIterable.from(responseIDToSystemFindingIt.asMap().values())
            .transform(CollectionUtils.<String>asSetFunction())
            .transform(CollectionUtils.sizeFunction()));

    final BarChart.Builder chart = BarChart.builder()
        .setTitle("Responses by how many systems found them");

    // which response was found the largest number of times?
    final int maxCount = Collections.max(countHistogram);
    // maxCountCount will be the size of the largest bar
    int maxCountCount = 0;
    for (int i = 1; i <= maxCount; ++i) {
      final int countCount = countHistogram.count(i);
      maxCountCount = Math.max(maxCountCount, countCount);
      chart.addBar(BarChart.Bar.builder(countCount).setLabel(Integer.toString(i)).build());
    }

    chart.setYAxis(Axis.yAxis().setRange(Range.closed(0.0, (double) maxCountCount + 10.0)).build());

    return chart.build();
  }

  private SetMultimap<String, String> mapResponsesToWhichSystemsFoundThem(
      Map<String, ImmutableSet<String>> systemToEquivIDs, int prefixLength,
      Set<String> humanSystems) {
    final SetMultimap<String, String> responseIDToSystemFindingIt = HashMultimap.create();
    for (final Map.Entry<String, ImmutableSet<String>> systemOutput : systemToEquivIDs.entrySet()) {
      final String systemName = systemOutput.getKey();
      final ImmutableSet<String> systemCorrectResponses = systemOutput.getValue();
      if (!humanSystems.contains(systemName)) {
        final String prefix = StringUtils.safeSubstring(systemName, 0, prefixLength);
        for (final String responseID : systemCorrectResponses) {
          responseIDToSystemFindingIt.put(responseID, prefix);
        }
      }
    }
    return responseIDToSystemFindingIt;
  }

}
