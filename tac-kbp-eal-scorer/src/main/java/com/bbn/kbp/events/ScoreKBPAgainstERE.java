package com.bbn.kbp.events;

import com.bbn.bue.common.Finishable;
import com.bbn.bue.common.HasDocID;
import com.bbn.bue.common.Inspector;
import com.bbn.bue.common.IntIDSequence;
import com.bbn.bue.common.TextGroupPackageImmutable;
import com.bbn.bue.common.evaluation.AggregateBinaryFScoresInspector;
import com.bbn.bue.common.evaluation.BinaryErrorLogger;
import com.bbn.bue.common.evaluation.BinaryFScoreBootstrapStrategy;
import com.bbn.bue.common.evaluation.BootstrapInspector;
import com.bbn.bue.common.evaluation.EquivalenceBasedProvenancedAligner;
import com.bbn.bue.common.evaluation.EvalPair;
import com.bbn.bue.common.evaluation.InspectionNode;
import com.bbn.bue.common.evaluation.InspectorTreeDSL;
import com.bbn.bue.common.evaluation.InspectorTreeNode;
import com.bbn.bue.common.evaluation.ProvenancedAlignment;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events.ontology.SimpleEventOntologyMapper;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.linking.ExplicitFMeasureInfo;
import com.bbn.kbp.linking.LinkF1;
import com.bbn.nlp.corenlp.CoreNLPDocument;
import com.bbn.nlp.corenlp.CoreNLPParseNode;
import com.bbn.nlp.corenlp.CoreNLPXMLLoader;
import com.bbn.nlp.corpora.ere.EREArgument;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.EREEvent;
import com.bbn.nlp.corpora.ere.EREEventMention;
import com.bbn.nlp.corpora.ere.ERELoader;
import com.bbn.nlp.corpora.ere.LinkRealis;
import com.bbn.nlp.events.HasEventType;
import com.bbn.nlp.parsing.HeadFinders;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;

import org.immutables.func.Functional;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

import static com.bbn.bue.common.evaluation.InspectorTreeDSL.inspect;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformBoth;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformLeft;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformRight;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformed;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
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

  private ScoreKBPAgainstERE() {
    throw new UnsupportedOperationException();
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
    Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    log.info(params.dump());
    final ImmutableSet<Symbol> docIDsToScore = ImmutableSet.copyOf(
        FileUtils.loadSymbolList(params.getExistingFile("docIDsToScore")));
    final ImmutableMap<Symbol, File> goldDocIDToFileMap = FileUtils.loadSymbolToFileMap(
        Files.asCharSource(params.getExistingFile("goldDocIDToFileMap"), Charsets.UTF_8));
    final File outputDir = params.getCreatableDirectory("ereScoringOutput");
    final SystemOutputLayout outputLayout = SystemOutputLayout.ParamParser.fromParamVal(
        params.getString("outputLayout"));
    final SystemOutputStore outputStore =
        outputLayout.open(params.getExistingDirectory("systemOutput"));

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
            coreNLPXMLLoader, relaxUsingCORENLP);
    final ResponsesAndLinkingFromEREExtractor responsesAndLinkingFromEREExtractor =
        new ResponsesAndLinkingFromEREExtractor(EREToKBPEventOntologyMapper.create2016Mapping());

    // this sets it up so that everything fed to input will be scored in various ways
    setupScoring(input, responsesAndLinkingFromKBPExtractor, responsesAndLinkingFromEREExtractor,
        outputDir);

    final ERELoader loader = ERELoader.create();

    for (final Symbol docID : docIDsToScore) {
      final File ereFileName = goldDocIDToFileMap.get(docID);
      if (ereFileName == null) {
        throw new RuntimeException("Missing key file for " + docID);
      }
      final EREDocument ereDoc = loader.loadFrom(ereFileName);
      if (!ereDoc.getDocId().equals(docID.asString())) {
        log.warn("Fetched document ID {} does not equal stored {}", ereDoc.getDocId(), docID);
      }
      final Iterable<Response>
          responses = filter(outputStore.read(docID).arguments().responses(), bannedRolesFilter);
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
      SymbolUtils.setFrom("Time", "Crime", "Position",
          "Fine", "Sentence");
  private static final ImmutableSet<Symbol> ROLES_2016 = SymbolUtils
      .setFrom("Agent", "Artifact", "Attacker", "Audience", "Beneficiary", "Crime", "Destination",
          "Entity", "Giver", "Instrument", "Money", "Origin", "Person", "Place", "Position",
          "Recipient", "Target", "Thing", "Time", "Victim");
  private static final ImmutableSet<Symbol> ALLOWED_ROLES_2016 =
      Sets.difference(ROLES_2016, BANNED_ROLES).immutableCopy();
  private static final ImmutableSet<Symbol> linkableRealis = SymbolUtils.setFrom("Other", "Actual");

  private static final Predicate<Response> bannedRolesFilter = new Predicate<Response>() {
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

    // set up for event argument scoring in 2015 style
    eventArgumentScoringSetup(filteredForLifeDie, outputDir);
    // set up for linking scoring in 2015 style
    linkingScoringSetup(filteredForLifeDie, outputDir);
  }

  private static void eventArgumentScoringSetup(
      final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>>
          inputAsResponsesAndLinking, final File outputDir) {
    final InspectorTreeNode<EvalPair<ImmutableSet<DocLevelEventArg>, ImmutableSet<DocLevelEventArg>>>
        inputAsSetsOfScoringTuples =
        transformBoth(inputAsResponsesAndLinking, ResponsesAndLinkingFunctions.args());

    // require exact match between the system arguments and the key responses
    final InspectorTreeNode<ProvenancedAlignment<DocLevelEventArg, DocLevelEventArg, DocLevelEventArg, DocLevelEventArg>>
        alignmentNode = transformed(inputAsSetsOfScoringTuples, EXACT_MATCH_ALIGNER);

    // overall F score
    final AggregateBinaryFScoresInspector<Object, Object> scoreAndWriteOverallFScore =
        AggregateBinaryFScoresInspector.createOutputtingTo("aggregateF.txt", outputDir);
    inspect(alignmentNode).with(scoreAndWriteOverallFScore);

    // log errors
    final BinaryErrorLogger<HasDocID, HasDocID> logWrongAnswers = BinaryErrorLogger
        .forStringifierAndOutputDir(Functions.<HasDocID>toStringFunction(), outputDir);
    inspect(alignmentNode).with(logWrongAnswers);

    final BinaryFScoreBootstrapStrategy perEventBootstrapStrategy =
        BinaryFScoreBootstrapStrategy.createBrokenDownBy("EventType",
            HasEventType.ExtractFunction.INSTANCE, outputDir);
    final BootstrapInspector breakdownScoresByEventTypeWithBootstrapping =
        BootstrapInspector.forStrategy(perEventBootstrapStrategy, 1000, new Random(0));
    inspect(alignmentNode).with(breakdownScoresByEventTypeWithBootstrapping);
  }

  private static void linkingScoringSetup(
      final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>>
          inputAsResponsesAndLinking, final File outputDir) {
    final InspectorTreeNode<EvalPair<ResponsesAndLinking, ResponsesAndLinking>> filteredForRealis =
        transformBoth(inputAsResponsesAndLinking,
            ResponsesAndLinking.filterFunction(REALIS_ALLOWED_FOR_LINKING));
    final InspectorTreeNode<EvalPair<DocLevelArgLinking, DocLevelArgLinking>>
        linkingNode = transformBoth(filteredForRealis, ResponsesAndLinkingFunctions.linking());

    // we throw out any system responses not found in the key before scoring linking
    final InspectorTreeNode<EvalPair<DocLevelArgLinking, DocLevelArgLinking>>
        filteredNode = transformed(linkingNode, RestrictToLinking.INSTANCE);
    final LinkingInspector linkingInspector =
        LinkingInspector.createOutputtingTo(outputDir);
    inspect(filteredNode).with(linkingInspector);
  }

  private static final Predicate<_DocLevelEventArg> ARG_TYPE_IS_ALLOWED_FOR_2016 =
      compose(in(ALLOWED_ROLES_2016), DocLevelEventArgFunctions.eventArgumentType());
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
          Predicates.compose(equalTo(LifeDie), DocLevelEventArgFunctions.eventType())));
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

  private static final class LinkingInspector implements
      Inspector<EvalPair<DocLevelArgLinking, DocLevelArgLinking>> {

    private final File outputDir;
    private final ImmutableMap.Builder<Symbol, ExplicitFMeasureInfo> countsB =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<Symbol, Integer> predictedCountsB = ImmutableMap.builder();
    private final ImmutableMap.Builder<Symbol, Integer> actualCountsB = ImmutableMap.builder();
    private final ImmutableMap.Builder<Symbol, Integer> linkingArgsCountB = ImmutableMap.builder();


    private LinkingInspector(final File outputDir) {
      this.outputDir = outputDir;
    }

    public static LinkingInspector createOutputtingTo(final File outputFile) {
      return new LinkingInspector(outputFile);
    }

    @Override
    public void inspect(
        final EvalPair<DocLevelArgLinking, DocLevelArgLinking> item) {
      checkArgument(ImmutableSet.copyOf(concat(item.key())).containsAll(
          ImmutableSet.copyOf(concat(item.test()))), "Must contain only answers in test set!");
      final ExplicitFMeasureInfo counts = LinkF1.create().score(item.test(), item.key());
      final ImmutableSet<DocLevelEventArg> args = ImmutableSet.copyOf(concat(
          transform(concat(item.test().eventFrames(), item.key().eventFrames()),
              ScoringEventFrameFunctions.arguments())));
      final ImmutableSet<Symbol> docids =
          ImmutableSet.copyOf(transform(args, DocLevelEventArgFunctions.docID()));
      final Symbol docid = Iterables.getOnlyElement(docids);
      predictedCountsB.put(docid, ImmutableSet.copyOf(concat(item.test().eventFrames())).size());
      actualCountsB.put(docid, ImmutableSet.copyOf(concat(item.key().eventFrames())).size());
      countsB.put(docid, counts);
      linkingArgsCountB.put(docid, args.size());
    }

    @Override
    public void finish() throws IOException {
      // copies logic from com.bbn.kbp.events2014.scorer.bin.AggregateResultWriter.computeLinkScores()
      final ImmutableMap<Symbol, ExplicitFMeasureInfo> counts = countsB.build();
      final ImmutableMap<Symbol, Integer> predictedCounts = predictedCountsB.build();
      final ImmutableMap<Symbol, Integer> actualCounts = actualCountsB.build();
      final ImmutableMap<Symbol, Integer> linkingArgsCounts = linkingArgsCountB.build();

      double precision = 0;
      double recall = 0;
      double f1 = 0;
      double linkNormalizerSum = 0;
      checkNotNull(counts, "Inspect must be called before Finish!");
      for (final Symbol docid : counts.keySet()) {
        final File docOutput = new File(outputDir, docid.asString());
        final PrintWriter outputWriter = new PrintWriter(new File(docOutput, "linkingF.txt"));
        outputWriter.println(counts.get(docid).toString());
        outputWriter.close();

        precision += counts.get(docid).precision() * predictedCounts.get(docid);
        recall += counts.get(docid).recall() * actualCounts.get(docid);
        f1 += counts.get(docid).f1() * actualCounts.get(docid);
        linkNormalizerSum += linkingArgsCounts.get(docid);
      }

      // the normalizer sum can't actually be negative here, but this minimizes divergence with the source logic.
      double aggregateLinkScore =
          (linkNormalizerSum > 0.0) ? f1 / linkNormalizerSum : 0.0;
      double aggregateLinkPrecision =
          (linkNormalizerSum > 0.0) ? precision / linkNormalizerSum : 0.0;
      double aggregateLinkRecall =
          (linkNormalizerSum > 0.0) ? recall / linkNormalizerSum : 0.0;

      final ExplicitFMeasureInfo aggregate =
          new ExplicitFMeasureInfo(aggregateLinkPrecision, aggregateLinkRecall, aggregateLinkScore);
      final PrintWriter outputWriter = new PrintWriter(new File(outputDir, "linkingF.txt"));
      outputWriter.println(aggregate);
      outputWriter.close();
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
    private final SimpleEventOntologyMapper mapper;

    private ResponsesAndLinkingFromEREExtractor(final SimpleEventOntologyMapper mapper) {
      this.mapper = checkNotNull(mapper);
    }

    @Override
    public ResponsesAndLinking apply(final EREDocument doc) {
      final ImmutableSet.Builder<DocLevelEventArg> ret = ImmutableSet.builder();
      // every event mention argument within a hopper is linked
      final DocLevelArgLinking.Builder linking = DocLevelArgLinking.builder();
      for (final EREEvent ereEvent : doc.getEvents()) {
        final ScoringEventFrame.Builder eventFrame = ScoringEventFrame.builder();
        boolean addedArg = false;
        for (final EREEventMention ereEventMention : ereEvent.getEventMentions()) {
          for (final EREArgument ereArgument : ereEventMention.getArguments()) {
            final Symbol ereEventMentionType = Symbol.from(ereEventMention.getType());
            final Symbol ereEventMentionSubtype = Symbol.from(ereEventMention.getSubtype());
            final Symbol ereArgumentRole = Symbol.from(ereArgument.getRole());
            final ArgumentRealis argumentRealis =
                getRealis(ereEventMention.getRealis(), ereArgument.getRealis().get());

            boolean skip = false;
            if (!mapper.eventType(ereEventMentionType).isPresent()) {
              log.debug("EventType {} is not known to the KBP ontology", ereEventMentionType);
              skip = true;
            }
            if (!mapper.eventRole(ereArgumentRole).isPresent()) {
              log.debug("EventRole {} is not known to the KBP ontology", ereArgumentRole);
              skip = true;
            }
            if (!mapper.eventSubtype(ereEventMentionSubtype).isPresent()) {
              log.debug("EventSubtype {} is not known to the KBP ontology", ereEventMentionSubtype);
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
            eventFrame.addArguments(arg);
            addedArg = true;
          }
        }
        if (addedArg) {
          linking.addEventFrames(eventFrame.build());
        }
      }
      return ResponsesAndLinking.of(ret.build(), linking.build());
    }

    /*
    {EventMentionRealis,ArgumentRealis}=event argument realis
    {Generic,*}=Generic
    {X, True}=X
    {Actual, False}=Other
    {Other,False}=Other
     */
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
    }
  }

  private static final class ResponsesAndLinkingFromKBPExtractor
      implements Function<EREDocAndResponses, ResponsesAndLinking>,
      Finishable {

    // each system item which fails to align to any reference item gets put in its own
    // coreference class, numbered using this sequence
    private IntIDSequence alignmentFailureIDs = IntIDSequence.startingFrom(0);
    private Multiset<String> mentionAlignmentFailures = HashMultiset.create();
    private Multiset<String> numResponses = HashMultiset.create();
    private final ImmutableMap<Symbol, File> ereMapping;
    private final CoreNLPXMLLoader coreNLPXMLLoader;
    private final boolean relaxUsingCORENLP;

    public ResponsesAndLinkingFromKBPExtractor(final Map<Symbol, File> ereMapping,
        final CoreNLPXMLLoader coreNLPXMLLoader, final boolean relaxUsingCORENLP) {
      this.ereMapping = ImmutableMap.copyOf(ereMapping);
      this.coreNLPXMLLoader = coreNLPXMLLoader;
      this.relaxUsingCORENLP = relaxUsingCORENLP;
    }

    public ResponsesAndLinking apply(final EREDocAndResponses input) {
      final ImmutableSet.Builder<DocLevelEventArg> ret = ImmutableSet.builder();
      final Iterable<Response> responses = input.responses();
      final EREDocument doc = input.ereDoc();
      final Symbol ereID = Symbol.from(doc.getDocId());
      final Optional<CoreNLPDocument> coreNLPDoc;
      final EREAligner ereAligner;

      try {
        coreNLPDoc = Optional.fromNullable(ereMapping.get(ereID)).isPresent() ? Optional
            .of(coreNLPXMLLoader.loadFrom(ereMapping.get(ereID)))
                                                                              : Optional.<CoreNLPDocument>absent();
        ereAligner = EREAligner
            .create(relaxUsingCORENLP, doc, coreNLPDoc,
                EREToKBPEventOntologyMapper.create2016Mapping());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      final ImmutableMap.Builder<Response, DocLevelEventArg> responseToDocLevelArg =
          ImmutableMap.builder();

      for (final Response response : responses) {
        numResponses.add(errKey(response));
        final Symbol realis = Symbol.from(response.realis().name());

        final Optional<ScoringCorefID> alignedCorefIDOpt = ereAligner.argumentForResponse(response);
        // this increments the alignment failure ID regardless of success or failure, but
        // we don't care
        final ScoringCorefID alignedCorefID = alignedCorefIDOpt.or(
            ScoringCorefID.of(ScoringEntityType.AlignmentFailure,
                Integer.toString(alignmentFailureIDs.nextID())));

        final DocLevelEventArg res = DocLevelEventArg.builder().docID(Symbol.from(doc.getDocId()))
            .eventType(response.type()).eventArgumentType(response.role())
            .corefID(alignedCorefID.globalID()).realis(realis).build();
        ret.add(res);
        responseToDocLevelArg.put(response, res);

        // record alignment failures
        if (!alignedCorefIDOpt.isPresent()) {
          mentionAlignmentFailures.add(errKey(response));
        }
      }
      return fromResponses(ImmutableSet.copyOf(input.responses()),
          responseToDocLevelArg.build(), input.linking());
    }

    ResponsesAndLinking fromResponses(final ImmutableSet<Response> originalResponses,
        final ImmutableMap<Response, DocLevelEventArg> responseToDocLevelEventArg,
        final ResponseLinking responseLinking) {
      final DocLevelArgLinking.Builder linkingBuilder = DocLevelArgLinking.builder();
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

    public void finish() {
      log.info(
          "Of {} system responses, got {} mention alignment failures",
          numResponses.size(), mentionAlignmentFailures.size());
      for (final String errKey : numResponses.elementSet()) {
        if (mentionAlignmentFailures.count(errKey) > 0) {
          log.info("Of {} {} responses, {} mention alignment failures",
              +numResponses.count(errKey), errKey, mentionAlignmentFailures.count(errKey));
        }
      }
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
    checkArgument(args().containsAll(ImmutableSet.copyOf(Iterables.concat(linking()))));
  }

  public final ResponsesAndLinking filter(Predicate<? super DocLevelEventArg> predicate) {
    return ResponsesAndLinking.of(
        Iterables.filter(args(), predicate),
        linking().filterArguments(predicate));
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
