package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.collections.MultisetUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.gnuplot.Axis;
import com.bbn.bue.gnuplot.BarChart;
import com.bbn.bue.gnuplot.GnuPlotRenderer;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.bin.CorpusAnalysis;
import com.bbn.kbp.linking.EALScorer2015Style;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Range;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

final class ChartsAboutData implements KBP2015Scorer.SimpleResultWriter {
  private final GnuPlotRenderer renderer;

  ChartsAboutData(final GnuPlotRenderer renderer) {
    this.renderer = checkNotNull(renderer);
  }

  @Override
  public void writeResult(final List<EALScorer2015Style.Result> perDocResults,
      final File baseOutputDir) throws IOException {
    final Multiset<String> truePositives = HashMultiset.create();
    final Multiset<String> falsePositives = HashMultiset.create();

    final ImmutableSet.Builder<TypeRoleFillerRealis> correctEquivClassesBuilder = ImmutableSet.builder();
    final ImmutableMultimap.Builder<TypeRoleFillerRealis, AssessedResponse> equivClassToAssessedResponseBuilder = ImmutableMultimap.builder();
    final ImmutableSet.Builder<EventArgumentLinking> referenceLinkingsBuilder = ImmutableSet.builder();

    for(final EALScorer2015Style.Result perDocResult : perDocResults) {
      final EventArgScoringAlignment<TypeRoleFillerRealis> argAlignment = perDocResult.argResult().argumentScoringAlignment();
      correctEquivClassesBuilder.addAll(argAlignment.truePositiveEquivalenceClasses());
      correctEquivClassesBuilder.addAll(argAlignment.falseNegativeEquivalenceClasses());
      equivClassToAssessedResponseBuilder.putAll(
          argAlignment.equivalenceClassesToAnswerKeyResponses());

      referenceLinkingsBuilder.add(perDocResult.linkResult().linkingScore().referenceArgumentLinking());
    }

    final ImmutableSet<TypeRoleFillerRealis> correctEquivClasses = correctEquivClassesBuilder.build();
    final ImmutableMultimap<TypeRoleFillerRealis, AssessedResponse> equivClassToAssessedResponse = equivClassToAssessedResponseBuilder.build();
    final ImmutableSet<EventArgumentLinking> referenceLinkings = referenceLinkingsBuilder.build();

    generateChartEventRoleCounts(correctEquivClasses, baseOutputDir);

    generateChartRealisCounts(correctEquivClasses, baseOutputDir);

    generateChartMentionTypeCounts(correctEquivClasses, equivClassToAssessedResponse, baseOutputDir);

    generateChartEventHopperCounts(referenceLinkings, baseOutputDir);

    generateChartNumberOfDocsPerEventType(correctEquivClasses, baseOutputDir);
  }

  private void generateChartEventRoleCounts(final ImmutableSet<TypeRoleFillerRealis> equivClasses,
      final File outputDir) throws IOException {
    // num# correct TRFRs for each event type.role
    final ImmutableMultiset<Symbol> eventRoleCounts = CorpusAnalysis.toEventRoleCounts(equivClasses);

    writeToFile(eventRoleCounts, new File(outputDir, "eventRoleCount.txt"));

    writeToChart(eventRoleCounts, new File(outputDir, "eventRoleCount.png"),
        renderer, "Event Role Counts", "Event Roles", "Counts", Optional.of(30));
  }

  private void generateChartRealisCounts(final ImmutableSet<TypeRoleFillerRealis> equivClasses,
      final File outputDir) throws IOException {
    // num# correct TRFRs for each realis
    final ImmutableMultiset<Symbol> realisCounts = CorpusAnalysis.toRealisCounts(equivClasses);

    writeToFile(realisCounts, new File(outputDir, "realisCount.txt"));

    writeToChart(realisCounts, new File(outputDir, "realisCount.png"),
        renderer, "Realis Counts", "Realis", "Counts", Optional.<Integer>absent());
  }

  private void generateChartMentionTypeCounts(final ImmutableSet<TypeRoleFillerRealis> equivClasses,
      final ImmutableMultimap<TypeRoleFillerRealis, AssessedResponse> equivClassToAssessedResponse,
      final File outputDir) throws IOException {
    // num# correct TRFRs for each mention type
    final ImmutableMultiset<Symbol> mentionTypeCounts = CorpusAnalysis.toMentionTypeCounts(
        equivClasses, equivClassToAssessedResponse);

    writeToFile(mentionTypeCounts, new File(outputDir, "mentionTypeCount.txt"));

    writeToChart(mentionTypeCounts, new File(outputDir, "mentionTypeCount.png"),
        renderer, "Mention Type Counts", "MentionTypes", "Counts", Optional.<Integer>absent());
  }

  private void generateChartEventHopperCounts(final ImmutableSet<EventArgumentLinking> linkings,
      final File outputDir) throws IOException {
    // num# event hoppers for each event type
    final ImmutableMultiset<Symbol> eventHopperPerEventTypeCounts = CorpusAnalysis.toEventHopperCounts(
        linkings);

    writeToFile(eventHopperPerEventTypeCounts,
        new File(outputDir, "eventHopperPerEventTypeCount.txt"));

    writeToChart(eventHopperPerEventTypeCounts,
        new File(outputDir, "eventHopperPerEventTypeCount.png"),
        renderer, "Event Hopper Counts", "Event Types", "num# event hoppers", Optional.<Integer>absent());
  }

  private void generateChartNumberOfDocsPerEventType(final ImmutableSet<TypeRoleFillerRealis> equivClasses,
      final File outputDir) throws IOException {
    // num# documents containing each event type
    final ImmutableMultiset<Symbol> numberOfDocsPerEventTypeCounts = CorpusAnalysis.toNumberOfDocsPerEventType(
        equivClasses);

    writeToFile(numberOfDocsPerEventTypeCounts, new File(outputDir, "numberOfDocsPerEventTypeCount.txt"));

    writeToChart(numberOfDocsPerEventTypeCounts,
        new File(outputDir, "numberOfDocsPerEventTypeCount.png"),
        renderer, "Number of documents per event type", "Event Types", "num# documents", Optional.<Integer>absent());
  }

  static void writeToFile(final Multiset<Symbol> counts, final File outFile) throws IOException {
    Files.asCharSink(outFile, Charsets.UTF_8).write(
        Joiner.on("\n").join(
            FluentIterable.from(counts.entrySet())
                .transform(new Function<Multiset.Entry<Symbol>, String>() {
                             @Override
                             public String apply(final Multiset.Entry<Symbol> input) {
                               return String.format("%s\t%d", input.getElement().toString(),
                                   input.getCount());
                             }
                           }
                )) + "\n");
  }

  static void writeToChart(final Multiset<Symbol> counts, final File outFile,
      final GnuPlotRenderer renderer,
      final String chartTitle, final String xAxisLabel, final String yAxisLabel,
      final Optional<Integer> maxNumberOfBars)
      throws IOException {

    final int maxCount = MultisetUtils.maxCount(counts);

    final Axis X_AXIS = Axis.xAxis().setLabel(xAxisLabel).rotateLabels().build();
    final Axis Y_AXIS = Axis.yAxis().setLabel(yAxisLabel).setRange(
        Range.closed(0.0, (double) roundUpNearestFactor(maxCount))).build();

    final BarChart.Builder chartBuilder =
        BarChart.builder().setTitle(chartTitle).setXAxis(X_AXIS).setYAxis(Y_AXIS).hideKey();

    int barCount = 0;
    for (final Multiset.Entry<Symbol> e : counts.entrySet()) {
      chartBuilder
          .addBar(BarChart.Bar.builder(e.getCount()).setLabel(e.getElement().toString()).build());

      barCount += 1;
      if(maxNumberOfBars.isPresent() && (barCount >= maxNumberOfBars.get())) {
        break;
      }
    }

    renderer.renderTo(chartBuilder.build(), outFile);
  }

  private static int roundUpNearestFactor(int n) {
    final int factor = n < 100? 10 : 100;

    if((n % factor) == 0) {
      return n;
    } else {
      return (factor - n % factor) + n;
    }
  }
}
