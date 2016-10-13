package com.bbn.kbp.events2014;

import com.bbn.bue.common.Inspector;
import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.TextGroupPackageImmutable;
import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.evaluation.AggregateBinaryFScoresInspector;
import com.bbn.bue.common.evaluation.Alignment;
import com.bbn.bue.common.evaluation.BinaryFScoreBootstrapStrategy;
import com.bbn.bue.common.evaluation.BootstrapInspector;
import com.bbn.bue.common.evaluation.BootstrapWriter;
import com.bbn.bue.common.evaluation.BrokenDownPRFAggregator;
import com.bbn.bue.common.evaluation.EquivalenceBasedProvenancedAligner;
import com.bbn.bue.common.evaluation.EvalPair;
import com.bbn.bue.common.evaluation.EvaluationConstants;
import com.bbn.bue.common.evaluation.InspectionNode;
import com.bbn.bue.common.evaluation.InspectorTreeDSL;
import com.bbn.bue.common.evaluation.InspectorTreeNode;
import com.bbn.bue.common.evaluation.ProvenancedAlignment;
import com.bbn.bue.common.evaluation.SummaryConfusionMatrices;
import com.bbn.bue.common.evaluation.SummaryConfusionMatrix;
import com.bbn.bue.common.math.PercentileComputer;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.TACException;
import com.bbn.kbp.events2014.io.DefaultCorpusQueryLoader;
import com.bbn.kbp.events2014.io.SingleFileQueryAssessmentsLoader;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;

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
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.io.CharSink;
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
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformRight;
import static com.bbn.kbp.events2014.QueryDocMatchFunctions.queryID;
import static com.bbn.kbp.events2014.ResponseFunctions.predicateJustifications;
import static com.google.common.base.Functions.compose;
import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
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

    final ImmutableMap<String, SystemOutputStore2016> systemOutputsByName =
        loadSystemOutputs(params);

    log.info("Scoring output will be written to {}", outputDir);

    for (final SystemOutputStore2016 systemOutputStore : systemOutputsByName.values()) {
      score(queries, queryAssessments, systemOutputStore,
          QueryResponseFromERE.queryExecutorFromParamsFor2016(params),
          params.getOptionalBoolean("com.bbn.tac.eal.allowUnassessed").or(false),
          params.getOptionalBoolean("com.bbn.tac.eal.ignoreJustifications").or(false),
          new File(outputDir, systemOutputStore.systemID().asString()));
      systemOutputStore.close();
    }
  }

  private static void score(final CorpusQuerySet2016 queries,
      final CorpusQueryAssessments queryAssessments,
      final SystemOutputStore2016 systemOutputStore,
      final CorpusQueryExecutor2016 queryExecutor,
      boolean allowUnassessed,
      boolean ignoreJustifications,
      final File outputDir) throws IOException {
    final TypeToken<Set<QueryDocMatch>> setOfQueryMatches = new TypeToken<Set<QueryDocMatch>>() {
    };
    final InspectionNode<EvalPair<Set<QueryDocMatch>, SystemOutputMatches>> input =
        InspectorTreeDSL.pairedInput(setOfQueryMatches, new TypeToken<SystemOutputMatches>() { });
    setUpScoring(input, allowUnassessed, outputDir);

    final CorrectMatchesFromAssessmentsExtractor matchesFromAssessmentsExtractor =
        new CorrectMatchesFromAssessmentsExtractor();
    final QueryResponsesFromSystemOutputExtractor matchesFromSystemOutputExtractor =
        QueryResponsesFromSystemOutputExtractor.builder()
          .corpusQueryAssessments(queryAssessments)
          .queryExecutor(queryExecutor)
          .ignoreJustifications(ignoreJustifications)
          .build();

    for (final CorpusQuery2016 query : queries) {
      final Set<QueryDocMatch> correctMatches = matchesFromAssessmentsExtractor
          .extractCorrectMatches(query, queryAssessments);
      final SystemOutputMatches systemMatches =
          matchesFromSystemOutputExtractor.extractMatches(query, systemOutputStore);
      log.info("For query {}, {} key matches, {} assessed and {} unassessed system matches", query.id(),
          correctMatches.size(), systemMatches.assessedMatches().size(),
          systemMatches.unassessedMatches().size());
      input.inspect(EvalPair.of(correctMatches, systemMatches));
    }

    // trigger scoring network to do final aggregated output
    input.finish();
  }

  private static Function<EvalPair<? extends Iterable<? extends QueryDocMatch>, ? extends Iterable<? extends QueryDocMatch>>, ProvenancedAlignment<QueryDocMatch, QueryDocMatch, QueryDocMatch, QueryDocMatch>>
      EXACT_MATCH_ALIGNER = EquivalenceBasedProvenancedAligner
      .forEquivalenceFunction(Functions.<QueryDocMatch>identity())
      .asFunction();

  private static void setUpScoring(
      final InspectionNode<EvalPair<Set<QueryDocMatch>, SystemOutputMatches>> rawInput,
      boolean allowUnassessed,
      final File outputDir) {

    // pull out unassessed matches for special handling
    final Inspector<EvalPair<Set<QueryDocMatch>, SystemOutputMatches>> unassessedInspector;
    if (allowUnassessed) {
      unassessedInspector = new LogUnassessed(Files.asCharSink(new File(outputDir, "unassessed.txt"),
          Charsets.UTF_8));
    } else {
      unassessedInspector = new ErrorOnUnassessed();
    }
    inspect(rawInput).with(unassessedInspector);

    // strip off unassessed matches for other scoring
    final InspectorTreeNode<EvalPair<Set<QueryDocMatch>, ImmutableSet<QueryDocMatch>>>
        input = transformRight(rawInput, SystemOutputMatchesFunctions.assessedMatches());
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

  }

  private static final String MULTIPLE_SYSTEMS_PARAM = "com.bbn.tac.eal.systemOutputsDir";
  private static final String SINGLE_SYSTEMS_PARAM = "com.bbn.tac.eal.systemOutputDir";

  /**
   * We can score one or many systems at a time, depending on the parameters
   */
  private static ImmutableMap<String, SystemOutputStore2016> loadSystemOutputs(
      final Parameters params) throws IOException {
    params.assertExactlyOneDefined(SINGLE_SYSTEMS_PARAM, MULTIPLE_SYSTEMS_PARAM);
    final ImmutableMap.Builder<String, SystemOutputStore2016> ret = ImmutableMap.builder();
    if (params.isPresent(MULTIPLE_SYSTEMS_PARAM)) {
      final File systemOutputsDir = params.getExistingDirectory(MULTIPLE_SYSTEMS_PARAM);
      for (final File f : systemOutputsDir.listFiles()) {
        if (f.isDirectory()) {
          ret.put(f.getName(), KBPEA2016OutputLayout.get().open(f));
        }
      }
    } else if (params.isPresent(SINGLE_SYSTEMS_PARAM)) {
      final File singleSystemOutputDir = params.getExistingDirectory(SINGLE_SYSTEMS_PARAM);
      ret.put(singleSystemOutputDir.getName(),
          KBPEA2016OutputLayout.get().open(singleSystemOutputDir));
    } else {
      throw new RuntimeException("Can't happen");
    }
    return ret.build();
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

/**
 * Gets all matches of queries against documents by any system where some system's match was
 * assessed as correct.
 */
final class CorrectMatchesFromAssessmentsExtractor {

  public final Set<QueryDocMatch> extractCorrectMatches(final CorpusQuery2016 query,
      final CorpusQueryAssessments input) {
    checkNotNull(input);
    final ImmutableSet.Builder<QueryDocMatch> ret = ImmutableSet.builder();
    for (final Map.Entry<QueryResponse2016, QueryAssessment2016> e
        : input.assessments().entrySet()) {
      final QueryResponse2016 queryResponse = e.getKey();
      final QueryAssessment2016 assessment = e.getValue();

      if (query.id().equalTo(queryResponse.queryID())) {
        checkArgument(!assessment.equals(QueryAssessment2016.UNASSESSED),
            "Response %s for query ID {} is not assessed", queryResponse,
            queryResponse.queryID());
        if (assessment.equals(QueryAssessment2016.CORRECT)) {
          ret.add(QueryDocMatch.of(queryResponse.queryID(), queryResponse.docID(), assessment));
        }
      }
    }
    return ret.build();
  }
}

@TextGroupPublicImmutable
@Value.Immutable
abstract class _QueryResponsesFromSystemOutputExtractor {

  @Value.Parameter
  public abstract CorpusQueryAssessments corpusQueryAssessments();

  @Value.Parameter
  public abstract CorpusQueryExecutor2016 queryExecutor();

  @Value.Default
  public boolean ignoreJustifications() {
    return false;
  }

  public final SystemOutputMatches extractMatches(final CorpusQuery2016 query,
      final SystemOutputStore2016 input) {
    checkNotNull(input);
    final ImmutableSet.Builder<QueryDocMatch> assessedMatches = ImmutableSet.builder();
    final ImmutableSet.Builder<UnassessedMatch> unassessedMatches = ImmutableSet.builder();

    // if we have been requested to ignore justifications, do so for the key
    final CorpusQueryAssessments assessmentsToUse;
    if (ignoreJustifications()) {
      assessmentsToUse = corpusQueryAssessments().withNeutralizedJustifications();
    } else {
      assessmentsToUse = corpusQueryAssessments();
    }

    try {
      for (final DocEventFrameReference match : queryExecutor().queryEventFrames(input, query)) {
        final ResponseLinking linkingForMatchedDocument = input.read(match.docID()).linking();
        if (linkingForMatchedDocument.responseSetIds().isPresent()) {
          final QueryResponse2016 queryResponse =
              QueryResponse2016.of(query.id(), match.docID(),
                  mergePJs(linkingForMatchedDocument.responseSetIds().get()
                      .get(match.eventFrameID())));

          // if we have been requested to ignore justifications, strip the justification from
          // our query response
          final QueryResponse2016 queryResponseToScore;
          if (ignoreJustifications()) {
            queryResponseToScore = queryResponse.withNeutralizedJustification();
          } else {
            queryResponseToScore = queryResponse;
          }

          final QueryAssessment2016 assessment =
              assessmentsToUse.assessments().get(queryResponseToScore);

          if (assessment != null) {
            final QueryDocMatch docMatch = QueryDocMatch.of(query.id(), match.docID(), assessment);
            assessedMatches.add(docMatch);
          } else {
            unassessedMatches.add(UnassessedMatch.of(query.id(), match.docID()));
          }
        } else {
          throw new TACException("Linking for document " + match.docID() + " lacks IDs");
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return SystemOutputMatches.of(assessedMatches.build(), unassessedMatches.build());
  }

  /**
   * Coalesce the predicate justifications of all responses, combining overlapping spans.
   */
  private Set<CharOffsetSpan> mergePJs(final ResponseSet responses) {
    final RangeSet<CharOffset> pjRanges = TreeRangeSet.create();

    final FluentIterable<CharOffsetSpan> pjsOfAnyLinkedResponses = FluentIterable.from(responses)
        .transformAndConcat(predicateJustifications());

    for (final CharOffsetSpan pjSpan : pjsOfAnyLinkedResponses) {
      pjRanges.add(pjSpan.asCharOffsetRange().asRange());
    }

    final ImmutableSet.Builder<CharOffsetSpan> ret = ImmutableSet.builder();
    for (final Range<CharOffset> mergedPJ : pjRanges.asRanges()) {
      ret.add(CharOffsetSpan.fromOffsetsOnly(mergedPJ.lowerEndpoint().asInt(),
          mergedPJ.upperEndpoint().asInt()));
    }
    return ret.build();
  }
}

@TextGroupPackageImmutable
@Value.Immutable
@Functional
abstract class _SystemOutputMatches {
  @Value.Parameter
  public abstract ImmutableSet<QueryDocMatch> assessedMatches();
  @Value.Parameter
  public abstract ImmutableSet<UnassessedMatch> unassessedMatches();
}

@TextGroupPackageImmutable
@Value.Immutable
@Functional
abstract class _UnassessedMatch {
  @Value.Parameter
  public abstract Symbol queryID();
  @Value.Parameter
  public abstract Symbol docID();
}

final class ErrorOnUnassessed implements Inspector<EvalPair<Set<QueryDocMatch>, SystemOutputMatches>> {
  @Override
  public void inspect(
      final EvalPair<Set<QueryDocMatch>, SystemOutputMatches> input) {
    if (!input.test().unassessedMatches().isEmpty()) {
      throw new TACKBPEALException("The following document matches are unassessed: "
        + input.test().unassessedMatches());
    }
  }

  @Override
  public void finish() throws IOException {

  }
}

final class LogUnassessed implements Inspector<EvalPair<Set<QueryDocMatch>, SystemOutputMatches>> {
  private final CharSink output;
  private ImmutableSet.Builder<UnassessedMatch> unassessed = ImmutableSet.builder();

  public LogUnassessed(CharSink output) {
    this.output = checkNotNull(output);
  }

  @Override
  public void inspect(
      final EvalPair<Set<QueryDocMatch>, SystemOutputMatches> input) {
    unassessed.addAll(input.test().unassessedMatches());
  }

  @Override
  public void finish() throws IOException {
    output.write(StringUtils.unixNewlineJoiner().join(unassessed.build()));
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
    log.info("Gathering scores for {}", queryID);
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

