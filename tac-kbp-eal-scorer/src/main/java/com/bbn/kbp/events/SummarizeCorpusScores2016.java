package com.bbn.kbp.events;


import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.evaluation.BootstrapWriter;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.math.PercentileComputer;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.gnuplot.Axis;
import com.bbn.bue.gnuplot.BoxPlot;
import com.bbn.bue.gnuplot.GnuPlotRenderer;
import com.bbn.kbp.events2014.CorpusQueryAssessments;
import com.bbn.kbp.events2014.QueryAssessment2016;
import com.bbn.kbp.events2014.QueryDocMatch;
import com.bbn.kbp.events2014.QueryResponse2016;
import com.bbn.kbp.events2014.io.SingleFileQueryAssessmentsLoader;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

import org.immutables.func.Functional;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import static com.bbn.bue.common.StringUtils.containsPredicate;
import static com.bbn.bue.common.StringUtils.suffixWithFunction;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Lists.transform;
import static com.google.common.io.Files.asCharSource;

public final class SummarizeCorpusScores2016 {

  private static final Logger log = LoggerFactory.getLogger(SummarizeCorpusScores2016.class);

  private final File inputDir;
  private final File outputDir;
  private final GnuPlotRenderer plotRenderer;
  private final double fScoreLDCMax;
  private final double fScoreNoLDCMax;
  private final double linearScoreLDCMax;
  private final double linearScoreNoLDCMax;
  private final CorpusQueryAssessments queryAssessments;

  private SummarizeCorpusScores2016(final File inputDir,
      final File outputDir, final GnuPlotRenderer plotRenderer, final double fScoreLDCMax,
      final double fScoreNoLDCMax, final double linearScoreLDCMax,
      final double linearScoreNoLDCMax, final CorpusQueryAssessments queryAssessments) {
    this.inputDir = inputDir;
    this.outputDir = outputDir;
    this.plotRenderer = plotRenderer;
    this.fScoreLDCMax = fScoreLDCMax;
    this.fScoreNoLDCMax = fScoreNoLDCMax;
    this.linearScoreLDCMax = linearScoreLDCMax;
    this.linearScoreNoLDCMax = linearScoreNoLDCMax;
    this.queryAssessments = queryAssessments;
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
    final File inputDir = params.getExistingDirectory("inputDir");
    final File outputDir = params.getCreatableDirectory("outputDir");
    final double fScoreLDCMax = params.getDouble("fScoreLDCMax");
    final double fScoreNoLDCMax = params.getDouble("fScoreNoLDCMax");
    final double linearScoreLDCMax = params.getDouble("linearScoreLDCMax");
    final double linearScoreNoLDCMax = params.getDouble("linearScoreNoLDCMax");
    final File queryResponseAssessmentsFile =
        params.getExistingFile("com.bbn.tac.eal.queryAssessmentsFile");
    final CorpusQueryAssessments queryAssessments =
        SingleFileQueryAssessmentsLoader.create().loadFrom(
            Files.asCharSource(queryResponseAssessmentsFile, Charsets.UTF_8));

    final GnuPlotRenderer plotRenderer =
        GnuPlotRenderer.createForGnuPlotExecutable(params.getExistingFile("gnuPlot.bin"));
    new SummarizeCorpusScores2016(inputDir, outputDir, plotRenderer, fScoreLDCMax, fScoreNoLDCMax,
        linearScoreLDCMax, linearScoreNoLDCMax, queryAssessments).go();
  }


  public void go() throws IOException {
    // plot overall scores & confidences
    final ImmutableMap<File, CorpusScores> scoresMap =
        FluentIterable.from(ImmutableList.copyOf(checkNotNull(inputDir.listFiles())))
            .filter(FileUtils.isDirectoryPredicate())
            .toMap(ExtractCorpusScores.INSTANCE);
    writePerSubmissionSummaries(scoresMap);
    // per system: plot queries
    // per query: plot system

    // write counts
    writeCounts();
  }

  private void writeCounts() throws IOException {
    final File outputSystemCounts = new File(outputDir, "systemCounts.csv");
    final Table<Symbol, Symbol, Set<QueryDocMatch>> truePositivesTable = HashBasedTable.create();
    final Table<Symbol, Symbol, Set<QueryDocMatch>> falsePositivesTable = HashBasedTable.create();
    final Map<Symbol, Set<QueryDocMatch>> correctAnswersCount = Maps.newHashMap();
    log.info("Writing per system counts to {}", outputSystemCounts);
    for (final QueryResponse2016 response : queryAssessments.queryReponses()) {
      for (final Symbol system : queryAssessments.systemIDs()) {
        truePositivesTable.put(system, response.queryID(), Sets.<QueryDocMatch>newHashSet());
        falsePositivesTable.put(system, response.queryID(), Sets.<QueryDocMatch>newHashSet());
        correctAnswersCount.put(response.queryID(), Sets.<QueryDocMatch>newHashSet());
      }
    }
    for (final QueryResponse2016 response : queryAssessments.queryReponses()) {
      final QueryAssessment2016 assessment = queryAssessments.assessments().get(response);
      if (assessment == null) {
        continue;
      }
      if (assessment.equals(QueryAssessment2016.CORRECT)) {
        correctAnswersCount.get(response.queryID())
            .add(QueryDocMatch.of(response.queryID(), response.docID(), assessment));
        for (final Symbol system : queryAssessments.queryResponsesToSystemIDs().get(response)) {
          truePositivesTable.get(system, response.queryID())
              .add(QueryDocMatch.of(response.queryID(), response.docID(), assessment));
        }
      } else {
        for (final Symbol system : queryAssessments.queryResponsesToSystemIDs().get(response)) {
          falsePositivesTable.get(system, response.queryID())
              .add(QueryDocMatch.of(response.queryID(), response.docID(), assessment));
        }
      }
    }

    final List<Symbol> allQueries = FluentIterable.from(correctAnswersCount.keySet())
        .append(falsePositivesTable.columnMap().keySet())
        .toSortedSet(Ordering.usingToString())
        .asList();
    try (Writer out = Files.asCharSink(outputSystemCounts, UTF_8).openBufferedStream()) {
      out.write("System, QueryID, TP, FP, FN\n");
      for (final Symbol system : queryAssessments.systemIDs()) {
        for (final Symbol queryID : allQueries) {
          int tp = truePositivesTable.get(system, queryID).size();
          int fp = falsePositivesTable.get(system, queryID).size();
          int fn = correctAnswersCount.get(queryID).size() - tp;
          out.write(String
              .format("%s, %s, %d, %d, %d\n", system.asString(), queryID.asString(), tp, fp, fn));
        }
      }
    }
  }

  private void writePerSubmissionSummaries(final Map<File, CorpusScores> scoresMap)
      throws IOException {
    final ImmutableSortedMap<File, CorpusScores> sortedScores =
        ImmutableSortedMap.copyOf(scoresMap);
    final ImmutableSortedMap<File, CorpusScores> sortedScoresNoLDC = ImmutableSortedMap.copyOf(
        Maps.filterKeys(scoresMap,
            not(compose(containsPredicate("LDC"), FileUtils.toNameFunction()))));

    // write CSV summary
    final File outputCSVFile = new File(outputDir, "perSubmission.csv");
    log.info("Writing CSV to {}", outputCSVFile);
    try (Writer out = Files.asCharSink(outputCSVFile, UTF_8).openBufferedStream()) {
      // write header
      out.write("System Name, ");
      out.write(CorpusScores.queryCSVHeader(""));
      out.write("\n");

      for (final Map.Entry<File, CorpusScores> e : sortedScores.entrySet()) {
        out.write(e.getKey().getName());
        out.write(", ");
        out.write(e.getValue().queryFAsCSVLine());
        out.write("\n");
      }
    }

    // plot with & without the LDC
    plotScores(sortedScores, new File(outputDir, "perSystemCorpusFScore.png"), "FScores",
        fScoreLDCMax, ExtractFScores.INSTANCE);
    plotScores(sortedScoresNoLDC, new File(outputDir, "perSystemCorpusFScoreNoLDC.png"), "FScores",
        fScoreNoLDCMax, ExtractFScores.INSTANCE);
    plotScores(sortedScores, new File(outputDir, "perSystemCorpusLinearScore.png"), "Linear Scores",
        linearScoreLDCMax, ExtractLinearScores.INSTANCE);
    plotScores(sortedScoresNoLDC, new File(outputDir, "perSystemCorpusLinearScoreNoLDC.png"),
        "Linear Scores", linearScoreNoLDCMax, ExtractLinearScores.INSTANCE);
  }

  private void plotScores(final ImmutableSortedMap<File, CorpusScores> sortedScores,
      final File outputFile, final String axisTitle, final double upperRange,
      final Function<CorpusScores, double[]> extractScores) throws IOException {
    final BoxPlot.Builder corpusFScoresBoxPlot = BoxPlot.builder();
    corpusFScoresBoxPlot.setTitle("2016 Corpus Query " + axisTitle)
        .setYAxis(Axis.yAxis().setLabel(axisTitle).setRange(Range.closed(0.0, upperRange))
            .build())
        .setXAxis(Axis.xAxis().setLabel("System").rotateLabels().build())
        .hideKey()
        .setWhiskers(
            BoxPlot.Whiskers.builder().setExtentMode(BoxPlot.Whiskers.Fraction.of(0.95)).build());

    // make bootstrapped charts
    for (final Map.Entry<File, CorpusScores> e : sortedScores.entrySet()) {
      if (e.getValue().linearQueryScore() > 0) {
        corpusFScoresBoxPlot.addDataset(BoxPlot.Dataset.createCopyingData(e.getKey().getName(),
            checkNotNull(extractScores.apply(checkNotNull(e.getValue())))));
      }
    }
    plotRenderer.renderTo(corpusFScoresBoxPlot.build(), outputFile);
  }
}

enum ExtractFScores implements Function<CorpusScores, double[]> {
  INSTANCE;

  @Override
  public double[] apply(final CorpusScores input) {
    return Doubles
        .toArray(transform(input.queryFPercentiles().get("F1").rawData(),
            Percentifier.INSTANCE));
  }
}

enum ExtractLinearScores implements Function<CorpusScores, double[]> {
  INSTANCE;

  @Override
  public double[] apply(final CorpusScores input) {
    return Doubles.toArray(
        transform(input.linearQueryPercentiles().get("OfficialScore").rawData(),
            Percentifier.INSTANCE));
  }
}

enum Percentifier implements Function<Double, Double> {
  INSTANCE;

  @Override
  public Double apply(Double unscaled) {
    return unscaled * 100.0;
  }
}

@SuppressWarnings("ImmutableEnumChecker")
enum ExtractCorpusScores implements Function<File, CorpusScores> {
  INSTANCE;

  final JacksonSerializer deserializer = JacksonSerializer.builder().forJson().build();

  @Nullable
  @Override
  public CorpusScores apply(@Nullable final File scoreDir) {
    checkNotNull(scoreDir);
    try {
      final File fScores = new File(new File(scoreDir, "fScores"), "AggregateF.txt.json");
      final FMeasureCounts corpusFScores = (FMeasureCounts) deserializer.deserializeFrom(
          Files.asByteSource(fScores));

      final File fPercentiles =
          new File(new File(new File(scoreDir, "fScores"), "bootstrapData"),
              "Present.percentile.json");
      final BootstrapWriter.SerializedBootstrapResults fPercentilesResults =
          (BootstrapWriter.SerializedBootstrapResults) deserializer
              .deserializeFrom(Files.asByteSource(fPercentiles));

      final File linearScoreFile = new File(new File(scoreDir, "linearScore"), "linearScore.txt");
      final Double linearScore = Double.parseDouble(checkNotNull(asCharSource(linearScoreFile,
          UTF_8).readFirstLine()).trim());

      final File linearScorePercentiles =
          new File(new File(new File(scoreDir, "linearBootstrapScore"), "bootstrapData"),
              "Aggregate.percentile.json");
      final BootstrapWriter.SerializedBootstrapResults linearPercentilesResults =
          (BootstrapWriter.SerializedBootstrapResults) deserializer
              .deserializeFrom(Files.asByteSource(linearScorePercentiles));

      return new CorpusScores.Builder().queryFMeasures(corpusFScores)
          .queryFPercentiles(fPercentilesResults.percentilesMap()).linearQueryScore(linearScore)
          .linearQueryPercentiles(linearPercentilesResults.percentilesMap()).build();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}

@TextGroupImmutable
@Functional
@Value.Immutable
abstract class CorpusScores {

  private static final Logger log = LoggerFactory.getLogger(CorpusScores.class);

  abstract FMeasureCounts queryFMeasures();

  abstract ImmutableMap<String, PercentileComputer.Percentiles> queryFPercentiles();

  abstract double linearQueryScore();

  abstract ImmutableMap<String, PercentileComputer.Percentiles> linearQueryPercentiles();

  final String queryFAsCSVLine() {
    return FluentIterable.from(ImmutableList.of(
        queryFPercentiles().get("F1").percentile(0.05).or(Double.NaN) * 100.0,
        queryFPercentiles().get("F1").median().or(Double.NaN) * 100.0,
        queryFPercentiles().get("F1").percentile(0.95).or(Double.NaN) * 100.0,
        100.0 * queryFMeasures().precision(),
        100.0 * queryFMeasures().recall(),
        100.0 * queryFMeasures().F1(),
        queryFMeasures().truePositives(),
        queryFMeasures().falseNegatives(),
        queryFMeasures().falsePositives(),
        linearQueryPercentiles().get("OfficialScore").percentile(0.05).or(Double.NaN) * 100.0,
        linearQueryPercentiles().get("OfficialScore").median().or(Double.NaN) * 100.0,
        linearQueryPercentiles().get("OfficialScore").percentile(0.95).or(Double.NaN) * 100.0,
        linearQueryScore() * 100.0))
        .transform(Prettify.INSTANCE)
        .join(Joiner.on(", "));
  }


  private static final ImmutableList<String> HEADER_STRINGS = ImmutableList
      .of("CorpusFScore 5%", "CorpusFScore 50%", "CorpusFScore 95%", "CorpusP", "CorpusR",
          "CorpusF1", "True Positives", "False Negatives", "False Positives", "OfficialScore 5%",
          "OfficialScore 50%", "OfficialScore 95%", "OfficialScore");

  public static String queryCSVHeader(String suffix) {
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


  public static class Builder extends ImmutableCorpusScores.Builder {

  }
}
