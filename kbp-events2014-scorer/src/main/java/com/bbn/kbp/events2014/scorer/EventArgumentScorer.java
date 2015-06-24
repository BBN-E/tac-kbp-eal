package com.bbn.kbp.events2014.scorer;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.scorer.bin.Preprocessor;
import com.bbn.kbp.events2014.scorer.observers.KBPScoringObserver;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

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
  private final Preprocessor preprocessor;
  private final ImmutableList<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers;

  private EventArgumentScorer(Preprocessor preprocessor,
      final Iterable<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers) {
    this.preprocessor = checkNotNull(preprocessor);
    this.scoringObservers = ImmutableList.copyOf(scoringObservers);
  }

  public static EventArgumentScorer create(Preprocessor preprocessor,
      Iterable<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers) {
    return new EventArgumentScorer(preprocessor, scoringObservers);
  }

  public EventArgScoringAlignment<TypeRoleFillerRealis> score(
      final AnswerKey referenceArguments, final SystemOutput systemOutput) {
    final Preprocessor.Result preprocessorResult = preprocessor.preprocess(systemOutput,
        referenceArguments);
    final Function<Response, TypeRoleFillerRealis> equivalenceClassFunction =
        TypeRoleFillerRealis.extractFromSystemResponse(preprocessorResult.answerKey().
            corefAnnotation().strictCASNormalizerFunction());

    final StandardScoringAligner<TypeRoleFillerRealis> scoringAligner =
        StandardScoringAligner.forEquivalenceClassFunction(equivalenceClassFunction);
    return scoringAligner.align(preprocessorResult.answerKey(), preprocessorResult.systemOutput());
  }

  public void logStats() {
    preprocessor.logStats();
  }
}

