package com.bbn.kbp.events;

import com.bbn.bue.common.Finishable;
import com.bbn.bue.common.HasDocID;
import com.bbn.bue.common.Inspector;
import com.bbn.bue.common.IntIDSequence;
import com.bbn.bue.common.TextGroupPackageImmutable;
import com.bbn.bue.common.evaluation.AggregateBinaryFScoresInspector;
import com.bbn.bue.common.evaluation.BinaryConfusionMatrixBootstrapStrategy;
import com.bbn.bue.common.evaluation.BinaryErrorLogger;
import com.bbn.bue.common.evaluation.BootstrapInspector;
import com.bbn.bue.common.evaluation.BrokenDownFMeasureAggregator;
import com.bbn.bue.common.evaluation.BrokenDownLinearScoreAggregator;
import com.bbn.bue.common.evaluation.EquivalenceBasedProvenancedAligner;
import com.bbn.bue.common.evaluation.EvalPair;
import com.bbn.bue.common.evaluation.InspectionNode;
import com.bbn.bue.common.evaluation.InspectorTreeDSL;
import com.bbn.bue.common.evaluation.InspectorTreeNode;
import com.bbn.bue.common.evaluation.ProvenancedAlignment;
import com.bbn.bue.common.evaluation.ScoringEventObserver;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events.ontology.SimpleEventOntologyMapper;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.KBPTIMEXExpression;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.TACKBPEALException;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.transformers.QuoteFilter;
import com.bbn.nlp.corenlp.CoreNLPDocument;
import com.bbn.nlp.corenlp.CoreNLPParseNode;
import com.bbn.nlp.corenlp.CoreNLPXMLLoader;
import com.bbn.nlp.corpora.ere.EREArgument;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.EREEvent;
import com.bbn.nlp.corpora.ere.EREEventMention;
import com.bbn.nlp.corpora.ere.ERELoader;
import com.bbn.nlp.corpora.ere.ERESpan;
import com.bbn.nlp.corpora.ere.LinkRealis;
import com.bbn.nlp.events.HasEventType;
import com.bbn.nlp.parsing.HeadFinders;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;

import org.immutables.func.Functional;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import static com.bbn.bue.common.evaluation.InspectorTreeDSL.inspect;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformBoth;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformLeft;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformRight;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformed;
import static com.bbn.kbp.events.DocLevelEventArgFunctions.eventArgumentType;
import static com.bbn.kbp.events.DocLevelEventArgFunctions.eventType;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Iterables.transform;

/**
 * Scores KBP 2016 event argument output against an ERE gold standard.  Scoring is in terms of
 * (Event Type, Event Role, Entity) tuples. This program is an experimental rough draft and has a
 * number of limitations: <ul> <li>We only handle arguments which are entity mentions; others are
 * ignored according to the ERE structure on the gold side and by filtering out a (currently
 * hardcoded) set of argument roles on the system side.</li> <i>We map system responses to entities
 * by looking for an entity which has a mention which shares the character offsets of the base
 * filler exactly either by itself or by its nominal head (given in ERE).  In the future we may
 * implement more lenient alignment strategies.</i> <li> Currently system responses which fail to
 * align to any entity at all are discarded rather than penalized.</li> </ul>
 */
public final class ScoreKBPAgainstERE {

  private static final Logger log = LoggerFactory.getLogger(ScoreKBPAgainstERE.class);

  private final EREToKBPEventOntologyMapper ontologyMapper;

  private ScoreKBPAgainstERE() {
    throw new UnsupportedOperationException();
  }

  // left over from pre-Guice version
  private final Parameters params;
  private final ImmutableMap<String, ScoringEventObserver<DocLevelEventArg, DocLevelEventArg>>
      scoringEventObservers;
  // we exclude text in quoted regions froms scoring
  private final QuoteFilter quoteFilter;

  @Inject
  ScoreKBPAgainstERE(
      final Parameters params,
      final Map<String, ScoringEventObserver<DocLevelEventArg, DocLevelEventArg>> scoringEventObservers,
      final EREToKBPEventOntologyMapper ontologyMapper,
      final QuoteFilter quoteFilter) {
    this.params = checkNotNull(params);
    // we use a sorted map because the binding of plugins may be non-deterministic
    this.scoringEventObservers = ImmutableSortedMap.copyOf(scoringEventObservers);
    this.ontologyMapper = checkNotNull(ontologyMapper);
    this.quoteFilter = checkNotNull(quoteFilter);
  }

  public void go() throws IOException {
    log.info(params.dump());

    final File outputDir = params.getCreatableDirectory("ereScoringOutput");
    final SystemOutputLayout outputLayout = SystemOutputLayout.ParamParser.fromParamVal(
        params.getString("outputLayout"));

    if (params.isPresent("systemOutputBase")) {
      for (final File dir : params.getExistingDirectory("systemOutputBase").listFiles()) {
        if (dir.isDirectory()) {
          processSystem(outputLayout.open(dir), new File(outputDir, dir.getName()));
        }
      }
    } else {
      processSystem(outputLayout.open(params.getExistingDirectory("systemOutput")),
          outputDir);
    }
  }

  void processSystem(SystemOutputStore outputStore, File outputDir) throws IOException {
    outputDir.mkdirs();

    final ImmutableSet<Symbol> docIDsToScore = ImmutableSet.copyOf(
        FileUtils.loadSymbolList(params.getExistingFile("docIDsToScore")));
    final ImmutableMap<Symbol, File> goldDocIDToFileMap = FileUtils.loadSymbolToFileMap(
        Files.asCharSource(params.getExistingFile("goldDocIDToFileMap"), Charsets.UTF_8));



    final CoreNLPXMLLoader coreNLPXMLLoader =
        CoreNLPXMLLoader.builder(HeadFinders.<CoreNLPParseNode>getEnglishPTBHeadFinder()).build();
    final boolean relaxUsingCORENLP = params.getBoolean("relaxUsingCoreNLP");
    final ImmutableMap<Symbol, File> coreNLPProcessedRawDocs;
    if (relaxUsingCORENLP) {
      log.info("Relaxing scoring using CoreNLP");
      coreNLPProcessedRawDocs = FileUtils.loadSymbolToFileMap(
          Files.asCharSource(params.getExistingFile("coreNLPDocIDMap"), Charsets.UTF_8));
    } else {
      coreNLPProcessedRawDocs = ImmutableMap.of();
    }

    log.info("Scoring over {} documents", docIDsToScore.size());

    // on the gold side we take an ERE document as input
    final TypeToken<EREDocument> inputIsEREDoc = new TypeToken<EREDocument>() {
    };
    // on the test side we take an AnswerKey, but we bundle it with the gold ERE document
    // for use in alignment later
    final TypeToken<EREDocAndResponses> inputIsEREDocAndAnswerKey =
        new TypeToken<EREDocAndResponses>() {
        };

    final InspectionNode<EvalPair<EREDocument, EREDocAndResponses>>
        input = InspectorTreeDSL.pairedInput(inputIsEREDoc, inputIsEREDocAndAnswerKey);

    // these will extract the scoring tuples from the KBP system input and ERE docs, respectively
    // we create these here because we will call their .finish method()s
    // at the end to record some statistics about alignment failures,
    // so we need to keep references to them
    final ResponsesAndLinkingFromKBPExtractor responsesAndLinkingFromKBPExtractor =
        new ResponsesAndLinkingFromKBPExtractor(coreNLPProcessedRawDocs,
            coreNLPXMLLoader, relaxUsingCORENLP, ontologyMapper,
            new File(outputDir, "alignmentFailures"));
    final ResponsesAndLinkingFromEREExtractor responsesAndLinkingFromEREExtractor =
        new ResponsesAndLinkingFromEREExtractor(EREToKBPEventOntologyMapper.create2016Mapping(),
            quoteFilter);

    // this sets it up so that everything fed to input will be scored in various ways
    setupScoring(input, responsesAndLinkingFromKBPExtractor, responsesAndLinkingFromEREExtractor,
        scoringEventObservers.values(), outputDir);

    // we want globally unique IDs here
    final ERELoader loader = ERELoader.builder().prefixDocIDToAllIDs(true).build();

    for (Symbol docID : docIDsToScore) {
      // EvalHack - 2016 dry run contains some files for which Serif spuriously adds this document ID
      docID = Symbol.from(docID.asString().replace("-kbp", ""));
      final File ereFileName = goldDocIDToFileMap.get(docID);
      if (ereFileName == null) {
        throw new RuntimeException("Missing key file for " + docID);
      }
      final EREDocument ereDoc = loader.loadFrom(ereFileName);
      // the LDC provides certain ERE documents with "-kbp" in the name. The -kbp is used by them
      // internally for some form of tracking but doesn't appear to the world, so we remove it.
      if (!ereDoc.getDocId().replace("-kbp", "").equals(docID.asString().replace(".kbp", ""))) {
        log.warn("Fetched document ID {} does not equal stored {}", ereDoc.getDocId(), docID);
      }
      final Iterable<Response>
          responses = filter(outputStore.read(docID).arguments().responses(), BANNED_ROLES_FILTER);
      final ResponseLinking linking =
          ((DocumentSystemOutput2015) outputStore.read(docID)).linking();
      linking.copyWithFilteredResponses(in(ImmutableSet.copyOf(responses)));
      // feed this ERE doc/ KBP output pair to the scoring network
      input.inspect(EvalPair.of(ereDoc, new EREDocAndResponses(ereDoc, responses, linking)));

    }

    // trigger the scoring network to write its summary files
    input.finish();
    // log alignment failures
    responsesAndLinkingFromKBPExtractor.finish();
    responsesAndLinkingFromEREExtractor.finish();
  }

  private static final ImmutableSet<Symbol> BANNED_ROLES =
      SymbolUtils.setFrom("Position", "Fine", "Sentence");
  private static final ImmutableSet<Symbol> ROLES_2016 = SymbolUtils
      .setFrom("Agent", "Artifact", "Attacker", "Audience", "Beneficiary", "Crime", "Destination",
          "Entity", "Giver", "Instrument", "Money", "Origin", "Person", "Place", "Position",
          "Recipient", "Target", "Thing", "Time", "Victim");
  private static final ImmutableSet<Symbol> ALLOWED_ROLES_2016 =
      Sets.difference(ROLES_2016, BANNED_ROLES).immutableCopy();
  private static final ImmutableSet<Symbol> linkableRealis = SymbolUtils.setFrom("Other", "Actual");

  // evaluation hack to make this usable by DerivedQuerySelector2016
  public static final Predicate<Response> BANNED_ROLES_FILTER = new Predicate<Response>() {
    @Override
    public boolean apply(@Nullable final Response response) {
      return ALLOWED_ROLES_2016.contains(response.role());
    }
  };

  private static Function<EvalPair<? extends Iterable<? extends DocLevelEventArg>, ? extends Iterable<? extends DocLevelEventArg>>, ProvenancedAlignment<DocLevelEventArg, DocLevelEventArg, DocLevelEventArg, DocLevelEventArg>>
      EXACT_MATCH_ALIGNER = EquivalenceBasedProvenancedAligner
      .forEquivalenceFunction(Functions.<DocLevelEventArg>identity())
      .asFunction();

  // this sets up a scoring network which is executed on every input
  private static void setupScoring(
      final InspectionNode<EvalPair<EREDocument, EREDocAndResponses>> input,
      final ResponsesAndLinkingFromKBPExtractor responsesAndLinkingFromKBPExtractor,
      final ResponsesAndLinkingFromEREExtractor responsesAndLinkingFromEREExtractor,
      Iterable<? extends ScoringEventObserver<DocLevelEventArg, DocLevelEventArg>> scoringEventObservers,
      final File outputDir) {
    final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>>
        inputAsResponsesAndLinking =
        transformRight(transformLeft(input, responsesAndLinkingFromEREExtractor),
            responsesAndLinkingFromKBPExtractor);
    final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>> filteredFor2016 =
        InspectorTreeDSL.transformBoth(
            inputAsResponsesAndLinking,
            ResponsesAndLinking.filterFunction(ARG_TYPE_IS_ALLOWED_FOR_2016));

    final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>> filteredForLifeDie =
        transformed(filteredFor2016, RestrictLifeInjureToLifeDieEvents.INSTANCE);

    // any timex resolution less specific than the most specific correct resolution for a given
    // argument slot is delete. See 2016 task description Section 5, "temporal arguments" bullet #3
    final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>> filteredForTemporal =
        transformed(filteredForLifeDie, TemporalSpecificityFilter.INSTANCE);

    // set up for event argument scoring in 2015 style
    eventArgumentScoringSetup(filteredForTemporal, scoringEventObservers, outputDir);

    // set up for linking scoring in 2015 style
    linkingScoringSetup(filteredForTemporal, outputDir);
  }

  private static void eventArgumentScoringSetup(
      final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>>
          inputAsResponsesAndLinking,
      Iterable<? extends ScoringEventObserver<DocLevelEventArg, DocLevelEventArg>> scoringEventObservers,
      final File outputDir) {
    final InspectorTreeNode<EvalPair<ImmutableSet<DocLevelEventArg>, ImmutableSet<DocLevelEventArg>>>
        inputAsSetsOfScoringTuples =
        transformBoth(inputAsResponsesAndLinking, ResponsesAndLinkingFunctions.args());
    final InspectorTreeNode<EvalPair<ImmutableSet<DocLevelEventArg>, ImmutableSet<DocLevelEventArg>>>
        inputAsSetsOfRealisNeutralizedTuples =
        transformBoth(inputAsResponsesAndLinking, NeutralizeRealis.INSTANCE);

    argScoringSetup(inputAsSetsOfScoringTuples,
        ImmutableList.<ScoringEventObserver<DocLevelEventArg, DocLevelEventArg>>of(),
        new File(outputDir, "withRealis"));
    // we apply scoring observers only to the realis neutralized version
    argScoringSetup(inputAsSetsOfRealisNeutralizedTuples,
        scoringEventObservers, new File(outputDir, "noRealis"));
  }

  private static void argScoringSetup(
      final InspectorTreeNode<EvalPair<ImmutableSet<DocLevelEventArg>, ImmutableSet<DocLevelEventArg>>> inputAsSetsOfScoringTuples,
      final Iterable<? extends ScoringEventObserver<DocLevelEventArg, DocLevelEventArg>> scoringEventObservers,
      final File outputDir) {
    // require exact match between the system arguments and the key responses
    final InspectorTreeNode<ProvenancedAlignment<DocLevelEventArg, DocLevelEventArg, DocLevelEventArg, DocLevelEventArg>>
        alignmentNode = transformed(inputAsSetsOfScoringTuples, EXACT_MATCH_ALIGNER);

    // overall F score
    final AggregateBinaryFScoresInspector<DocLevelEventArg, DocLevelEventArg>
        scoreAndWriteOverallFScore =
        AggregateBinaryFScoresInspector.createWithScoringObservers("aggregateF.txt", outputDir,
            scoringEventObservers);
    inspect(alignmentNode).with(scoreAndWriteOverallFScore);

    // bootstrapped per-event F-scores
    final BinaryConfusionMatrixBootstrapStrategy<HasEventType> perEventBootstrapStrategy =
        BinaryConfusionMatrixBootstrapStrategy.create(HasEventType.ExtractFunction.INSTANCE,
            ImmutableSet.of(BrokenDownFMeasureAggregator.create("EventType", new File(outputDir, "fScores"))));
    final BootstrapInspector breakdownScoresByEventTypeWithBootstrapping =
        BootstrapInspector.forStrategy(perEventBootstrapStrategy, 1000, new Random(0));
    inspect(alignmentNode).with(breakdownScoresByEventTypeWithBootstrapping);

    // "arg" score with weighted TP/FP
    final ArgumentScoringInspector argScorer =
        ArgumentScoringInspector.createOutputtingTo(outputDir);
    inspect(alignmentNode).with(argScorer);

    // bootstrapped arg scores
    final BinaryConfusionMatrixBootstrapStrategy<HasEventType> argScoreBootstrapStrategy =
        BinaryConfusionMatrixBootstrapStrategy.create(
            Functions.constant("Aggregate"),
            ImmutableSet.of(new BrokenDownLinearScoreAggregator.Builder().name("ArgScore")
                .outputDir(new File(outputDir, "argScores")).alpha(0.25).build()));
    final BootstrapInspector argScoreWithBootstrapping =
        BootstrapInspector.forStrategy(argScoreBootstrapStrategy, 1000, new Random(0));
    inspect(alignmentNode).with(argScoreWithBootstrapping);

    // see By2016TypeGroup's Javadoc for an explanation of this
    final BinaryConfusionMatrixBootstrapStrategy<HasEventType> argScoreByTypeGroupBootstrapStrategy =
        BinaryConfusionMatrixBootstrapStrategy.create(
            By2016TypeGroup.INSTANCE,
            ImmutableSet.of(new BrokenDownLinearScoreAggregator.Builder().name("ArgScore")
                .outputDir(new File(outputDir, "argScoresGrouped")).alpha(0.25).build()));
    final BootstrapInspector argScoreByTypeWithBootstrapping =
        BootstrapInspector.forStrategy(argScoreByTypeGroupBootstrapStrategy, 1000, new Random(0));
    inspect(alignmentNode).with(argScoreByTypeWithBootstrapping);


    // log errors
    final BinaryErrorLogger<HasDocID, HasDocID> logWrongAnswers = BinaryErrorLogger
        .forStringifierAndOutputDir(Functions.<HasDocID>toStringFunction(), outputDir);
    inspect(alignmentNode).with(logWrongAnswers);




  }

  private static void linkingScoringSetup(
      final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>>
          inputAsResponsesAndLinking, final File outputDir) {
    final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>> filteredForRealis =
        transformBoth(inputAsResponsesAndLinking,
            ResponsesAndLinking.filterFunction(REALIS_ALLOWED_FOR_LINKING));
    // withRealis
    {
      final InspectorTreeNode<EvalPair<DocLevelArgLinking, DocLevelArgLinking>>
          linkingNode = transformBoth(filteredForRealis, ResponsesAndLinkingFunctions.linking());

      // we throw out any system responses not found in the key before scoring linking
      final InspectorTreeNode<EvalPair<DocLevelArgLinking, DocLevelArgLinking>>
          filteredNode = transformed(linkingNode, RestrictToLinking.INSTANCE);
      final BootstrapInspector<EvalPair<DocLevelArgLinking, DocLevelArgLinking>, LinkingInspector.LinkingScoreDocRecord>
          linkScoreWithBootstrapping =
          BootstrapInspector.forStrategy(LinkingInspector.createOutputtingTo(new File(outputDir, "withRealis")),
              1000, new Random(0));
      inspect(filteredNode).with(linkScoreWithBootstrapping);
    }
    // without realis
    {
      final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>>
          neutralizedRealis =
          transformBoth(filteredForRealis, transformArgs(LinkingRealisNeutralizer.INSTANCE));
      final InspectorTreeNode<EvalPair<DocLevelArgLinking, DocLevelArgLinking>>
          linkingNode = transformBoth(neutralizedRealis, ResponsesAndLinkingFunctions.linking());
      // we throw out any system responses not found in the key before scoring linking, after neutralizing realis
      final InspectorTreeNode<EvalPair<DocLevelArgLinking, DocLevelArgLinking>>
          filteredNode = transformed(linkingNode, RestrictToLinking.INSTANCE);
      final BootstrapInspector<EvalPair<DocLevelArgLinking, DocLevelArgLinking>, LinkingInspector.LinkingScoreDocRecord>
          linkScoreWithBootstrapping =
          BootstrapInspector.forStrategy(LinkingInspector.createOutputtingTo(new File(outputDir, "noRealis")),
              1000, new Random(0));
      inspect(filteredNode).with(linkScoreWithBootstrapping);
    }
  }

  private static final Predicate<_DocLevelEventArg> ARG_TYPE_IS_ALLOWED_FOR_2016 =
      compose(in(ALLOWED_ROLES_2016), eventArgumentType());
  private static final Predicate<_DocLevelEventArg> REALIS_ALLOWED_FOR_LINKING =
      compose(in(linkableRealis), DocLevelEventArgFunctions.realis());


  private enum RestrictLifeInjureToLifeDieEvents implements
      Function<EvalPair<ResponsesAndLinking, ResponsesAndLinking>, EvalPair<ResponsesAndLinking, ResponsesAndLinking>> {
    INSTANCE;

    final Symbol LifeDie = Symbol.from("Life.Die");

    @Override
    public EvalPair<ResponsesAndLinking, ResponsesAndLinking> apply(
        final EvalPair<ResponsesAndLinking, ResponsesAndLinking> input) {
      // find all Life.Die event arguments
      final ImmutableSet<DocLevelEventArg> keyArgs = ImmutableSet.copyOf(filter(input.key().args(),
          compose(equalTo(LifeDie), eventType())));
      // get all possible candidate Life.Injure event arguments that could be derived from these Life.Die arguments
      final ImmutableSet<DocLevelEventArg> argsToIgnore =
          ImmutableSet.copyOf(transform(keyArgs, LifeDieToLifeInjure.INSTANCE));
      // filter both the ERE and the system input to ignore these derived arguments.
      return EvalPair.of(input.key().filter(not(in(argsToIgnore))),
          input.test().filter(not(in(argsToIgnore))));
    }
  }

  private enum LifeDieToLifeInjure implements Function<DocLevelEventArg, DocLevelEventArg> {
    INSTANCE {

      final Symbol LifeInjure = Symbol.from("Life.Injure");

      @Nullable
      @Override
      public DocLevelEventArg apply(@Nullable final DocLevelEventArg docLevelEventArg) {
        checkNotNull(docLevelEventArg);
        checkArgument(docLevelEventArg.eventType()
            .equalTo(RestrictLifeInjureToLifeDieEvents.INSTANCE.LifeDie));
        return docLevelEventArg.withEventType(LifeInjure);
      }
    }
  }

  private enum TemporalSpecificityFilter implements
      Function<EvalPair<ResponsesAndLinking, ResponsesAndLinking>, EvalPair<ResponsesAndLinking, ResponsesAndLinking>> {
    INSTANCE;

    private static final Symbol TIME = Symbol.from("Time");

    @Override
    public EvalPair<ResponsesAndLinking, ResponsesAndLinking> apply(
        final EvalPair<ResponsesAndLinking, ResponsesAndLinking> input) {
      final ImmutableListMultimap<Symbol, DocLevelEventArg> goldTemporalArgsByEventType =
          FluentIterable.from(input.key().args())
              .filter(compose(equalTo(TIME), eventArgumentType()))
              .index(eventType());
      final ImmutableListMultimap<Symbol, DocLevelEventArg> systemTemporalArgsByEventType =
          FluentIterable.from(input.test().args())
              .filter(compose(equalTo(TIME), eventArgumentType()))
              .index(eventType());

      final ImmutableSet.Builder<DocLevelEventArg> toDeleteB = ImmutableSet.builder();

      final Sets.SetView<Symbol> eventTypesWithTimesInBoth =
          Sets.intersection(goldTemporalArgsByEventType.keySet(),
              systemTemporalArgsByEventType.keySet());

      for (final Symbol eventType : eventTypesWithTimesInBoth) {
        final ImmutableList<DocLevelEventArg> goldTimeArgsOfType =
            goldTemporalArgsByEventType.get(eventType);

        final Set<DocLevelEventArg> lessSpecificVersionsOfGoldTimes = new HashSet<>();
        for (final DocLevelEventArg docLevelEventArg : goldTimeArgsOfType) {
          try {
            final Pattern TIME_PAT = Pattern.compile("Time-(.+)");
            final Matcher m = TIME_PAT.matcher(docLevelEventArg.corefID());
            if (m.matches()) {
              final KBPTIMEXExpression time = KBPTIMEXExpression.parseTIMEX(m.group(1));

              for (final KBPTIMEXExpression lessSpecificTime : time.lessSpecificCompatibleTimes()) {
                lessSpecificVersionsOfGoldTimes.add(docLevelEventArg
                    .withCorefID("Time-" + lessSpecificTime));
              }
            } else {
              throw new KBPTIMEXExpression.KBPTIMEXException("Illegal temporal corefID "
                  + docLevelEventArg.corefID());
            }
          } catch (KBPTIMEXExpression.KBPTIMEXException timexException) {
            log.warn(
                "While applying only-most-specific-temporal rule, encountered an illegal coref ID "
                    + "for temporal argument " + docLevelEventArg.corefID()
                    + " in the LDC key!");
          }
        }

        toDeleteB.addAll(lessSpecificVersionsOfGoldTimes);
      }

      final ImmutableSet<DocLevelEventArg> toDelete = toDeleteB.build();
      final Predicate<DocLevelEventArg> notDeleted = not(in(toDelete));
      if (Sets.filter(input.test().args(), notDeleted).size() < input.test().args().size()) {
        log.info("The following argument were deleted by the temporal specificity rule: {}",
            Sets.filter(input.test().args(), in(toDelete)));
      }
      return EvalPair.of(input.key().filter(notDeleted), input.test().filter(notDeleted));
    }
  }

    private static Function<? super ResponsesAndLinking, ResponsesAndLinking> transformArgs(
      final Function<? super DocLevelEventArg, DocLevelEventArg> transformer) {
    return new Function<ResponsesAndLinking, ResponsesAndLinking>() {
      @Override
      public ResponsesAndLinking apply(final ResponsesAndLinking responsesAndLinking) {
        return responsesAndLinking.transform(transformer);
      }
    };
  }

  private enum LinkingRealisNeutralizer
      implements Function<DocLevelEventArg, DocLevelEventArg> {
    INSTANCE;

    static final Symbol NEUTRALIZED = Symbol.from("neutralized");

    @Override
    public DocLevelEventArg apply(final DocLevelEventArg docLevelEventArg) {
      return docLevelEventArg.withRealis(NEUTRALIZED);
    }
  }

  private enum NeutralizeRealis
      implements Function<ResponsesAndLinking, ImmutableSet<DocLevelEventArg>> {
    INSTANCE;

    static final Symbol NEUTRALIZED = Symbol.from("neutralized");

    @Override
    public ImmutableSet<DocLevelEventArg> apply(final ResponsesAndLinking input) {
      final ImmutableSet.Builder<DocLevelEventArg> ret = ImmutableSet.builder();
      for (final DocLevelEventArg arg : input.args()) {
        ret.add(arg.withRealis(NEUTRALIZED));
      }
      return ret.build();
    }
  }

  private enum RestrictToLinking implements
      Function<EvalPair<DocLevelArgLinking, DocLevelArgLinking>, EvalPair<DocLevelArgLinking, DocLevelArgLinking>> {
    INSTANCE;

    @Override
    public EvalPair<DocLevelArgLinking, DocLevelArgLinking> apply(
        final EvalPair<DocLevelArgLinking, DocLevelArgLinking> input) {
      final DocLevelArgLinking newTest =
          input.test().filterArguments(in(input.key().allArguments()));
      return EvalPair.of(input.key(), newTest);
    }
  }

  private static final class ArgumentScoringInspector implements
      Inspector<ProvenancedAlignment<DocLevelEventArg, DocLevelEventArg, DocLevelEventArg, DocLevelEventArg>> {

    // beta as defined by the EAL task guidelines.
    private static final double beta = 0.25;
    private final File outputDir;
    private double scoreAggregator = 0.0;
    private int aggregateTPs = 0;
    private int aggregateFPs = 0;
    private int aggregateFNs = 0;

    final ImmutableMap.Builder<Symbol, Integer> truePositives = ImmutableMap.builder();
    final ImmutableMap.Builder<Symbol, Integer> falsePositives = ImmutableMap.builder();
    final ImmutableMap.Builder<Symbol, Integer> falseNegatives = ImmutableMap.builder();
    final ImmutableMap.Builder<Symbol, Double> scores = ImmutableMap.builder();

    private ArgumentScoringInspector(final File outputDir) {
      this.outputDir = outputDir;
    }

    public static ArgumentScoringInspector createOutputtingTo(final File outputDir) {
      return new ArgumentScoringInspector(outputDir);
    }

    @Override
    public void inspect(
        final ProvenancedAlignment<DocLevelEventArg, DocLevelEventArg, DocLevelEventArg, DocLevelEventArg> evalPair) {
      // left is ERE, right is system output.
      final Iterable<DocLevelEventArg> args =
          concat(evalPair.allLeftItems(), evalPair.allRightItems());
      if (Iterables.size(args) == 0) {
        log.warn("No output for eval pair {}", evalPair);
        return;
      }
      final Symbol docid = checkNotNull(getFirst(args, null)).docID();
      log.info("Gathering arg scores for {}", docid);
      int docTPs = evalPair.leftAligned().size();
      checkArgument(evalPair.leftAligned().equals(evalPair.rightAligned()));
      this.aggregateTPs += docTPs;
      int docFPs = evalPair.rightUnaligned().size();
      this.aggregateFPs += docFPs;
      // scores are clipped at 0.
      double score = Math.max(docTPs - beta * docFPs, 0);
      int docFNs = evalPair.leftUnaligned().size();
      aggregateFNs += docFNs;
      scoreAggregator += score;
      truePositives.put(docid, docTPs);
      falsePositives.put(docid, docFPs);
      falseNegatives.put(docid, docFNs);
      scores.put(docid, score);
    }

    private static final JacksonSerializer serializer =
        JacksonSerializer.builder().forJson().prettyOutput().build();

    @Override
    public void finish() throws IOException {
      final String scorePattern = "TP: %d, FP: %d, FN: %d, Score: %f\n";
      // see guidelines section 7.3.1.1.4 for aggregating rules:
      // sum over per document contributions, divide by total number of TRFRs in the answer key
      // Math.max is to skip division by zero errors.
      final double overAllArgScore =
          100 * scoreAggregator / Math.max(0.0 + aggregateFNs + aggregateTPs, 1.0);
      final String scoreString =
          String.format(scorePattern, aggregateTPs, aggregateFPs, aggregateFNs, overAllArgScore);
      Files.asCharSink(new File(outputDir, "argScores.txt"), Charsets.UTF_8).write(scoreString);
      final File jsonArgScores = new File(outputDir, "argScore.json");
      serializer.serializeTo(overAllArgScore, Files.asByteSink(jsonArgScores));

      final ImmutableMap<Symbol, Double> scores = this.scores.build();
      final ImmutableMap<Symbol, Integer> falsePositives = this.falsePositives.build();
      final ImmutableMap<Symbol, Integer> truePositives = this.truePositives.build();
      final ImmutableMap<Symbol, Integer> falseNegatives = this.falseNegatives.build();
      for (final Symbol docid : scores.keySet()) {
        final File docDir = new File(outputDir, docid.asString());
        docDir.mkdirs();
        final File docScore = new File(docDir, "argScores.txt");
        // avoid dividing by zero
        final double normalizer = Math.max(truePositives.get(docid) + falseNegatives.get(docid), 1);
        // see guidelines referenced above
        // pretends that the corpus is a single document
        final double normalizedAndScaledArgScore = 100 * scores.get(docid) / normalizer;
        Files.asCharSink(docScore, Charsets.UTF_8).write(String
            .format(scorePattern, truePositives.get(docid), falsePositives.get(docid),
                falseNegatives.get(docid), normalizedAndScaledArgScore));

        final File docJsonArgScores = new File(docDir, "argScores.json");
        serializer.serializeTo(normalizedAndScaledArgScore, Files.asByteSink(docJsonArgScores));
      }
    }
  }

  private enum ERERealisEnum {
    generic,
    other,
    actual,
  }

  private enum ArgumentRealis {
    Generic,
    Actual,
    Other
  }

  private static final class ResponsesAndLinkingFromEREExtractor
      implements Function<EREDocument, ResponsesAndLinking>, Finishable {

    // for tracking things from the answer key discarded due to not being entity mentions
    private final Multiset<String> allGoldArgs = HashMultiset.create();
    private final Multiset<String> discarded = HashMultiset.create();
    private final Set<Symbol> unknownEventTypes = Sets.newHashSet();
    private final Set<Symbol> unknownEventSubtypes = Sets.newHashSet();
    private final Set<Symbol> unknownRoles = Sets.newHashSet();

    private final SimpleEventOntologyMapper mapper;
    private final QuoteFilter quoteFilter;

    private ResponsesAndLinkingFromEREExtractor(final SimpleEventOntologyMapper mapper,
        final QuoteFilter quoteFilter) {
      this.mapper = checkNotNull(mapper);
      this.quoteFilter = checkNotNull(quoteFilter);
    }

    private boolean inQuotedRegion(String docId, ERESpan span) {
      // the kbp replacement is a hack to handle dry run docids having additional tracking information on them sometimes.
      return quoteFilter.isInQuote(Symbol.from(docId.replaceAll("-kbp", "")),
          CharOffsetSpan.of(span.asCharOffsets()));
    }

    @Override
    public ResponsesAndLinking apply(final EREDocument doc) {
      final ImmutableSet.Builder<DocLevelEventArg> ret = ImmutableSet.builder();
      // every event mention argument within a hopper is linked
      final DocLevelArgLinking.Builder linking = DocLevelArgLinking.builder()
          .docID(Symbol.from(doc.getDocId()));
      for (final EREEvent ereEvent : doc.getEvents()) {
        final ScoringEventFrame.Builder eventFrame = ScoringEventFrame.builder();
        boolean addedArg = false;
        for (final EREEventMention ereEventMention : ereEvent.getEventMentions()) {
          // events from quoted regions are invalid
          if (!inQuotedRegion(doc.getDocId(), ereEventMention.getTrigger())) {
            for (final EREArgument ereArgument : ereEventMention.getArguments()) {
              if (!inQuotedRegion(doc.getDocId(), ereArgument.getExtent())) {
                // arguments from quoted regions are invalid
                final Symbol ereEventMentionType = Symbol.from(ereEventMention.getType());
                final Symbol ereEventMentionSubtype = Symbol.from(ereEventMention.getSubtype());
                final Symbol ereArgumentRole = Symbol.from(ereArgument.getRole());
                final ArgumentRealis argumentRealis =
                    getRealis(ereEventMention.getRealis(), ereArgument.getRealis().get());

                boolean skip = false;
                if (!mapper.eventType(ereEventMentionType).isPresent()) {
                  unknownEventTypes.add(ereEventMentionType);
                  skip = true;
                }
                if (!mapper.eventRole(ereArgumentRole).isPresent()) {
                  unknownRoles.add(ereArgumentRole);
                  skip = true;
                }
                if (!mapper.eventSubtype(ereEventMentionSubtype).isPresent()) {
                  unknownEventSubtypes.add(ereEventMentionSubtype);
                  skip = true;
                }
                if (skip) {
                  continue;
                }

                // type.subtype is Response format
                final String typeRoleKey = mapper.eventType(ereEventMentionType).get() +
                    "." + mapper.eventSubtype(ereEventMentionSubtype).get() +
                    "/" + mapper.eventRole(ereArgumentRole).get();
                allGoldArgs.add(typeRoleKey);

                final DocLevelEventArg arg =
                    DocLevelEventArg.builder().docID(Symbol.from(doc.getDocId()))
                        .eventType(Symbol.from(mapper.eventType(ereEventMentionType).get() + "." +
                            mapper.eventSubtype(ereEventMentionSubtype).get()))
                        .eventArgumentType(mapper.eventRole(ereArgumentRole).get())
                        .corefID(ScoringUtils.extractScoringEntity(ereArgument, doc).globalID())
                        .realis(Symbol.from(argumentRealis.name())).build();

                ret.add(arg);
                // ban generic responses from ERE linking.
                if (!arg.realis().asString().equalsIgnoreCase(ERERealisEnum.generic.name())) {
                  eventFrame.addArguments(arg);
                  addedArg = true;
                } else {
                  log.debug("Dropping ERE arg {} from linking in {} due to generic realis", arg,
                      ereEventMention);
                }
              } else {
                log.info("Ignoring ERE event mention argument {} as within a quoted region",
                    ereArgument);
              }
            }
          } else {
            log.info("Ignoring ERE event mention {} as within a quoted region", ereEventMention);
          }
          if (addedArg) {
            linking.addEventFrames(eventFrame.build());
          }
        }
      }
      return ResponsesAndLinking.of(ret.build(), linking.build());
    }

    private ArgumentRealis getRealis(final String ERERealis, final LinkRealis linkRealis) {
      // generic event mention realis overrides everything
      if (ERERealis.equals(ERERealisEnum.generic.name())) {
        return ArgumentRealis.Generic;
      } else {
        // if the argument is realis
        if (linkRealis.equals(LinkRealis.REALIS)) {
          if (ERERealis.equals(ERERealisEnum.other.name())) {
            return ArgumentRealis.Other;
          } else if (ERERealis.equals(ERERealisEnum.actual.name())) {
            return ArgumentRealis.Actual;
          } else {
            throw new RuntimeException(
                "Unknown ERERealis of type " + linkRealis);
          }
        } else {
          // if it's irrealis, override Actual with Other, Other is preserved. Generic is handled above.
          return ArgumentRealis.Other;
        }
      }
    }

    @Override
    public void finish() throws IOException {
      log.info(
          "Of {} gold event arguments, {} were discarded as non-entities",
          allGoldArgs.size(), discarded.size());
      for (final String errKey : discarded.elementSet()) {
        if (discarded.count(errKey) > 0) {
          log.info("Of {} gold {} arguments, {} discarded ",
              +allGoldArgs.count(errKey), errKey, discarded.count(errKey));
        }
      }
      if (!unknownEventTypes.isEmpty()) {
        log.info("The following ERE event types were ignored as outside the ontology: {}",
            SymbolUtils.byStringOrdering().immutableSortedCopy(unknownEventTypes));
      }
      if (!unknownEventSubtypes.isEmpty()) {
        log.info("The following ERE event subtypes were ignored as outside the ontology: {}",
            SymbolUtils.byStringOrdering().immutableSortedCopy(unknownEventSubtypes));
      }
      if (!unknownRoles.isEmpty()) {
        log.info("The following ERE event argument roles were ignored as outside the ontology: {}",
            SymbolUtils.byStringOrdering().immutableSortedCopy(unknownRoles));
      }
    }
  }

  private static final class ResponsesAndLinkingFromKBPExtractor
      implements Function<EREDocAndResponses, ResponsesAndLinking>,
      Finishable {

    // each system item which fails to align to any reference item gets put in its own
    // coreference class, numbered using this sequence
    private IntIDSequence alignmentFailureIDs = IntIDSequence.startingFrom(0);
    private ImmutableSetMultimap.Builder<String, String> mentionAlignmentFailuresB =
        ImmutableSetMultimap.builder();
    private Multiset<String> numResponses = HashMultiset.create();
    private final ImmutableMap<Symbol, File> ereMapping;
    private final CoreNLPXMLLoader coreNLPXMLLoader;
    private final boolean relaxUsingCORENLP;
    private final EREToKBPEventOntologyMapper ontologyMapper;
    private final File outputDir;

    public ResponsesAndLinkingFromKBPExtractor(final Map<Symbol, File> ereMapping,
        final CoreNLPXMLLoader coreNLPXMLLoader, final boolean relaxUsingCORENLP,
        final EREToKBPEventOntologyMapper ontologyMapper,
        File outputDir) {
      this.ereMapping = ImmutableMap.copyOf(ereMapping);
      this.coreNLPXMLLoader = coreNLPXMLLoader;
      this.relaxUsingCORENLP = relaxUsingCORENLP;
      this.ontologyMapper = checkNotNull(ontologyMapper);
      this.outputDir = checkNotNull(outputDir);
    }

    public ResponsesAndLinking apply(final EREDocAndResponses input) {
      final ImmutableSet.Builder<DocLevelEventArg> ret = ImmutableSet.builder();
      final Iterable<Response> responses = input.responses();
      final EREDocument doc = input.ereDoc();
      // Work around LDC document ID inconsistency; -kbp is used internally by the LDC as a form of
      // document tracking. Externally the difference does not matter so we just normalize the ID
      final Symbol ereID = Symbol.from(doc.getDocId().replace("-kbp", ""));
      final Optional<CoreNLPDocument> coreNLPDoc;
      final EREAligner ereAligner;

      try {
        coreNLPDoc = Optional.fromNullable(ereMapping.get(ereID)).isPresent() ? Optional
            .of(coreNLPXMLLoader.loadFrom(ereMapping.get(ereID)))
                                                                              : Optional.<CoreNLPDocument>absent();
        checkState(coreNLPDoc.isPresent() || !relaxUsingCORENLP, "Must have CoreNLP document "
            + "if using Core NLP relaxation");
        ereAligner = EREAligner.create(doc, coreNLPDoc, ontologyMapper);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      final ImmutableMap.Builder<Response, DocLevelEventArg> responseToDocLevelArg =
          ImmutableMap.builder();

      for (final Response response : responses) {
        final DocLevelEventArg res = resolveToERE(doc, ereAligner, response);

        ret.add(res);
        responseToDocLevelArg.put(response, res);
      }
      for (final Response response : input.linking().allResponses()) {
        if (response.realis().equals(KBPRealis.Generic)) {
          throw new TACKBPEALException("Generic Arguments are not allowed in linking");
        }
      }

      return fromResponses(ImmutableSet.copyOf(input.responses()),
          responseToDocLevelArg.build(), input.linking());
    }

    private DocLevelEventArg resolveToERE(final EREDocument doc, final EREAligner ereAligner,
        final Response response) {
      numResponses.add(errKey(response));
      final Symbol realis = Symbol.from(response.realis().name());

      final Optional<ScoringCorefID> alignedCorefIDOpt = ereAligner.argumentForResponse(response);
      if (!alignedCorefIDOpt.isPresent()) {
        log.info("Alignment failed for {}", response);
        mentionAlignmentFailuresB.put(errKey(response), response.toString());
      } else if (alignedCorefIDOpt.get().scoringEntityType()
          .equals(ScoringEntityType.InsufficientEntityLevel)) {
        log.info("Insufficient entity level for {}", response);
      }

      // this increments the alignment failure ID regardless of success or failure, but
      // we don't care
      final ScoringCorefID alignedCorefID = alignedCorefIDOpt.or(
          // in case of alignment failure, we make a pseudo-entity from the CAS offsets
          // it will always be wrong, but will be consistent for the same extent appearing in
          // different event roles
          new ScoringCorefID.Builder().scoringEntityType(ScoringEntityType.AlignmentFailure)
          .withinTypeID(response.canonicalArgument().charOffsetSpan().asCharOffsetRange().toString())
          .build());

      return DocLevelEventArg.builder().docID(Symbol.from(doc.getDocId()))
          .eventType(response.type()).eventArgumentType(response.role())
          .corefID(alignedCorefID.globalID()).realis(realis).build();
    }

    ResponsesAndLinking fromResponses(final ImmutableSet<Response> originalResponses,
        final ImmutableMap<Response, DocLevelEventArg> responseToDocLevelEventArg,
        final ResponseLinking responseLinking) {
      final DocLevelArgLinking.Builder linkingBuilder = DocLevelArgLinking.builder()
          .docID(responseLinking.docID());
      for (final ResponseSet rs : responseLinking.responseSets()) {
        final ScoringEventFrame.Builder eventFrameBuilder = ScoringEventFrame.builder();
        boolean addedArg = false;
        for (final Response response : rs) {
          if (responseToDocLevelEventArg.containsKey(response)) {
            eventFrameBuilder.addArguments(responseToDocLevelEventArg.get(response));
            addedArg = true;
          }
        }
        if (addedArg) {
          linkingBuilder.addEventFrames(eventFrameBuilder.build());
        }
      }
      return ResponsesAndLinking.of(responseToDocLevelEventArg.values(), linkingBuilder.build());
    }

    public String errKey(Response r) {
      return r.type() + "/" + r.role();
    }

    public void finish() throws IOException {
      outputDir.mkdirs();

      final ImmutableSetMultimap<String, String> mentionAlignmentFailures =
          mentionAlignmentFailuresB.build();
      log.info("Of {} system responses, got {} mention alignment failures",
          numResponses.size(), mentionAlignmentFailures.size());

      final File serializedFailuresFile = new File(outputDir, "alignmentFailures.json");
      final JacksonSerializer serializer = JacksonSerializer.builder().forJson().prettyOutput().build();
      serializer.serializeTo(mentionAlignmentFailures, Files.asByteSink(serializedFailuresFile));

      final File failuresCount = new File(outputDir, "alignmentFailures.count.txt");
      serializer.serializeTo(mentionAlignmentFailures.size(), Files.asByteSink(failuresCount));
    }
  }

  // code for running as a standalone executable
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

  public static void trueMain(String[] argv) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    Guice.createInjector(new ScoreKBPAgainstERE.GuiceModule(params))
        .getInstance(ScoreKBPAgainstERE.class).go();
  }


  // sets up a plugin architecture for additional scoring observers
  public static final class GuiceModule extends AbstractModule {

    private final Parameters params;

    GuiceModule(final Parameters params) {
      this.params = checkNotNull(params);
    }

    @Override
    protected void configure() {
      bind(Parameters.class).toInstance(params);
      // declare that people can provide scoring observer plugins, even though none are
      // provided by default
      MapBinder.newMapBinder(binder(), TypeLiteral.get(String.class),
          new TypeLiteral<ScoringEventObserver<DocLevelEventArg, DocLevelEventArg>>() {
          });
      try {
        bind(EREToKBPEventOntologyMapper.class)
            .toInstance(EREToKBPEventOntologyMapper.create2016Mapping());
      } catch (IOException ioe) {
        throw new TACKBPEALException(ioe);
      }
    }

    @Provides
    QuoteFilter getQuoteFiler(Parameters params) throws IOException {
      return QuoteFilter.loadFrom(Files.asByteSource(params.getExistingFile("quoteFilter")));
    }
  }
}

@Value.Immutable
@Functional
@TextGroupPackageImmutable
abstract class _ResponsesAndLinking {

  @Value.Parameter
  public abstract ImmutableSet<DocLevelEventArg> args();

  @Value.Parameter
  public abstract DocLevelArgLinking linking();

  @Value.Check
  protected void check() {
    checkArgument(args().containsAll(ImmutableSet.copyOf(concat(linking()))));
  }

  public final ResponsesAndLinking filter(Predicate<? super DocLevelEventArg> predicate) {
    return ResponsesAndLinking.of(
        Iterables.filter(args(), predicate),
        linking().filterArguments(predicate));
  }

  public final ResponsesAndLinking transform(
      final Function<? super DocLevelEventArg, DocLevelEventArg> transformer) {
    return ResponsesAndLinking
        .of(Iterables.transform(args(), transformer), linking().transformArguments(transformer));
  }

  static final Function<ResponsesAndLinking, ResponsesAndLinking> filterFunction(
      final Predicate<? super DocLevelEventArg> predicate) {
    return new Function<ResponsesAndLinking, ResponsesAndLinking>() {
      @Override
      public ResponsesAndLinking apply(final ResponsesAndLinking input) {
        return input.filter(predicate);
      }
    };
  }
}

final class EREDocAndResponses {

  private final EREDocument ereDoc;
  private final Iterable<Response> responses;
  private final ResponseLinking linking;

  public EREDocAndResponses(final EREDocument ereDoc, final Iterable<Response> responses,
      final ResponseLinking linking) {
    this.ereDoc = checkNotNull(ereDoc);
    this.responses = checkNotNull(responses);
    this.linking = checkNotNull(linking);
  }

  public EREDocument ereDoc() {
    return ereDoc;
  }

  public Iterable<Response> responses() {
    return responses;
  }

  public ResponseLinking linking() {
    return linking;
  }
}

/**
 * For 2016, there are two groups of events - the Contact.* cluster and the Transaction.*
 * cluster which have the odd property that there is a catch-all category (Contact.Contact,
 * Transaction.Transaction) to cover events that cannot be assigned more specific sub-type.  This
 * transformation will let us separate out the scores for those events from all other events.
 */
enum By2016TypeGroup implements Function<HasEventType, String> {
  INSTANCE;

  private static final ImmutableSet<Symbol> TRANSACTION_SUBTYPES = SymbolUtils.setFrom(
      "Transaction.Transaction", "Transaction.Transfer-Money",
      "Transaction.Transfer-Ownership");

  private static final ImmutableSet<Symbol> CONTACT_SUBTYPES = SymbolUtils.setFrom(
      "Contact.Contact", "Contact.Broadcast", "Contact.Meet", "Contact.Correspondance");

  @Override
  public String apply(final HasEventType x) {
    if (TRANSACTION_SUBTYPES.contains(x.eventType())) {
      return "Transaction";
    } else if (CONTACT_SUBTYPES.contains(x.eventType())) {
      return "Contact";
    } else {
      return "Other";
    }
  }
}
