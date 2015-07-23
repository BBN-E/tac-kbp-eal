package com.bbn.kbp.events2014.scorer;

import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ScoringData;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.scorer.observers.KBPScoringObserver;
import com.bbn.kbp.events2014.transformers.ScoringDataTransformation;

import com.google.common.base.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A scorer for the KBP event argument-attachment task.
 *
 * @author rgabbard
 */
public final class EventArgumentScorer {

  private static final Logger log = LoggerFactory.getLogger(EventArgumentScorer.class);
  private final ScoringDataTransformation preprocessor;

  private EventArgumentScorer(ScoringDataTransformation preprocessor,
      final Iterable<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers) {
    this.preprocessor = checkNotNull(preprocessor);
  }

  public static EventArgumentScorer create(ScoringDataTransformation preprocessor,
      Iterable<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers) {
    return new EventArgumentScorer(preprocessor, scoringObservers);
  }

  public EventArgScoringAlignment<TypeRoleFillerRealis> score(ScoringData scoringData) {
    final ScoringData preprocessorResult = preprocessor.transform(scoringData);
    final Function<Response, TypeRoleFillerRealis> equivalenceClassFunction =
        TypeRoleFillerRealis.extractFromSystemResponse(preprocessorResult.answerKey().get()
            .corefAnnotation().strictCASNormalizerFunction());

    final StandardScoringAligner<TypeRoleFillerRealis> scoringAligner =
        StandardScoringAligner.forEquivalenceClassFunction(equivalenceClassFunction);
    return scoringAligner
        .align(preprocessorResult.answerKey().get(), preprocessorResult.argumentOutput().get());
  }

  public void logStats() {
    preprocessor.logStats();
  }
}

