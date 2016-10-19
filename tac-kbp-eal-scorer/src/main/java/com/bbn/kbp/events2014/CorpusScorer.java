package com.bbn.kbp.events2014;

import com.bbn.bue.common.Inspector;
import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.evaluation.AggregateBinaryFScoresInspector;
import com.bbn.bue.common.evaluation.Alignment;
import com.bbn.bue.common.evaluation.BinaryConfusionMatrixBootstrapStrategy;
import com.bbn.bue.common.evaluation.BinaryFScoreBootstrapStrategy;
import com.bbn.bue.common.evaluation.BootstrapInspector;
import com.bbn.bue.common.evaluation.BootstrapWriter;
import com.bbn.bue.common.evaluation.BrokenDownLinearScoreAggregator;
import com.bbn.bue.common.evaluation.BrokenDownPRFAggregator;
import com.bbn.bue.common.evaluation.EquivalenceBasedProvenancedAligner;
import com.bbn.bue.common.evaluation.EvalPair;
import com.bbn.bue.common.evaluation.EvaluationConstants;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.evaluation.InspectionNode;
import com.bbn.bue.common.evaluation.InspectorTreeDSL;
import com.bbn.bue.common.evaluation.InspectorTreeNode;
import com.bbn.bue.common.evaluation.ProvenancedAlignment;
import com.bbn.bue.common.evaluation.SummaryConfusionMatrices;
import com.bbn.bue.common.evaluation.SummaryConfusionMatrix;
import com.bbn.bue.common.math.PercentileComputer;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.DefaultCorpusQueryLoader;
import com.bbn.kbp.events2014.io.SingleFileQueryAssessmentsLoader;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.math.DoubleMath;
import com.google.common.reflect.TypeToken;

import org.immutables.func.Functional;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.bbn.bue.common.evaluation.InspectorTreeDSL.inspect;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformBoth;
import static com.bbn.kbp.events2014.QueryDocMatchFunctions.queryID;
import static com.google.common.base.Functions.compose;
import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.getFirst;

public final class CorpusScorer {

  private static final Logger log = LoggerFactory.getLogger(CorpusScorer.class);

  private CorpusScorer() {
    throw new UnsupportedOperationException();
  }

  public static void main(String[] argv) {
    // we wrap the main method in this way to
    // ensure a non-zero return value on failure
    try {
      final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
      trueMain(params);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  static void trueMain(Parameters params) throws IOException {
    log.info(params.dump());
    final File outputDir = params.getCreatableDirectory("com.bbn.tac.eal.outputDir");
    final File queryFile = params.getExistingFile("com.bbn.tac.eal.queryFile");
    final File queryResponseAssessmentsFile =
        params.getExistingFile("com.bbn.tac.eal.queryAssessmentsFile");
    final CorpusQueryAssessments queryAssessments =
        SingleFileQueryAssessmentsLoader.create().loadFrom(
            Files.asCharSource(queryResponseAssessmentsFile, Charsets.UTF_8));
    final CorpusQuerySet2016 queries = DefaultCorpusQueryLoader.create().loadQueries(
        Files.asCharSource(queryFile, Charsets.UTF_8));
    final boolean ignoreJustifications = params.getBoolean("com.bbn.tac.eal.ignoreJustifications");
    final boolean allowUnassessed = params.getBoolean("com.bbn.tac.eal.allowUnassessed");
    final CorpusQueryAssessments justifiedForScoring;
    if (ignoreJustifications) {
      justifiedForScoring = queryAssessments.withNeutralizedJustifications();
    } else {
      justifiedForScoring = queryAssessments;
    }
    final CorpusQueryAssessments assessedForScoring;
    if(allowUnassessed) {
      assessedForScoring = justifiedForScoring.filterForAssessment(ImmutableSet.copyOf(QueryAssessment2016.values()));
    } else {
      assessedForScoring = justifiedForScoring;
    }
    log.info("Scoring output will be written to {}", outputDir);

    final ImmutableSet<Symbol> systemsToScore =
        ImmutableSet.copyOf(params.getSymbolSet("com.bbn.tac.eal.systemsToScore"));
    log.info("loaded {} assessed queries", justifiedForScoring.assessments().size());
    for (final Symbol system : systemsToScore) {
      log.info("Processing {}", system);
      scoreJustAssessments(queries, assessedForScoring, system,
          new File(outputDir, system.asString()));
    }
  }

  private static void scoreJustAssessments(final CorpusQuerySet2016 queries,
      final CorpusQueryAssessments queryAssessments, final Symbol systemToScore,
      final File outputDir) throws IOException {
    final InspectionNode<EvalPair<CorpusQueryAssessments, CorpusQueryAssessments>>
        inputAssessments = InspectorTreeDSL.pairedInput(new TypeToken<CorpusQueryAssessments>() {
    });

    final InspectorTreeNode<EvalPair<Set<QueryDocMatch>, Set<QueryDocMatch>>> inputSets =
        transformBoth(inputAssessments, QueryDocMatchFromAssessments.INSTANCE);

    final Inspector<EvalPair<Set<QueryDocMatch>, Set<QueryDocMatch>>> unassessedInspector =
        ErrorIfUnassessed.INSTANCE;
    inspect(inputSets).with(unassessedInspector);

    setUpAssessedScoring(outputDir, inputSets);

    for (final CorpusQuery2016 query : queries) {
      final CorpusQueryAssessments filteredForID = queryAssessments.filterForQuery(query.id());
      final CorpusQueryAssessments correctTestQueries =
          filteredForID.filterForAssessment(ImmutableSet.of(QueryAssessment2016.CORRECT));
      final CorpusQueryAssessments systemResults = filteredForID.filterForSystem(systemToScore);
      log.info("Answer key for {} has {} correct answers, \"{}\" has {}", query.id(),
          correctTestQueries.assessments().size(), systemToScore,
          systemResults.assessments().size());
      checkState(systemResults.systemIDs().size() <= 1,
          "Expected zero or one systems but got " + systemResults.systemIDs());
      inputAssessments.inspect(EvalPair.of(correctTestQueries, systemResults));
    }

    inputAssessments.finish();
  }


  private static Function<EvalPair<? extends Iterable<? extends QueryDocMatch>, ? extends Iterable<? extends QueryDocMatch>>, ProvenancedAlignment<QueryDocMatch, QueryDocMatch, QueryDocMatch, QueryDocMatch>>
      EXACT_MATCH_ALIGNER = EquivalenceBasedProvenancedAligner
      .forEquivalenceFunction(Functions.<QueryDocMatch>identity())
      .asFunction();

  private static void setUpAssessedScoring(final File outputDir,
      final InspectorTreeNode<EvalPair<Set<QueryDocMatch>, Set<QueryDocMatch>>> input) {
    final InspectorTreeNode<ProvenancedAlignment<QueryDocMatch, QueryDocMatch, QueryDocMatch, QueryDocMatch>>
        alignment = InspectorTreeDSL.transformed(input, EXACT_MATCH_ALIGNER);

    inspect(alignment)
        .with(AggregateBinaryFScoresInspector.createOutputtingTo("aggregate", outputDir));

    inspect(alignment)
        .with(BootstrapInspector.forStrategy(
            BinaryFScoreBootstrapStrategy.create("Aggregate", outputDir),
            1000, new Random(0)));

    // bootstrapped scores per-query
    inspect(alignment)
        .with(BootstrapInspector.forStrategy(
            BinaryFScoreBootstrapStrategy.createBrokenDownBy("ByQuery",
                compose(toStringFunction(), queryID()), outputDir),
            1000, new Random(0)));

    // linear score (non-bootstrapped)
    final File linearScoreDir = new File(outputDir, "linearScore");
    inspect(alignment)
        .with(LinearScoringInspector.createOutputtingTo(linearScoreDir));

    // officials core (bootstrapped linear score)
    inspect(alignment)
        .with(BootstrapInspector.forStrategy(
            LinearScoreBootstrapStrategy.create("OfficialScore", outputDir),
            1000, new Random(0)));

    // bootstrapped corpus scores
    final BinaryConfusionMatrixBootstrapStrategy<QueryDocMatch> corpusScoreBootstrapStrategy =
        BinaryConfusionMatrixBootstrapStrategy.create(
            compose(toStringFunction(), queryID()),
            ImmutableSet.of(new BrokenDownLinearScoreAggregator.Builder().name("corpusScore")
                .outputDir(new File(outputDir, "corpusScore")).alpha(0.25).build()));
    final BootstrapInspector<Alignment<? extends QueryDocMatch, ? extends QueryDocMatch>, Map<String, FMeasureCounts>>
        corpusScoreWithBootstrapping =
        BootstrapInspector.forStrategy(corpusScoreBootstrapStrategy, 1000, new Random(0));
    inspect(alignment).with(corpusScoreWithBootstrapping);
  }
}

enum ErrorIfUnassessed
    implements Inspector<EvalPair<Set<QueryDocMatch>, Set<QueryDocMatch>>> {
  INSTANCE;

  @Override
  public void inspect(final EvalPair<Set<QueryDocMatch>, Set<QueryDocMatch>> inputPair) {
    if (FluentIterable.from(concat(inputPair.key(), inputPair.test()))
        .transform(QueryDocMatchFunctions.assessment()).toSet()
        .contains(QueryAssessment2016.UNASSESSED)) {
      throw new TACKBPEALException("Found unassessed responses in input!");
    }
  }

  @Override
  public void finish() throws IOException {

  }
}

/**
 * The match of a query against a document.
 */
@Value.Immutable
@Functional
@TextGroupPublicImmutable
abstract class _QueryDocMatch {

  @Value.Parameter
  public abstract Symbol queryID();

  @Value.Parameter
  public abstract Symbol docID();

  @Value.Parameter
  public abstract QueryAssessment2016 assessment();
}

enum QueryDocMatchFromAssessments implements Function<CorpusQueryAssessments, Set<QueryDocMatch>> {
  INSTANCE;
  @Override
  public Set<QueryDocMatch> apply(final CorpusQueryAssessments assessments) {
    final ImmutableSet.Builder<QueryDocMatch> ret = ImmutableSet.builder();
    for(final QueryResponse2016 queryResponse2016: assessments.queryReponses()) {
      ret.add(QueryDocMatch.of(queryResponse2016.queryID(), queryResponse2016.docID(),
          assessments.assessments().get(queryResponse2016)));
    }
    return ret.build();
  }
}

// This and the very similar ArgumentScoringInspector should get refactored together someday
final class LinearScoringInspector implements
    Inspector<ProvenancedAlignment<QueryDocMatch, QueryDocMatch, QueryDocMatch, QueryDocMatch>> {
  private static final Logger log = LoggerFactory.getLogger(LinearScoringInspector.class);

  // gamma as defined by the 2016 task guidelines.
  // http://www.nist.gov/tac/2016/KBP/Event/Argument/guidelines/TAC_2016.v24.pdf
  private static final double gamma = 0.25;
  private final File outputDir;

  final ImmutableMap.Builder<Symbol, Integer> truePositives = ImmutableMap.builder();
  final ImmutableMap.Builder<Symbol, Integer> falsePositives = ImmutableMap.builder();
  final ImmutableMap.Builder<Symbol, Integer> falseNegatives = ImmutableMap.builder();
  final ImmutableMap.Builder<Symbol, Double> scores = ImmutableMap.builder();

  private LinearScoringInspector(final File outputDir) {
    this.outputDir = outputDir;
  }

  public static LinearScoringInspector createOutputtingTo(final File outputDir) {
    return new LinearScoringInspector(outputDir);
  }

  @Override
  public void inspect(
      final ProvenancedAlignment<QueryDocMatch, QueryDocMatch, QueryDocMatch, QueryDocMatch> evalPair) {
    // left is ERE, right is system output.
    final Iterable<QueryDocMatch> args =
        concat(evalPair.allLeftItems(), evalPair.allRightItems());
    if (Iterables.isEmpty(args)) {
      log.warn("Got a query with no matches in key");
      return;
    }

    final ImmutableSet<Symbol> queryIds = FluentIterable.from(args).transform(queryID()).toSet();
    if (queryIds.size() > 1) {
      throw new TACKBPEALException("All query matches being compared must be from the same query "
          + "but got " + queryID());
    }

    int queryTPs = evalPair.leftAligned().size();
    int queryFPs = evalPair.rightUnaligned().size();
    int queryFNs = evalPair.leftUnaligned().size();

    final Symbol queryID = checkNotNull(getFirst(args, null)).queryID();
    checkArgument(evalPair.leftAligned().equals(evalPair.rightAligned()));
    double score = computeLinearScore(queryTPs, queryFPs, queryFNs);
    truePositives.put(queryID, queryTPs);
    falsePositives.put(queryID, queryFPs);
    falseNegatives.put(queryID, queryFNs);
    scores.put(queryID, score);
  }

  static double computeLinearScore(final double queryTPs, final double queryFPs, final double queryFNs) {
    double scoreDenom = Math.max(queryTPs + queryFNs, 1);
    // scores are clipped at 0.
    return Math.max((queryTPs - gamma * queryFPs)/scoreDenom, 0);
  }

  private static final String SCORE_PATTERN = "TP: %d, FP: %d, FN: %d, Score: %f\n";
  @Override
  public void finish() throws IOException {
    final ImmutableMap<Symbol, Double> scores = this.scores.build();
    final ImmutableMap<Symbol, Integer> falsePositives = this.falsePositives.build();
    final ImmutableMap<Symbol, Integer> truePositives = this.truePositives.build();
    final ImmutableMap<Symbol, Integer> falseNegatives = this.falseNegatives.build();

    // see guidelines section 7.3.1.1.3 for aggregating rules:
    outputDir.mkdirs();
    final double meanScore = scores.isEmpty()?Double.NaN:DoubleMath.mean(scores.values());
    Files.asCharSink(new File(outputDir, "linearScore.txt"), Charsets.UTF_8)
        .write(Double.toString(meanScore));

    for (final Symbol queryId : scores.keySet()) {
      final File queryDir = new File(outputDir, queryId.asString());
      queryDir.mkdirs();
      final File queryScoreFile = new File(queryDir, "score.txt");
      // avoid dividing by zero
      final double normalizer = Math.max(truePositives.get(queryId) + falseNegatives.get(queryId), 1);
      // see guidelines referenced above
      // pretends that the corpus is a single document
      Files.asCharSink(queryScoreFile, Charsets.UTF_8).write(String
          .format(SCORE_PATTERN, truePositives.get(queryId), falsePositives.get(queryId),
              falseNegatives.get(queryId), 100 * scores.get(queryId) / normalizer));
    }
  }
}

final class LinearScoreBootstrapStrategy<T> implements
    BootstrapInspector.BootstrapStrategy<Alignment<? extends T, ? extends T>, SummaryConfusionMatrix> {
  private final File outputDir;
  private final String name;

  private LinearScoreBootstrapStrategy(String name, File outputDir) {
    this.name = (String) Preconditions.checkNotNull(name);
    this.outputDir = (File)Preconditions.checkNotNull(outputDir);
  }

  public static <T> LinearScoreBootstrapStrategy<T> create(String name, File outputDir) {
    return new LinearScoreBootstrapStrategy<>(name, outputDir);
  }

  public BootstrapInspector.ObservationSummarizer<Alignment<? extends T, ? extends T>, SummaryConfusionMatrix> createObservationSummarizer() {
    return new BootstrapInspector.ObservationSummarizer<Alignment<? extends T, ? extends T>, SummaryConfusionMatrix>() {
      public SummaryConfusionMatrix summarizeObservation(Alignment<? extends T, ? extends T> alignment) {
        return LinearScoreBootstrapStrategy.this.confusionMatrixForAlignment(alignment);
      }
    };
  }

  private SummaryConfusionMatrix confusionMatrixForAlignment(Alignment<? extends T, ? extends T> alignment) {
    com.bbn.bue.common.evaluation.SummaryConfusionMatrices.Builder summaryConfusionMatrixB = SummaryConfusionMatrices
        .builder();
    summaryConfusionMatrixB.accumulatePredictedGold(EvaluationConstants.PRESENT, EvaluationConstants.PRESENT, (double)alignment.rightAligned().size());
    summaryConfusionMatrixB.accumulatePredictedGold(EvaluationConstants.ABSENT, EvaluationConstants.PRESENT, (double)alignment.leftUnaligned().size());
    summaryConfusionMatrixB.accumulatePredictedGold(EvaluationConstants.PRESENT, EvaluationConstants.ABSENT, (double)alignment.rightUnaligned().size());
    return summaryConfusionMatrixB.build();
  }

  public Collection<BootstrapInspector.SummaryAggregator<SummaryConfusionMatrix>> createSummaryAggregators() {
    return ImmutableList.<BootstrapInspector.SummaryAggregator<SummaryConfusionMatrix>>of(new Aggregator());
  }

  public BootstrapInspector.SummaryAggregator<Map<String, SummaryConfusionMatrix>> prfAggregator() {
    return BrokenDownPRFAggregator.create(this.name, this.outputDir);
  }

  private final class Aggregator implements BootstrapInspector.SummaryAggregator<SummaryConfusionMatrix> {
    private final ImmutableList.Builder<Double> linearScores = ImmutableList.builder();

    private static final String AGGREGATE = "Aggregate";
    private static final String OFFICIAL_SCORE = "OfficialScore";
    private final BootstrapWriter writer = new BootstrapWriter.Builder()
        .measures(ImmutableList.of(OFFICIAL_SCORE))
        .percentilesToPrint(ImmutableList.of(0.025, 0.05, 0.25, 0.5, 0.75, 0.95, 0.975))
        .percentileComputer(PercentileComputer.nistPercentileComputer())
        .build();

    @Override
    public void observeSample(final Collection<SummaryConfusionMatrix> collection) {
      final List<Double> perQueryScores = new ArrayList<>();

      for (final SummaryConfusionMatrix summaryConfusionMatrix : collection) {
        final double queryTPs = summaryConfusionMatrix.cell(EvaluationConstants.PRESENT, EvaluationConstants.PRESENT);
        final double queryFPs = summaryConfusionMatrix.cell(EvaluationConstants.PRESENT, EvaluationConstants.ABSENT);
        final double queryFNs = summaryConfusionMatrix.cell(EvaluationConstants.ABSENT, EvaluationConstants.PRESENT);

        perQueryScores.add(LinearScoringInspector.computeLinearScore(queryTPs, queryFPs, queryFNs));
      }

      linearScores.add(perQueryScores.isEmpty()?0.0:DoubleMath.mean(perQueryScores));
    }

    @Override
    public void finish() throws IOException {
      final ImmutableListMultimap<String, Double> data =
          ImmutableListMultimap.<String, Double>builder()
              .putAll(AGGREGATE, linearScores.build()).build();

      writer.writeBootstrapData(name,
          ImmutableMap.of(OFFICIAL_SCORE, data),
          outputDir);
    }
  }
}

