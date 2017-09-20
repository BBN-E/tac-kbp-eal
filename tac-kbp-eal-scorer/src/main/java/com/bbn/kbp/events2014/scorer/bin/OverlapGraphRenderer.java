package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.collections.CollectionUtils;
import com.bbn.bue.gnuplot.Axis;
import com.bbn.bue.gnuplot.BarChart;
import com.bbn.bue.gnuplot.GnuPlotRenderer;

import com.google.common.base.Optional;
import com.google.common.collect.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.*;

/**
 * This is hideous, but it's a one-off thing, so not work cleaning up.
 */
class OverlapGraphRenderer {

  private static final Logger log = LoggerFactory.getLogger(OverlapGraphRenderer.class);

  final GnuPlotRenderer renderer;
  final Map<String, ImmutableSet<String>> systemToEquivIDs;
  final Set<String> humanSystems;
  final Set<String> referenceSystems;
  final Set<String> expandedReferenceSystems;
  final Set<String> expandedReferenceIDs;
  final Set<String> referenceIDs;

  public OverlapGraphRenderer(GnuPlotRenderer renderer,
      Map<String, ImmutableSet<String>> systemToEquivIDs,
      Set<String> humanSystems, Set<String> referenceSystems,
      Optional<Integer> excludeSharedPrefixSize) {
    this.renderer = renderer;
    this.systemToEquivIDs = systemToEquivIDs;
    this.humanSystems = humanSystems;
    this.referenceSystems = referenceSystems;
    this.expandedReferenceSystems = PlotOverlap.getSystemsSharingPrefix(referenceSystems,
        systemToEquivIDs.keySet(), excludeSharedPrefixSize);
    log.info("Expanded reference systems from {} to {} for computing non-reference overlap",
        referenceSystems, expandedReferenceSystems);
    this.expandedReferenceIDs = PlotOverlap.gatherIDs(systemToEquivIDs, expandedReferenceSystems);
    this.referenceIDs = PlotOverlap.gatherIDs(systemToEquivIDs, referenceSystems);
  }

  public void renderPlot(String title, Set<String> systemIDs,
      Set<String> humanIDs,
      Set<String> systemsSharingPrefix, File outputDir) throws IOException,
                                                               InterruptedException {
    final Set<String> systemsToIgnoreForNonReference = ImmutableSet.copyOf(
        Iterables.concat(humanSystems, systemsSharingPrefix, expandedReferenceSystems));

    final Map<String, ImmutableSet<String>> nonReferenceSystemIDMap =
        Maps.filterKeys(systemToEquivIDs, not(in(systemsToIgnoreForNonReference)));

    final ImmutableSet<String> nonReferenceSystemIDs =
        ImmutableSet.copyOf(concat(nonReferenceSystemIDMap.values()));
    final ImmutableSet<String> nonReferenceSystemOnlyIDs =
        difference(nonReferenceSystemIDs, expandedReferenceIDs).immutableCopy();

    final ImmutableSet<String> humanOverlap = intersection(systemIDs, humanIDs).immutableCopy();
    final ImmutableSet<String> referenceOverlap =
        intersection(systemIDs, referenceIDs).immutableCopy();
    final ImmutableSet<String> nonReferenceOverlap =
        intersection(systemIDs, nonReferenceSystemIDs).immutableCopy();
    final Sets.SetView<String> nonUnique = union(referenceIDs, nonReferenceSystemIDs);
    final ImmutableSet<String> unique = difference(systemIDs, nonUnique).immutableCopy();

    final int biggestBar = Ordering.natural().max(transform(
        ImmutableList.of(systemIDs, humanOverlap, referenceOverlap, nonReferenceOverlap, unique),
        CollectionUtils.sizeFunction()));

    outputDir.mkdirs();
    final File commandsFile = BarChart.builder()
        .setTitle(title)
        .setXAxis(Axis.xAxis().build())
        .setYAxis(Axis.yAxis()
            .setLabel("Number of Equivalence classes")
                // make y-axis range to 20% taller than biggest bar
            .setRange(Range.closed(0.0, 1.2 * biggestBar)).build())
        .setBoxWidth(0.5)
        .addBar(BarChart.Bar.builder(systemIDs.size()).setLabel("Total").build())
        .addBar(BarChart.Bar.builder(unique.size()).setLabel("Unique").build())
        .addBar(BarChart.Bar.builder(humanOverlap.size()).setLabel("Human").build())
        .addBar(BarChart.Bar.builder(referenceOverlap.size()).setLabel("Reference system").build())
        .addBar(BarChart.Bar.builder(nonReferenceOverlap.size()).setLabel("Non-reference systems")
            .build())
        .hideKey()
        .build().renderToEmptyDirectory(outputDir);

    renderer.render(commandsFile, new File(outputDir, "chart.png"));
  }
}
