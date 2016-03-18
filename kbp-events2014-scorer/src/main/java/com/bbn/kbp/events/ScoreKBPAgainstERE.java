package com.bbn.kbp.events;

import com.bbn.bue.common.Finishable;
import com.bbn.bue.common.HasDocID;
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
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.nlp.corenlp.CoreNLPConstituencyParse;
import com.bbn.nlp.corenlp.CoreNLPDocument;
import com.bbn.nlp.corenlp.CoreNLPParseNode;
import com.bbn.nlp.corenlp.CoreNLPSentence;
import com.bbn.nlp.corenlp.CoreNLPXMLLoader;
import com.bbn.nlp.corpora.ere.EREArgument;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.EREEntity;
import com.bbn.nlp.corpora.ere.EREEntityArgument;
import com.bbn.nlp.corpora.ere.EREEntityMention;
import com.bbn.nlp.corpora.ere.EREEvent;
import com.bbn.nlp.corpora.ere.EREEventMention;
import com.bbn.nlp.corpora.ere.EREFillerArgument;
import com.bbn.nlp.corpora.ere.ERELoader;
import com.bbn.nlp.events.HasEventType;
import com.bbn.nlp.events.scoring.DocLevelEventArg;
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
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;

import static com.bbn.bue.common.evaluation.InspectorTreeDSL.inspect;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformLeft;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformRight;
import static com.bbn.bue.common.evaluation.InspectorTreeDSL.transformed;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Scores KBP 2015 event argument output against an ERE gold standard.  Scoring is in terms of
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
    final File outputDir = params.getCreatableDirectory("outputDir");
    final AnnotationStore annStore = AssessmentSpecFormats.openAnnotationStore(
        params.getExistingDirectory("annotationStore"), AssessmentSpecFormats.Format.KBP2015);
    final ImmutableMap<Symbol, File> coreNLPProcessedRawDocs = FileUtils.loadSymbolToFileMap(
        Files.asCharSource(params.getExistingFile("coreNLPDocIDMap"), Charsets.UTF_8));
    final boolean relaxUsingCORENLP = params.getBoolean("relaxUsingCoreNLP");
    final boolean useExactMatchForCoreNLPRelaxation =
        relaxUsingCORENLP && params.getBoolean("useExactMatchForCoreNLPRelaxation");
    final CoreNLPXMLLoader coreNLPXMLLoader =
        CoreNLPXMLLoader.builder(HeadFinders.<CoreNLPParseNode>getEnglishPTBHeadFinder()).build();

    log.info("Scoring over {} documents", docIDsToScore.size());

    // on the gold side we take an ERE document as input
    final TypeToken<EREDocument> inputIsEREDoc = new TypeToken<EREDocument>() {
    };
    // on the test side we take an AnswerKey, but we bundle it with the gold ERE document
    // for use in alignment later
    final TypeToken<EREDocAndAnswerKey> inputIsEREDocAndAnswerKey =
        new TypeToken<EREDocAndAnswerKey>() {
        };

    final InspectionNode<EvalPair<EREDocument, EREDocAndAnswerKey>>
        input = InspectorTreeDSL.pairedInput(inputIsEREDoc, inputIsEREDocAndAnswerKey);

    // these will extract the scoring tuples from the KBP system input and ERE docs, respectively
    // we create these here because we will call their .finish method()s
    // at the end to record some statistics about alignment failures,
    // so we need to keep references to them
    final DocLevelArgsFromKBPExtractor docLevelArgsFromKBPExtractor =
        new DocLevelArgsFromKBPExtractor(coreNLPProcessedRawDocs,
            coreNLPXMLLoader, relaxUsingCORENLP,
            useExactMatchForCoreNLPRelaxation);
    final DocLevelArgsFromEREExtractor docLevelArgsFromEREExtractor =
        new DocLevelArgsFromEREExtractor();

    // this sets it up so that everything fed to input will be scored in various ways
    setupScoring(input, docLevelArgsFromKBPExtractor, docLevelArgsFromEREExtractor, outputDir);

    final ERELoader loader = ERELoader.create();

    for (final Symbol docID : docIDsToScore) {
      final File ereFileName = goldDocIDToFileMap.get(docID);
      if (ereFileName == null) {
        throw new RuntimeException("Missing key file for " + docID);
      }
      final EREDocument ereDoc = loader.loadFrom(ereFileName);
      checkState(ereDoc.getDocId().equals(docID.asString()),
          "fetched document ID must be equal to stored");
      final AnswerKey answerKey = annStore.read(docID).filter(
          KEEP_CORRECT_ANSWERS_OF_RELEVANT_ROLES_ONLY);
      // feed this ERE doc/ KBP output pair to the scoring network
      input.inspect(EvalPair.of(ereDoc, new EREDocAndAnswerKey(ereDoc, answerKey)));
    }

    // trigger the scoring network to write its summary files
    input.finish();
    // log alignment failures
    docLevelArgsFromKBPExtractor.finish();
    docLevelArgsFromEREExtractor.finish();
  }

  private static final ImmutableSet<Symbol> BANNED_ROLES =
      SymbolUtils.setFrom("Time", "Crime", "Position",
          "Fine", "Sentence");
  public static final AnswerKey.Filter KEEP_CORRECT_ANSWERS_OF_RELEVANT_ROLES_ONLY =
      new AnswerKey.Filter() {
        @Override
        public Predicate<AssessedResponse> assessedFilter() {
          return new Predicate<AssessedResponse>() {
            @Override
            public boolean apply(final AssessedResponse input) {
              return input.isCorrectUpToInexactJustifications()
                  && !BANNED_ROLES.contains(input.response().role());
            }
          };
        }

        @Override
        public Predicate<Response> unassessedFilter() {
          return Predicates.alwaysFalse();
        }
      };

  private static Function<EvalPair<? extends Iterable<? extends DocLevelEventArg>, ? extends Iterable<? extends DocLevelEventArg>>, ProvenancedAlignment<DocLevelEventArg, DocLevelEventArg, DocLevelEventArg, DocLevelEventArg>>
      EXACT_MATCH_ALIGNER = EquivalenceBasedProvenancedAligner
      .forEquivalenceFunction(Functions.<DocLevelEventArg>identity())
      .asFunction();

  // this sets up a scoring network which is executed on every input
  private static void setupScoring(
      final InspectionNode<EvalPair<EREDocument, EREDocAndAnswerKey>> input,
      final DocLevelArgsFromKBPExtractor docLevelArgsFromKBPExtractor,
      final DocLevelArgsFromEREExtractor docLeveLArgsFromKBPExtractor,
      final File outputDir) {
    final InspectorTreeNode<EvalPair<ImmutableSet<DocLevelEventArg>, ImmutableSet<DocLevelEventArg>>>
        inputAsSetsOfScoringTuples =
        transformRight(transformLeft(input, docLeveLArgsFromKBPExtractor),
            docLevelArgsFromKBPExtractor);

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

  private static final class DocLevelArgsFromEREExtractor
      implements Function<EREDocument, ImmutableSet<DocLevelEventArg>>, Finishable {

    // for tracking things from the answer key discarded due to not being entity mentions
    private final Multiset<String> allGoldArgs = HashMultiset.create();
    private final Multiset<String> discarded = HashMultiset.create();

    @Override
    public ImmutableSet<DocLevelEventArg> apply(final EREDocument doc) {
      final ImmutableSet.Builder<DocLevelEventArg> ret = ImmutableSet.builder();
      for (final EREEvent ereEvent : doc.getEvents()) {
        for (final EREEventMention ereEventMention : ereEvent.getEventMentions()) {
          for (final EREArgument ereArgument : ereEventMention.getArguments()) {
            final String typeRoleKey = ereEventMention.getType() + "/" + ereArgument.getRole();
            allGoldArgs.add(typeRoleKey);

            if (ereArgument instanceof EREEntityArgument) {
              final EREEntityMention entityMention =
                  ((EREEntityArgument) ereArgument).entityMention();
              final Optional<EREEntity> containingEntity = doc.getEntityContaining(entityMention);
              checkState(containingEntity.isPresent(), "Corrupt ERE key input lacks "
                  + "entity for entity mention %s", entityMention);
              ret.add(DocLevelEventArg.create(Symbol.from(doc.getDocId()),
                  Symbol.from(ereEventMention.getType()), Symbol.from(ereArgument.getRole()),
                  containingEntity.get().getID()));
            } else if (ereArgument instanceof EREFillerArgument) {
              // we don't currently handle non-entity mention arguments
              discarded.add(typeRoleKey);
            } else {
              throw new RuntimeException("Unknown ERE argument type " + ereArgument.getClass());
            }
          }
        }
      }
      return ret.build();
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

  private static final class DocLevelArgsFromKBPExtractor
      implements Function<EREDocAndAnswerKey, ImmutableSet<DocLevelEventArg>>,
      Finishable {

    private Multiset<String> mentionAlignmentFailures = HashMultiset.create();
    private Multiset<String> numResponses = HashMultiset.create();
    private final ImmutableMap<Symbol, File> ereMapping;
    private final CoreNLPXMLLoader coreNLPXMLLoader;
    private final boolean relaxUsingCORENLP;
    private final boolean useExactMatchForCoreNLPRelaxation;

    public DocLevelArgsFromKBPExtractor(final Map<Symbol, File> ereMapping,
        final CoreNLPXMLLoader coreNLPXMLLoader, final boolean relaxUsingCORENLP,
        final boolean useExactMatchForCoreNLPRelaxation) {
      this.ereMapping = ImmutableMap.copyOf(ereMapping);
      this.coreNLPXMLLoader = coreNLPXMLLoader;
      this.relaxUsingCORENLP = relaxUsingCORENLP;
      this.useExactMatchForCoreNLPRelaxation = useExactMatchForCoreNLPRelaxation;
    }

    public ImmutableSet<DocLevelEventArg> apply(final EREDocAndAnswerKey input) {
      final ImmutableSet.Builder<DocLevelEventArg> ret = ImmutableSet.builder();
      final AnswerKey answerKey = input.answerKey();
      final EREDocument doc = input.ereDoc();
      final Symbol ereID = Symbol.from(doc.getDocId());
      final Optional<CoreNLPDocument> coreNLPDoc;
      try {
        coreNLPDoc = Optional.fromNullable(ereMapping.get(ereID)).isPresent() ? Optional
            .of(coreNLPXMLLoader.loadFrom(ereMapping.get(ereID)))
                                                                              : Optional.<CoreNLPDocument>absent();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      for (final AssessedResponse annResponse : answerKey.annotatedResponses()) {
        // we try to align a system response to an ERE entity by exact offset match of the
        // basefiller against one of an entity's mentions
        // this search could be faster but is probably good enough
        final Response response = annResponse.response();
        numResponses.add(errKey(response));
        final OffsetRange<CharOffset> baseFillerOffsets = response.baseFiller().asCharOffsetRange();

        EREEntity matchingEntity = null;

        for (final EREEntity entity : doc.getEntities()) {
          for (final EREEntityMention ereEntityMention : entity.getMentions()) {
            final OffsetRange<CharOffset> mentionRange = OffsetRange.charOffsetRange(
                ereEntityMention.getExtent().getStart(), ereEntityMention.getExtent().getEnd());
            if (baseFillerOffsets.equals(mentionRange)) {
              matchingEntity = entity;
              break;
            }
            if (ereEntityMention.getHead().isPresent()) {
              final OffsetRange<CharOffset> headRange = OffsetRange.charOffsetRange(
                  ereEntityMention.getHead().get().getStart(),
                  ereEntityMention.getHead().get().getEnd());
              if (baseFillerOffsets.equals(headRange)) {
                matchingEntity = entity;
                break;
              }
              if (coreNLPDoc.isPresent() && relaxUsingCORENLP) {
                final Optional<CoreNLPSentence> sent =
                    coreNLPDoc.get().sentenceForCharOffsets(baseFillerOffsets);
                if (sent.isPresent()) {
                  final Optional<CoreNLPParseNode> node =
                      sent.get().nodeForOffsets(baseFillerOffsets);
                  if (node.isPresent()) {
                    final Optional<CoreNLPParseNode> terminalHead = node.get().terminalHead();
                    if (terminalHead.isPresent()) {
                      // how strict do we want this to be?
                      terminalHead.get().span();
                      final OffsetRange<CharOffset> ereHead = OffsetRange
                          .charOffsetRange(ereEntityMention.getHead().get().getStart(),
                              ereEntityMention.getHead().get().getEnd());
                      if ((useExactMatchForCoreNLPRelaxation &&
                               ereHead.equals(terminalHead.get().span()))
                          || (!useExactMatchForCoreNLPRelaxation &&
                                  ereHead.contains(terminalHead.get().span()))) {
                        matchingEntity = entity;
                        break;
                      }
                    }
                  }
                }
              }
            }
          }
        }

        if (matchingEntity != null) {
          ret.add(DocLevelEventArg.create(Symbol.from(doc.getDocId()), response.type(),
              response.role(), matchingEntity.getID()));
        } else if (coreNLPDoc.isPresent() && relaxUsingCORENLP) {
          final String parseString;
          final Optional<CoreNLPSentence> sent =
              coreNLPDoc.get().sentenceForCharOffsets(baseFillerOffsets);
          if (sent.isPresent()) {
            final Optional<CoreNLPConstituencyParse> parse = sent.get().parse();
            if (parse.isPresent()) {
              parseString = parse.get().coreNLPString();
            } else {
              parseString = "no parse found!";
            }
          } else {
            parseString = "no sentence found!";
          }
          log.info(
              "Failed to align base filler with offsets {} to an ERE mention for response {}, parse tree is {}",
              baseFillerOffsets, response, parseString);
          mentionAlignmentFailures.add(errKey(response));
        } else {
          log.info("Failed to align base filler with offsets {} to an ERE mention for response {}",
              baseFillerOffsets, response);
          mentionAlignmentFailures.add(errKey(response));
        }
      }
      return ret.build();
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

final class EREDocAndAnswerKey {

  private final EREDocument ereDoc;
  private final AnswerKey anwerKey;

  public EREDocAndAnswerKey(final EREDocument ereDoc, final AnswerKey anwerKey) {
    this.ereDoc = checkNotNull(ereDoc);
    this.anwerKey = checkNotNull(anwerKey);
  }

  public EREDocument ereDoc() {
    return ereDoc;
  }

  public AnswerKey answerKey() {
    return anwerKey;
  }
}
