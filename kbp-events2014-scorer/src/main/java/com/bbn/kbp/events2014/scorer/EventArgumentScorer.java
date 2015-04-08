package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.scorer.bin.Preprocessor;
import com.bbn.kbp.events2014.scorer.observers.KBPScoringObserver;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        TypeRoleFillerRealis.extractFromSystemResponse(preprocessorResult.normalizer());

    final StandardScoringAligner<TypeRoleFillerRealis> scoringAligner =
        StandardScoringAligner.forEquivalenceClassFunction(equivalenceClassFunction);
    return scoringAligner.align(preprocessorResult.answerKey(), preprocessorResult.systemOutput());
  }
}

