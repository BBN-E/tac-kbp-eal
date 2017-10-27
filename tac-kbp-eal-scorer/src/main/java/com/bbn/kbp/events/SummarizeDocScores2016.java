package com.bbn.kbp.events;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.collections.MultimapUtils;
import com.bbn.bue.common.evaluation.BootstrapWriter;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.evaluation.FMeasureInfo;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.math.PercentileComputer;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.scoring.Scoreds;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.gnuplot.Axis;
import com.bbn.bue.gnuplot.BoxPlot;
import com.bbn.bue.gnuplot.GnuPlotRenderer;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

import org.immutables.func.Functional;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.bbn.bue.common.StringUtils.suffixWithFunction;
import static com.bbn.kbp.events.SystemConditionScoresFunctions.argPercentiles;
import static com.bbn.kbp.events.SystemScoresFunctions.withRealis;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.in;

/**
 * Summarizes the scorer output for multiple systems for the TAC KBP 2016 EAL eval for easy analysis.
 */
public final class SummarizeDocScores2016 {
  private static final Logger log = LoggerFactory.getLogger(SummarizeDocScores2016.class);

  private final File inputDir;
  private final File outputCSVFile;
  private final File outputChartDir;
  private final String language;
  private final GnuPlotRenderer plotRenderer;

  SummarizeDocScores2016(final File inputDir, final File outputCSVFile,
      File outputChartDir, final String language, GnuPlotRenderer renderer) {
    this.inputDir = checkNotNull(inputDir);
    this.outputCSVFile = checkNotNull(outputCSVFile);
    this.outputChartDir = checkNotNull(outputChartDir);
    this.plotRenderer = checkNotNull(renderer);
    this.language = checkNotNull(language);
  }

  public static void main(String[] argv) {
      // we wrap the main method in this way to
      // ensure a non-zero return value on failure
      try {
          trueMain(argv);
      } catch (Exception e) {
          e.printStackTrace();
          System.exit(1);
      }
  }

  private static void trueMain(String[] argv) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final File outputDir = params.getCreatableDirectory("outputDir");

    new SummarizeDocScores2016(params.getExistingDirectory("inputDir"),
        new File(outputDir, "summary.csv"), new File(outputDir, "charts"),
        params.getString("language"),
        GnuPlotRenderer.createForGnuPlotExecutable(params.getExistingFile("gnuPlot.bin"))
        ).go(params);
  }

  public void go(Parameters params) throws IOException {
    log.info("Loading system scores from {}", inputDir);

    final ImmutableMap<File, SystemScores> scoresMap =
        FluentIterable.from(ImmutableList.copyOf(inputDir.listFiles()))
            .filter(FileUtils.isDirectoryPredicate())
            .toMap(ExtractScores.INSTANCE);

    writePerSubmissionSummaries(scoresMap);

    writePerTeamSummaries(scoresMap, FileUtils.loadStringMap(
        Files.asCharSource(params.getExistingFile("teamsFile"), Charsets.UTF_8)),
        new File(outputChartDir, "teams"));
  }



  private static final String DISTRIBUTION_MESSGAE = "* This document does not contain technology or "
      + "technical data controlled under either the U.S. International Traffic in Arms Regulations "
      + "or the U.S. Export Administration Regulations.\n"
      + "* Distribution C: Distribution authorized to U.S. Government Agencies and their "
      + "contractors (Administrative or Operational Use; November 4, 2014). Other requests for this "
      + "document shall be referred to DARPA Public Release Center (PRC) "
      + "via email at PRC@darpa.mil.";

  private void writePerTeamSummaries(final ImmutableMap<File, SystemScores> scoresMap,
      final ImmutableMap<String, String> systemToTeamMap,
      final File teamsDir) throws IOException {
    teamsDir.mkdirs();

    final ImmutableMap<String, SystemScores> systemNameToScores =
        MapUtils.copyWithKeysTransformedByInjection(scoresMap, FileUtils.toNameFunction());

    if (!systemToTeamMap.keySet().containsAll(systemNameToScores.keySet())) {
      throw new RuntimeException("Some submissions lack mappings to teams: " +
          Sets.difference(systemNameToScores.keySet(), systemToTeamMap.keySet()));
    }

    final ImmutableSetMultimap<String, String> teamToSystems = systemToTeamMap.asMultimap().inverse();

    for (final Map.Entry<String, Collection<String>> e : teamToSystems.asMap().entrySet()) {
      final String team = e.getKey();
      final Collection<String> submissions = e.getValue();

      final File teamMessage = new File(teamsDir, language + "_" + team + ".txt");
      try (Writer out = Files.asCharSink(teamMessage, Charsets.UTF_8).openBufferedStream()) {
        out.write(String.format("Team: %s\n", team));
        out.write(String.format("Language: %s\n", language));
        out.write(
            String.format("Number of participating teams: %d\n", teamToSystems.keySet().size()));
        out.write("\n\nThis report first contains a summary of the scores of your submissions "
            + "compared to those of the other systems based on the official metric for each "
            + "sub-task.  This is followed by more detailed information about your system's"
            + " performance.  In the charts below, max is the best scoring submission across "
            + "all participants.  If there were at least three submissions for this language,  "
            + "the median score over the best submissions from each team will be given as well.  "
            + "All summary scores are given as percentiles based on bootstrap resampling .\n\n");

        writeSummaryArgScores(submissions, systemNameToScores, teamToSystems, out);

        final Map<String, SystemScores> teamScores =
            writeSummaryLinkingScores(submissions, systemNameToScores, teamToSystems, out);

        printScoreDetails(teamScores, out);

        out.write("\n");
        out.write(DISTRIBUTION_MESSGAE);
      }
    }
  }

  private Map<String, SystemScores> writeSummaryLinkingScores(
      final Collection<String> focusSystemSubmissionNames,
      final ImmutableMap<String, SystemScores> systemNameToScores,
      final ImmutableSetMultimap<String, String> teamsToSystems, final Writer out)
      throws IOException {
    final Map<String, SystemScores> teamScores =
        Maps.filterKeys(systemNameToScores, in(focusSystemSubmissionNames));

    final Function<SystemScores, PercentileComputer.Percentiles> EXTRACT_LINKING_BOOTSTRAP_SCORES =
        Functions.compose(
            SummarizeDocScores2016.<String, PercentileComputer.Percentiles>getFunction("F1"),
            Functions.compose(SystemConditionScoresFunctions.linkPercentiles(), SystemScoresFunctions
                .withRealis()));

    final Function<SystemScores, Double> EXTRACT_LINKING_BOOTSTRAP_MEDIAN =
        Functions.compose(RecklessMedianFunction.INSTANCE, EXTRACT_LINKING_BOOTSTRAP_SCORES);
    final Ordering<SystemScores> BY_BOOTSTRAP_MEDIAN_LINKING_SCORE =
        Ordering.natural().onResultOf(EXTRACT_LINKING_BOOTSTRAP_MEDIAN);

    final ImmutableMap.Builder<String, SystemScores> linkScoresToPrint = ImmutableMap.builder();
    linkScoresToPrint.putAll(teamScores);

    final Optional<Map<Integer, PercentileComputer.Percentiles>> medianScores;
    if (teamsToSystems.keySet().size() > 3) {
      medianScores = Optional.of(Maps.transformValues(
          findMedianSystemsBy(systemNameToScores, teamsToSystems, EXTRACT_LINKING_BOOTSTRAP_MEDIAN),
          EXTRACT_LINKING_BOOTSTRAP_SCORES));
    } else {
      medianScores = Optional.absent();
    }

    linkScoresToPrint.put("Max", BY_BOOTSTRAP_MEDIAN_LINKING_SCORE.max(systemNameToScores.values()));
    printBootstrapScoresChart(linkScoresToPrint.build(), EXTRACT_LINKING_BOOTSTRAP_SCORES,
        medianScores, out);
    return teamScores;
  }

  private static final Function<SystemScores, PercentileComputer.Percentiles> EXTRACT_ARG_BOOTSTRAP_SCORES =
      Functions.compose(
          SummarizeDocScores2016.<String, PercentileComputer.Percentiles>getFunction(
              "LinearScore"),
          Functions.compose(
              SystemConditionScoresFunctions.argPercentiles(), SystemScoresFunctions.withRealis()));
  private static final Function<SystemScores, Double> EXTRACT_ARG_BOOTSTRAP_MEDIAN =
      Functions.compose(RecklessMedianFunction.INSTANCE, EXTRACT_ARG_BOOTSTRAP_SCORES);
  private static final Ordering<SystemScores> BY_BOOTSTRAP_MEDIAN_ARG_SCORE =
      Ordering.natural().onResultOf(EXTRACT_ARG_BOOTSTRAP_MEDIAN);

  private void writeSummaryArgScores(
      final Collection<String> focusTeamSubmissionNames,
      final ImmutableMap<String, SystemScores> systemNameToScores,
      final ImmutableSetMultimap<String, String> teamsToSystems, final Writer out)
      throws IOException {
    out.write("Document Level Argument Summary Score:\n\n");

    final Map<String, SystemScores> teamScores =
        Maps.filterKeys(systemNameToScores, in(focusTeamSubmissionNames));

    final ImmutableMap.Builder<String, SystemScores> argScoresToPrint = ImmutableMap.builder();
    argScoresToPrint.putAll(teamScores);
    final Optional<Map<Integer, PercentileComputer.Percentiles>> medianScores;
    if (teamsToSystems.keySet().size() > 3) {
      medianScores = Optional.of(Maps.transformValues(
          findMedianSystemsBy(systemNameToScores, teamsToSystems, EXTRACT_ARG_BOOTSTRAP_MEDIAN),
          EXTRACT_ARG_BOOTSTRAP_SCORES));
    } else {
      medianScores = Optional.absent();
    }

    argScoresToPrint.put("Max", BY_BOOTSTRAP_MEDIAN_ARG_SCORE.max(systemNameToScores.values()));
    printBootstrapScoresChart(argScoresToPrint.build(), EXTRACT_ARG_BOOTSTRAP_SCORES, medianScores, out);

    out.write("The argument score is described in section 7.1 of the task guidelines.\n\n");
  }

  private void printScoreDetails(final Map<String, SystemScores> scoresToPrint, final Writer out)
      throws IOException {
    out.write("The linking score is described in section 7.1 of the task guidelines.\n\n");

    out.write("Score details:\n" +
        "TP = # of true positive document-level arguments found\n"
        + "FP = # of false positive document-level arguments found\n"
        + "FN = # of false negative document-level arguments\n"
        + "ArgP = precision of finding document-level arguments\n"
        + "ArgR = recall of finding document-level arguments\n"
        + "F1 = F1 measure of finding document-level arguments\n"
        + "ArgScore = official document-level argument finding score\n"
        + "LinkScore = official document-level linking score\n\n"
        + "All scores scaled 0-100\n\n");

    final String headerPattern = "%10s\t%6s\t%6s\t%6s\t%6s\t%6s\t%6s\t%6s\t%6s\n";
    out.write(String.format(headerPattern, "System", "TP", "FP", "FN", "ArgP", "ArgR", "ArgF1",
        "ArgScore", "LinkScore"));
    final String linePattern =
        "%10s\t%6.1f\t%6.1f\t%6.1f\t%6.1f\t%6.1f\t%6.1f\t%6.1f\t%6.1f\n";

    for (final Map.Entry<String, SystemScores> submissionEntry : scoresToPrint.entrySet()) {
      final String submissionName = submissionEntry.getKey();
      final SystemConditionScores scores = submissionEntry.getValue().withRealis();
      out.write(String.format(linePattern, submissionName, scores.argScore().fMeasureCounts().truePositives(),
          scores.argScore().fMeasureCounts().falsePositives(),
          scores.argScore().fMeasureCounts().falseNegatives(),
          100.0*scores.argScore().fMeasureCounts().precision(),
          100.0*scores.argScore().fMeasureCounts().recall(),
          100.0*scores.argScore().fMeasureCounts().F1(),
          scores.argPercentiles().get("LinearScore").median().get(),
          scores.linkPercentiles().get("F1").median().get()));
    }
  }

  private void printBootstrapScoresChart(final Map<String, SystemScores> scoresToPrint,
      final Function<SystemScores, PercentileComputer.Percentiles> toPercentiles,
      final Optional<? extends Map<Integer, PercentileComputer.Percentiles>> medianScores, final Writer out) throws IOException {
    final String headerFormat = "%20s\t%10s\t%10s\t%10s\n";

    out.write(String.format(headerFormat, "System", "5%", "50%", "95%\n"));
    for (final Map.Entry<String, PercentileComputer.Percentiles> e :
        Maps.transformValues(scoresToPrint, toPercentiles).entrySet()) {
      final String submissionName = e.getKey();
      final PercentileComputer.Percentiles percentiles = e.getValue();

      writePercentileLine(submissionName, percentiles, out);
    }

    if (medianScores.isPresent()) {
      if (medianScores.get().size() == 1) {
        final Map.Entry<Integer, PercentileComputer.Percentiles> medianEl =
            Iterables.getOnlyElement(medianScores.get().entrySet());
        writePercentileLine("Rank " + (medianEl.getKey() + 1), medianEl.getValue(), out);
      } else if (medianScores.get().size() == 2) {
        final ImmutableList<Map.Entry<Integer, PercentileComputer.Percentiles>> entryList =
            ImmutableList.copyOf(medianScores.get().entrySet());
        writePercentileLine("Rank " + (entryList.get(0).getKey() + 1), entryList.get(0).getValue(), out);
        writePercentileLine("Rank " + (entryList.get(1).getKey() + 1), entryList.get(1).getValue(), out);
      } else {
        throw new IllegalStateException("can't happen");
      }
    }
    out.write("\n");
  }

  private void writePercentileLine(final String label,
      final PercentileComputer.Percentiles percentiles, final Writer out) throws IOException {
    final String lineFormat = "%20s\t%10.1f\t%10.1f\t%10.1f\n";
    out.write(String.format(lineFormat, label, percentiles.percentile(.05).get(),
        percentiles.percentile(0.5).get(), percentiles.percentile(0.95).get()));
  }

  ImmutableMap<Integer, SystemScores> findMedianSystemsBy(
      Map<String, SystemScores> submissionNameToScores,
      final Multimap<String, String> teamsToSystems,
      Function<SystemScores, Double> rankScoreFunc) {
    checkArgument(!teamsToSystems.isEmpty());

    final Map<String, Double> systemToRankScore = Maps.transformValues(submissionNameToScores, rankScoreFunc);

    // first locate the best scoring system for each team
    final ImmutableMap<String, String> teamToBestSubmissionName =
        MultimapUtils.reduceToMap(teamsToSystems, new Function<Collection<String>, String>() {
          @Override
          public String apply(final Collection<String> submissionNames) {
            return Scoreds.<String>ByScoreThenByItem().max(
                mapToScoreds(FluentIterable.from(submissionNames)
                    .toMap(Functions.forMap(systemToRankScore)))).item();
          }
        });

    final List<Scored<String>> teamToBestScores =
        Scoreds.<String>ByScoreThenByItem().sortedCopy(
            mapToScoreds(
                Maps.transformValues(teamToBestSubmissionName,
                    Functions.compose(rankScoreFunc, Functions.forMap(submissionNameToScores)))));

    if (teamToBestScores.size() % 2 == 1) {
      final int index = teamToBestScores.size() / 2;
      final String medianSubmissionName = teamToBestSubmissionName.get(teamToBestScores.get(index).item());
      log.info("Median submission name: {}", medianSubmissionName);
      return ImmutableMap.of(index, submissionNameToScores.get(
          medianSubmissionName));
    } else {
      final int lowIndex = teamToBestScores.size() / 2 - 1;
      final int highIndex = teamToBestScores.size() / 2;

      return ImmutableMap.of(
          lowIndex, submissionNameToScores.get(teamToBestSubmissionName.get(teamToBestScores.get(lowIndex).item())),
          highIndex, submissionNameToScores.get(teamToBestSubmissionName.get(teamToBestScores.get(highIndex).item())));
    }
  }

  private void writePerSubmissionSummaries(final Map<File, SystemScores> scoresMap)
      throws IOException {
    final ImmutableSortedMap<File, SystemScores> sortedScores = ImmutableSortedMap.copyOf(scoresMap);

    // write CSV summary
    log.info("Writing CSV to {}", outputCSVFile);
    try (Writer out = Files.asCharSink(outputCSVFile, Charsets.UTF_8).openBufferedStream()) {
      // write header
      out.write("System Name, ");
      out.write(SystemScores.header());
      out.write("\n");

      for (final Map.Entry<File, SystemScores> e : sortedScores.entrySet()) {
        out.write(e.getKey().getName());
        out.write(", ");
        out.write(e.getValue().asCSVLine());
        out.write("\n");
      }
    }

    final BoxPlot.Builder withRealisPlot = BoxPlot.builder();

    withRealisPlot.setTitle("2016 Doc-level Event Argument Scores")
        .setYAxis(Axis.yAxis().setLabel("ArgScore").setRange(Range.closed(0.0, 15.0))
            .build())
        .setXAxis(Axis.xAxis().setLabel("System").rotateLabels().build())
        .hideKey()
        .setWhiskers(BoxPlot.Whiskers.builder().setExtentMode(BoxPlot.Whiskers.Fraction.of(0.95)).build());

    final BoxPlot.Builder noRealisPlot = BoxPlot.builder();

    noRealisPlot.setTitle("2016 Doc-level Event Argument Scores (no realis)")
        .setYAxis(Axis.yAxis().setLabel("ArgScore").setRange(Range.closed(0.0, 25.0))
            .build())
        .setXAxis(Axis.xAxis().setLabel("System").rotateLabels().build())
        .hideKey()
        .setWhiskers(BoxPlot.Whiskers.builder().setExtentMode(BoxPlot.Whiskers.Fraction.of(0.95)).build());

    final BoxPlot.Builder withRealisLinkingPlot = BoxPlot.builder();

    withRealisLinkingPlot.setTitle("2016 Doc-level Linking Scores")
        .setYAxis(Axis.yAxis().setLabel("LinkScore").setRange(Range.closed(0.0, 15.0))
            .build())
        .setXAxis(Axis.xAxis().setLabel("System").rotateLabels().build())
        .hideKey()
        .setWhiskers(BoxPlot.Whiskers.builder().setExtentMode(BoxPlot.Whiskers.Fraction.of(0.95)).build());

    final BoxPlot.Builder noRealisLinkingPlot = BoxPlot.builder();

    noRealisLinkingPlot.setTitle("2016 Doc-level Event Linking Scores (no realis)")
        .setYAxis(Axis.yAxis().setLabel("LinkScore").setRange(Range.closed(0.0, 15.0))
            .build())
        .setXAxis(Axis.xAxis().setLabel("System").rotateLabels().build())
        .hideKey()
        .setWhiskers(BoxPlot.Whiskers.builder().setExtentMode(BoxPlot.Whiskers.Fraction.of(0.95)).build());


    // make bootstrapped charts
    for (final Map.Entry<File, SystemScores> e : sortedScores.entrySet()) {
      withRealisPlot.addDataset(BoxPlot.Dataset.createCopyingData(e.getKey().getName(),
          Doubles.toArray(e.getValue().withRealis().argPercentiles().get("LinearScore").rawData()))).build();
      noRealisPlot.addDataset(BoxPlot.Dataset.createCopyingData(e.getKey().getName(),
          Doubles.toArray(e.getValue().noRealis().argPercentiles().get("LinearScore").rawData()))).build();

      withRealisLinkingPlot.addDataset(BoxPlot.Dataset.createCopyingData(e.getKey().getName(),
          Doubles.toArray(e.getValue().withRealis().linkPercentiles().get("F1").rawData()))).build();
      noRealisLinkingPlot.addDataset(BoxPlot.Dataset.createCopyingData(e.getKey().getName(),
          Doubles.toArray(e.getValue().noRealis().linkPercentiles().get("F1").rawData()))).build();
    }

    outputChartDir.mkdirs();
    plotRenderer.renderTo(withRealisPlot.build(), new File(outputChartDir, "withRealis.args.png"));
    plotRenderer.renderTo(noRealisPlot.build(), new File(outputChartDir, "noRealis.args.png"));
    plotRenderer.renderTo(withRealisLinkingPlot.build(), new File(outputChartDir, "withRealis.link.png"));
    plotRenderer.renderTo(noRealisLinkingPlot.build(), new File(outputChartDir, "noRealis.link.png"));
  }

  @MoveToBUECommon
  public static <K,V> Function<Map<? super K,V>, V> getFunction(final K key) {
    return new Function<Map<? super K, V>, V>() {
      @Override
      public V apply(final Map<? super K, V> input) {
        return input.get(key);
      }
    };
  };

  @MoveToBUECommon
  public static <T> ImmutableSet<Scored<T>> mapToScoreds(Map<T, Double> map) {
    return FluentIterable.from(map.entrySet())
        .transform(Scoreds.<T>mapEntryToDoubleToScored())
        .toSet();
  }

  /**
   * Maps percentile information to its median value; crashes if the percentiles contain no data
   */
  public enum RecklessMedianFunction implements Function<PercentileComputer.Percentiles, Double> {
    INSTANCE;

    @Override
    public Double apply(final PercentileComputer.Percentiles input) {
      return input.median().get();
    }
  }
}

/**
 * Turns the scoring output directory for a system into a summary of its scores
 */
@SuppressWarnings("ImmutableEnumChecker")
enum ExtractScores implements Function<File, SystemScores> {
  INSTANCE;

  final JacksonSerializer deserializer = JacksonSerializer.builder().forJson().build();

  @Override
  // deserialization requires unchecked cast
  @SuppressWarnings("unchecked")
  public SystemScores apply(final File scoringDir) {
    try {
      final File alignmentFailuresFile = new File(new File(scoringDir, "alignmentFailures"),
          "alignmentFailures.count.txt");
      final int numAlignmentFailures = (int) deserializer.deserializeFrom(
              Files.asByteSource(alignmentFailuresFile));

      return new SystemScores.Builder()
          .noRealis(extractConditionScores(new File(scoringDir, "noRealis")))
          .withRealis(extractConditionScores(new File(scoringDir, "withRealis")))
          .alignmentFailures(numAlignmentFailures)
          .build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // unchecked cast cannot be avoided with deserialization
  @SuppressWarnings("unchecked")
  private SystemConditionScores extractConditionScores(File scoreDir) throws IOException {


    final File fScores = new File(scoreDir, "aggregateF.txtF.txt.json");
    final FMeasureCounts argFScores = (FMeasureCounts) deserializer.deserializeFrom(
        Files.asByteSource(fScores));

    final File argScoresFile = new File(scoreDir, "argScore.json");
    final ArgScoreSummary argScore = (ArgScoreSummary)deserializer.deserializeFrom(
        Files.asByteSource(argScoresFile));

    final File argPercentiles = new File(new File(new File(scoreDir, "argScores"), "bootstrapData"), "Aggregate.percentile.json");

    final BootstrapWriter.SerializedBootstrapResults argBootstrapResults =
        (BootstrapWriter.SerializedBootstrapResults) deserializer
            .deserializeFrom(Files.asByteSource(argPercentiles));

    final File linkPercentiles = new File(new File(new File(scoreDir, "linkScores"), "bootstrapData"), "Aggregate.percentile.json");

    final BootstrapWriter.SerializedBootstrapResults linkBootstrapResults =
        (BootstrapWriter.SerializedBootstrapResults) deserializer
            .deserializeFrom(Files.asByteSource(linkPercentiles));

    return new SystemConditionScores.Builder()
        .argScore(argScore)
        .argFMeasures(argFScores)
        .argPercentiles(argBootstrapResults.percentilesMap())
        .linkPercentiles(linkBootstrapResults.percentilesMap())
        .build();
  }
}

/**
 * Summarizes the scores for a single system
 */
@TextGroupImmutable
@Functional
@Value.Immutable
abstract class SystemScores {
  abstract SystemConditionScores withRealis();
  abstract SystemConditionScores noRealis();
  abstract int alignmentFailures();

  final String asCSVLine() {
    return withRealis().asCSVLine() + ", " + noRealis().asCSVLine()
        + ", " + alignmentFailures();
  }

  public static String header() {
    return SystemConditionScores.header(" +R") + ", " + SystemConditionScores.header(" -R")
         + ", #Align Fail";
  }

  public static class Builder extends ImmutableSystemScores.Builder {}
}

/**
 * Summarises the scores for a single system in a single realis scoring condition
 */
@TextGroupImmutable
@Functional
@Value.Immutable
abstract class SystemConditionScores {
  abstract ArgScoreSummary argScore();
  abstract FMeasureInfo argFMeasures();
  abstract ImmutableMap<String, PercentileComputer.Percentiles> argPercentiles();
  abstract ImmutableMap<String, PercentileComputer.Percentiles> linkPercentiles();

  final String asCSVLine() {
    return FluentIterable.from(ImmutableList.of(
        argPercentiles().get("LinearScore").percentile(0.05).or(Double.NaN),
        argPercentiles().get("LinearScore").median().or(Double.NaN),
        argPercentiles().get("LinearScore").percentile(0.95).or(Double.NaN),
        100.0*argFMeasures().precision(),
        100.0*argFMeasures().recall(), 100.0 * argFMeasures().F1(),
        linkPercentiles().get("Precision").median().or(Double.NaN),
        linkPercentiles().get("Recall").median().or(Double.NaN),
        linkPercentiles().get("F1").percentile(0.05).or(Double.NaN),
        linkPercentiles().get("F1").median().or(Double.NaN),
        linkPercentiles().get("F1").percentile(0.95).or(Double.NaN)))
        .transform(Prettify.INSTANCE)
        .join(Joiner.on(", "));
  }

  private static final ImmutableList<String> HEADER_STRINGS = ImmutableList.of("ArgScore 5%",
      "ArgScore 50%", "ArgScore 95%",
      "ArgP", "ArgR", "ArgF1", "LinkP", "LinkR", "LinkF1 5%","LinkF1 50%", "LinkF1 95%");
  public static String header(String suffix) {
    return FluentIterable.from(HEADER_STRINGS)
        .transform(suffixWithFunction(suffix))
        .join(Joiner.on(", "));
  }

  enum Prettify implements Function<Double, String> {
    INSTANCE;

    @Override
    public String apply(final Double input) {
      return String.format("%.2f", input);
    }
  }

  public static class Builder extends ImmutableSystemConditionScores.Builder {}
}
