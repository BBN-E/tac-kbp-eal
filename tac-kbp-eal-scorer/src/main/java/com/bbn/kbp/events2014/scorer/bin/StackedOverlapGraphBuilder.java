package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.gnuplot.Axis;
import com.bbn.bue.gnuplot.GnuPlotRenderer;
import com.bbn.bue.gnuplot.Key;
import com.bbn.bue.gnuplot.StackedBarChart;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.intersection;
import static com.google.common.collect.Sets.union;

/**
 * Created by rgabbard on 11/5/14.
 */
class StackedOverlapGraphBuilder {

  private static final Logger log = LoggerFactory.getLogger(StackedOverlapGraphBuilder.class);
  private static final ImmutableList<String> BAR_SEGMENTS = ImmutableList.of(
      "T_only", "T_H", "T_O", "T_H_O", "SameTeamOther", "H_O", "O_only", "H_only");
  private final StackedBarChart.Builder chart = StackedBarChart.builder()
      .setXAxis(Axis.xAxis().rotateLabels().build())
      .addBarSegments(BAR_SEGMENTS)
      .setKey(Key.visibleKey().outside().vertical().withBox().build());
  private double yMax = Double.NEGATIVE_INFINITY;
  private String plotName;

  StackedOverlapGraphBuilder(PlotOverlap.PlotType plotType) {
    this.plotName = plotType.name();
    chart.setTitle("Overlap distribution of " + plotName);
  }

  public void addBar(Map<String, ImmutableSet<String>> systemToEquivIDs, String systemName,
      ImmutableSet<String> systemIDs, Set<String> systemsSharingPrefix,
      Set<String> humanSystems, ImmutableSet<String> humanIDs) {
    final Set<String> T = systemIDs;
    final Set<String> H = humanIDs;
    final Set<String> nonHumanSystemsFromOtherTeams =
        difference(difference(systemToEquivIDs.keySet(),
            systemsSharingPrefix), humanSystems);
    final Set<String> O = PlotOverlap.gatherIDs(systemToEquivIDs, nonHumanSystemsFromOtherTeams);
    final Set<String> O_SameTeam = PlotOverlap.gatherIDs(systemToEquivIDs,
        difference(systemsSharingPrefix, ImmutableSet.of(systemName)));

    final double T_only = difference(T, union(H, O)).size();
    final double H_T = difference(intersection(T, H), O).size();
    final double T_O = difference(intersection(T, O), H).size();
    final double H_O_T = intersection(intersection(T, H), O).size();
    final double H_O = difference(intersection(H, O), T).size();
    final double O_only = difference(difference(O, T), H).size();
    final double H_only = difference(difference(H, T), O).size();
    final double O_SameTeam_Only = difference(O_SameTeam, union(T, union(H, O))).size();

    yMax = Math.max(yMax, 1.2 * (T_only + H_T + T_O + H_O_T + H_O + O_only + H_only
                                     + O_SameTeam_Only));

    chart.addStackedBar(StackedBarChart.StackedBar.create(systemName,
        ImmutableList.of(T_only, H_T, T_O, H_O_T, O_SameTeam_Only, H_O, O_only, H_only)));
  }

  public void renderTo(GnuPlotRenderer renderer, File directory)
      throws IOException, InterruptedException {
    chart.setYAxis(Axis.yAxis().setLabel(plotName)
        .setRange(Range.closed(0.0, yMax)).build());
    final File outputDir = new File(directory, plotName);
    final File commandsFile = chart.build().renderToEmptyDirectory(outputDir);
    renderer.render(commandsFile, new File(outputDir, "chart.png"));
  }
}
