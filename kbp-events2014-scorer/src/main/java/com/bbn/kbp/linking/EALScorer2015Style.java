package com.bbn.kbp.linking;

import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.collections.CollectionUtils;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ScoringData;
import com.bbn.kbp.events2014.SystemOutput2015;
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
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;

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
    private final EventArgScoringAlignment<TypeRoleFillerRealis> argScoringAlignment;
    private final LinkingScore linkingScore;

    private Result(
        final EventArgScoringAlignment<TypeRoleFillerRealis> argScoringAlignment,
        final LinkingScore linkingScore) {
      this.argScoringAlignment = checkNotNull(argScoringAlignment);
      this.linkingScore = checkNotNull(linkingScore);
    }

    public double scaledArgumentScore() {
      return unscaledArgumentScore() / argumentNormalizer();
    }

    public double unscaledArgumentScore() {
      final int truePositiveArgumentECs = argScoringAlignment.truePositiveEquivalenceClasses().size();
      final int falsePositiveArgumentECs = argScoringAlignment.falsePositiveEquivalenceClasses().size();
      return truePositiveArgumentECs - beta * falsePositiveArgumentECs;
    }

    public double unscaledLinkingScore() {
      return linkingScore.F1()*linkingNormalizer();
    }

    public double scaledLinkingScore() {
      return linkingScore.F1();
    }

    public double scaledScore() {
      return (1.0-lambda)*scaledArgumentScore()+lambda*scaledLinkingScore();
    }

    public EventArgScoringAlignment<TypeRoleFillerRealis> argumentScoringAlignment() {
      return argScoringAlignment;
    }

    public LinkingScore linkingScore() {
      return linkingScore;
    }

    public Symbol docID() {
      return argScoringAlignment.docID();
    }

    public double argumentNormalizer() {
      return Sets.union(
          argScoringAlignment.truePositiveEquivalenceClasses(),
          argScoringAlignment.falseNegativeEquivalenceClasses()).size();
    }

    public double linkingNormalizer() {
      return linkingScore.referenceArgumentLinking().allLinkedEquivalenceClasses().size();
    }
  }

  private static final Predicate<TypeRoleFillerRealis> REALIS_IS_NOT_GENERIC =
      compose(not(equalTo(KBPRealis.Generic)), TypeRoleFillerRealis.realisFunction());

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
            SystemOutput2015
                .from(scoringData.argumentOutput().get(), scoringData.systemLinking().get()),
            scoringData.answerKey().get().corefAnnotation());
    final ScoringData bestOnlyScoringData = scoringData.modifiedCopy()
        .withArgumentOutput(keepBestResponseMapping.apply(scoringData.argumentOutput().get()))
        .withSystemLinking(keepBestResponseMapping.apply(scoringData.systemLinking().get()))
        .build();

    return new Result(scoreEventArguments(bestOnlyScoringData), scoreLinking(bestOnlyScoringData));
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

    final EventArgumentLinking filteredSystemArgumentLinking = systemArgumentLinking
        .filteredCopy(REALIS_IS_NOT_GENERIC);

    return LinkingScore.from(referenceLinking, referenceArgumentLinking, systemLinking, systemArgumentLinking,
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

class LinkF1 {

  private static final Logger log = LoggerFactory.getLogger(LinkF1.class);

  private LinkF1() {
  }

  public static LinkF1 create() {
    return new LinkF1();
  }

  public <T> ExplicitFMeasureInfo score(final Iterable<Set<T>> predicted,
      final Iterable<Set<T>> gold) {
    double linkF1Sum = 0.0;
    double linkPrecisionSum = 0.0;
    double linkRecallSum = 0.0;

    final Multimap<T, Set<T>> predictedItemToGroup =
        CollectionUtils.makeSetElementsToContainersMultimap(predicted);
    final Multimap<T, Set<T>> goldItemToGroup =
        CollectionUtils.makeSetElementsToContainersMultimap(gold);

    final ImmutableSet<T> keyItems = ImmutableSet.copyOf(concat(gold));
    final ImmutableSet<T> predictedItems = ImmutableSet.copyOf(concat(predicted));
    checkArgument(keyItems.containsAll(predictedItems),
        "Predicted linking has items the gold linking lacks: %s", Sets.difference(predictedItems, keyItems));
    if (keyItems.isEmpty()) {
      if (predictedItemToGroup.isEmpty()) {
        log.info("Key and predicted are empty; returning score of 1");
        return new ExplicitFMeasureInfo(1.0, 1.0, 1.0);
      } else {
        log.info("Key is empty but predicted is not; returning score of 0");
        return new ExplicitFMeasureInfo(0.0, 0.0, 0.0);
      }
    } else if (predictedItems.isEmpty()) {
      log.info("Predicted is empty but key is not; returning score of 0");
      return new ExplicitFMeasureInfo(0.0, 0.0, 0.0);
    }

    for (final T keyItem : keyItems) {
      // withouts are to ensure an element is not counted as its own neighbor
      final Set<T> predictedNeighbors = ImmutableSet.copyOf(
          without(concat(predictedItemToGroup.get(keyItem)),
              keyItem));
      final Set<T> goldNeighbors = ImmutableSet.copyOf(without(
          concat(goldItemToGroup.get(keyItem)),
          keyItem));

      if (predictedItems.contains(keyItem)) {
        final boolean predictedIsSingleton = predictedNeighbors.isEmpty();
        final boolean goldIsSingleton = goldNeighbors.isEmpty();

        if (!(predictedIsSingleton && goldIsSingleton)) {
          int truePositiveLinks = Sets.intersection(predictedNeighbors, goldNeighbors).size();
          int falsePositiveLinks = Sets.difference(predictedNeighbors, goldNeighbors).size();
          int falseNegativeLinks = Sets.difference(goldNeighbors, predictedNeighbors).size();

          final FMeasureCounts fMeasureCounts =
              FMeasureCounts.from(truePositiveLinks, falsePositiveLinks, falseNegativeLinks);
          linkF1Sum += fMeasureCounts.F1();
          linkPrecisionSum += fMeasureCounts.precision();
          linkRecallSum += fMeasureCounts.recall();

          if (predictedIsSingleton) {
            log.info(
                "For {}, gold neighbors are {} but predicted is singleton. Item f-measure is {}",
                keyItem, goldNeighbors, fMeasureCounts.F1());
          } else {
            log.info(
                "For {}, gold neighbors are {} and predicted neighbors are {}. Item f-measure is {}",
                keyItem, goldNeighbors, predictedNeighbors, fMeasureCounts.F1());
          }
        } else {
          final boolean appearsInPredicted = predictedItems.contains(keyItem);
          if (appearsInPredicted) {
            // arguments which are correctly linked to nothing (singletons)
            // count as having perfect links
            linkF1Sum += 1.0;
            linkPrecisionSum += 1.0;
            linkRecallSum += 1.0;
            log.info("{} is a singleton in both key and predicted. Score of 1.0", keyItem);
          } else {
            log.info("{} is a singleton in key but does not appear in predicted. Score of 0.0",
                keyItem);
          }
        }
      } else {
        log.info("{} is present only in the gold linking. Item F-measure is 0.0", keyItem);
      }
    }
    // note we divide linkPrecisionSum by the number of predicted items,
    // but the others by the number of gold items. This is because missing items
    // hurt recall but not precision
    final ExplicitFMeasureInfo explicitFMeasureInfo =
        new ExplicitFMeasureInfo(linkPrecisionSum / predictedItems.size(),
            linkRecallSum / keyItems.size(), linkF1Sum / keyItems.size());
    log.info("Final document linking score: {}", explicitFMeasureInfo);
    return explicitFMeasureInfo;
  }

  @MoveToBUECommon
  private static <T, S extends T> Iterable<T> without(Iterable<T> input, S itemToExclude) {
    return filter(input, not(Predicates.<T>equalTo(itemToExclude)));
  }

  @MoveToBUECommon
  private static <T> Iterable<T> withoutAnyOf(Iterable<T> input,
      Collection<? extends T> itemsToExclude) {
    return filter(input, not(in(itemsToExclude)));
  }
}

