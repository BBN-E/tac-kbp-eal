package com.bbn.kbp.linking;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;
import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ScoringData;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.linking.EventArgumentLinkingAligner;
import com.bbn.kbp.events2014.linking.ExactMatchEventArgumentLinkingAligner;
import com.bbn.kbp.events2014.scorer.LinkingScore;
import com.bbn.kbp.events2014.scorer.StandardScoringAligner;
import com.bbn.kbp.events2014.scorer.bin.Preprocessors;
import com.bbn.kbp.events2014.transformers.KeepBestJustificationOnly;
import com.bbn.kbp.events2014.transformers.ResponseMapping;
import com.bbn.kbp.events2014.transformers.ScoringDataTransformation;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.bbn.kbp.events2014.TypeRoleFillerRealisFunctions.realis;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;

@Beta
public final class EALScorer2015Style {
  private static final Logger log = LoggerFactory.getLogger(EALScorer2015Style.class);

  private final Optional<ScoringDataTransformation> preprocessor;
  private final EventArgumentLinkingAligner aligner =
      ExactMatchEventArgumentLinkingAligner.create();
  private final LinkF1 linkF1 = LinkF1.create();
  private final double beta;
  private final double lambda;

  /* pacakge-private */ EALScorer2015Style(ScoringDataTransformation preprocessor, final double beta, final double lambda) {
    checkArgument(beta >= 0.0);
    checkArgument(lambda >= 0.0);

    this.preprocessor = Optional.fromNullable(preprocessor);
    this.beta = beta;
    this.lambda = lambda;
  }

  public static EALScorer2015Style create(Parameters params) {
    return new EALScorer2015Style(Preprocessors.for2015FromParameters(params), 0.25, 0.5);
  }

  public static EALScorer2015Style createWithoutPreprocessing() {
    return new EALScorer2015Style(null, 0.25, 0.5);
  }

  public double lambda() {
    return lambda;
  }

  public final class Result {

    private final ArgResult argResult;
    private final LinkResult linkResult;

    private Result(final ArgResult argResult, final LinkResult linkResult) {
      this.argResult = checkNotNull(argResult);
      this.linkResult = checkNotNull(linkResult);
      checkArgument(argResult.docID().equalTo(linkResult.docID()));
    }

    public double scaledScore() {
      return (1.0 - lambda) * argResult.scaledArgumentScore() + lambda * linkResult
          .scaledLinkingScore();
    }

    public ArgResult argResult() {
      return argResult;
    }

    public LinkResult linkResult() {
      return linkResult;
    }

    public Symbol docID() {
      return argResult.docID();
    }
  }

  public final class ArgResult {

    private final EventArgScoringAlignment<TypeRoleFillerRealis> argScoringAlignment;

    private ArgResult(
        final EventArgScoringAlignment<TypeRoleFillerRealis> argScoringAlignment) {
      this.argScoringAlignment = checkNotNull(argScoringAlignment);
    }

    public double argumentNormalizer() {
      return Sets.union(
          argScoringAlignment.truePositiveEquivalenceClasses(),
          argScoringAlignment.falseNegativeEquivalenceClasses()).size();
    }

    public Symbol docID() {
      return argScoringAlignment.docID();
    }

    public EventArgScoringAlignment<TypeRoleFillerRealis> argumentScoringAlignment() {
      return argScoringAlignment;
    }

    public double scaledArgumentScore() {
      return unscaledArgumentScore() / argumentNormalizer();
    }

    public double unscaledArgumentScore() {
      final int truePositiveArgumentECs =
          argScoringAlignment.truePositiveEquivalenceClasses().size();
      final int falsePositiveArgumentECs =
          argScoringAlignment.falsePositiveEquivalenceClasses().size();
      return truePositiveArgumentECs - beta * falsePositiveArgumentECs;
    }

    public double unscaledTruePositiveArguments() {
      return argScoringAlignment.truePositiveEquivalenceClasses().size();
    }

    public double unscaledFalsePositiveArguments() {
      return argScoringAlignment.falsePositiveEquivalenceClasses().size();
    }

    public double precision() {
      return (unscaledTruePositiveArguments() > 0.0) ?
             unscaledTruePositiveArguments() / (unscaledTruePositiveArguments()
                                                    + unscaledFalsePositiveArguments())
                                                     : 0.0;
    }

    public double recall() {
      return (unscaledTruePositiveArguments() > 0.0) ?
             unscaledTruePositiveArguments() / (unscaledTruePositiveArguments()
                                                    + argScoringAlignment
                 .falseNegativeEquivalenceClasses().size())
                                                     : 0.0;
    }

    public ArgResult copyFiltered(final Predicate<TypeRoleFillerRealis> filter) {
      return new ArgResult(argScoringAlignment.copyFiltered(filter));
    }

    public double unscaledFalseNegativeArguments() {
      return argScoringAlignment.falseNegativeEquivalenceClasses().size();
    }
  }

  public final class LinkResult {

    private final LinkingScore linkingScore;

    private LinkResult(final LinkingScore linkingScore) {
      this.linkingScore = checkNotNull(linkingScore);
    }

    public Symbol docID() {
      return linkingScore.docID();
    }

    public double linkingNormalizer() {
      return linkingScore.referenceLinkingSize();
    }

    public LinkingScore linkingScore() {
      return linkingScore;
    }

    public double unscaledLinkingScore() {
      return linkingScore.F1() * linkingNormalizer();
    }

    public double unscaledLinkingPrecision() {
      return linkingScore.precision() * linkingNormalizer();
    }

    public double unscaledLinkingRecall() {
      return linkingScore.recall() * linkingNormalizer();
    }

    public double scaledLinkingScore() {
      return linkingScore.F1();
    }

    public double scaledLinkingPrecision() {
      return linkingScore.precision();
    }

    public double scaledLinkingRecall() {
      return linkingScore.recall();
    }
  }

  private static final Predicate<TypeRoleFillerRealis> REALIS_IS_NOT_GENERIC =
      compose(not(equalTo(KBPRealis.Generic)), realis());

  public Result score(ScoringData unpreprocessedScoringData) {
    checkArgument(unpreprocessedScoringData.answerKey().isPresent() && unpreprocessedScoringData
        .argumentOutput().isPresent()
        && unpreprocessedScoringData.referenceLinking().isPresent() && unpreprocessedScoringData
        .systemLinking().isPresent());

    final ScoringData scoringData = preprocessor.isPresent()?
                                    preprocessor.get().transform(unpreprocessedScoringData)
                                                            :unpreprocessedScoringData;

    // regardless of the preprocessing, we always filter down to having the smallest possible set
    // of highest scoring responses which keeps at least one response in each event frame of the
    // system linking.
    final ResponseMapping keepBestResponseMapping =
        KeepBestJustificationOnly.computeResponseMappingUsingProvidedCoref(
            DocumentSystemOutput2015
                .from(scoringData.argumentOutput().get(), scoringData.systemLinking().get()),
            scoringData.answerKey().get().corefAnnotation());
    final ScoringData bestOnlyScoringData = ScoringData.builder().from(scoringData)
        .argumentOutput(keepBestResponseMapping.apply(scoringData.argumentOutput().get()))
        .systemLinking(keepBestResponseMapping.apply(scoringData.systemLinking().get()))
        .build();

    return new Result(new ArgResult(scoreEventArguments(bestOnlyScoringData)),
        new LinkResult(scoreLinking(bestOnlyScoringData)));
  }

  private EventArgScoringAlignment<TypeRoleFillerRealis> scoreEventArguments(ScoringData scoringData) {
    final Function<Response, TypeRoleFillerRealis> equivalenceClassFunction =
        TypeRoleFillerRealis.extractFromSystemResponse(
            scoringData.answerKey().get().corefAnnotation().strictCASNormalizerFunction());

    final StandardScoringAligner<TypeRoleFillerRealis> scoringAligner =
        StandardScoringAligner.forEquivalenceClassFunction(equivalenceClassFunction);
    return scoringAligner.align(scoringData.answerKey().get(), scoringData.argumentOutput().get());
  }

  public LinkingScore scoreLinking(ScoringData scoringData) {
    log.info("Scoring linking for {}", scoringData.answerKey().get().docId());
    checkArgument(scoringData.systemLinking().isPresent());
    checkArgument(scoringData.referenceLinking().isPresent());
    checkArgument(scoringData.answerKey().isPresent());

    final ResponseLinking referenceLinking = scoringData.referenceLinking().get();
    final AnswerKey answerKey = scoringData.answerKey().get();
    // we need to remove all incorrectly assessed responses from the system linking
    final ResponseLinking systemLinking =
        deleteIncorrectResponses(scoringData.argumentOutput().get(),
        answerKey).apply(scoringData.systemLinking().get());

    checkArgument(referenceLinking.incompleteResponses().isEmpty(),
        "Reference key for %s has %s responses whose linking has not been annotated",
        referenceLinking.docID(), referenceLinking.incompleteResponses().size());

    checkArgument(systemLinking.incompleteResponses().isEmpty(),
        "System linking for %s has incomplete responses", systemLinking.docID());

    final EventArgumentLinking referenceArgumentLinking =
        aligner.align(referenceLinking, answerKey);
    final EventArgumentLinking systemArgumentLinking =
        aligner.align(systemLinking, answerKey);

    final EventArgumentLinking filteredReferenceArgumentLinking = referenceArgumentLinking
        .filteredCopy(REALIS_IS_NOT_GENERIC);

    /*
      Remove system TRFR that are not in reference TRFR.
      Just removing incorrectly assessed responses is not sufficient, because:
      Assume we have a Response that is correct except for its Realis: its prediction is Actual
      when correct is Generic.
      After neutralize -realis, this Response is treated as correct.

      During linking scoring, we remove all incorrectly assessed responses from system linking.
      But since this Response is now 'correct', it will pass through.
      However, this Response will not be present in the Reference-linking, since it is Generic.
    */
    final Predicate<TypeRoleFillerRealis> inReferenceArgumentLinking = in(
        filteredReferenceArgumentLinking.allLinkedEquivalenceClasses());

    final EventArgumentLinking filteredSystemArgumentLinking = systemArgumentLinking
        .filteredCopy(REALIS_IS_NOT_GENERIC).filteredCopy(inReferenceArgumentLinking);

    return LinkingScore.from(filteredReferenceArgumentLinking,
        linkF1.score(filteredSystemArgumentLinking.linkedAsSetOfSets(),
            filteredReferenceArgumentLinking.linkedAsSetOfSets()));
  }

  private ResponseMapping deleteIncorrectResponses(final ArgumentOutput argumentOutput,
      final AnswerKey answerKey) {
    final ImmutableSet.Builder<Response> toDelete = ImmutableSet.builder();
    for (final Response r : argumentOutput.responses()) {
      if (!answerKey.assess(r).get().isCorrectUpToInexactJustifications()) {
        toDelete.add(r);
      }
    }
    return ResponseMapping.delete(toDelete.build());
  }
}

