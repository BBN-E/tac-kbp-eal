package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.scorer.AnswerKeyEquivalenceClasses;
import com.bbn.kbp.events2014.scorer.IncompleteAnnotationException;
import com.bbn.kbp.events2014.scorer.SystemOutputEquivalenceClasses;

import java.util.Set;

/**
 * Does nothing except throw an exception if it encounters and unannotated response. You want to do
 * this a evaluation-time scoring, for example.
 */
public final class ExitOnUnannotatedResponse<Answerable> extends KBPScoringObserver<Answerable> {

  private ExitOnUnannotatedResponse() {
  }

  public static <Answerable> ExitOnUnannotatedResponse<Answerable> create() {
    return new ExitOnUnannotatedResponse<Answerable>();
  }

  @Override
  public KBPAnswerSourceObserver answerSourceObserver(
      final SystemOutputEquivalenceClasses<Answerable> systemOutputSource,
      final AnswerKeyEquivalenceClasses<Answerable> answerKeyAnswerSource) {
    return new KBPAnswerSourceObserver(systemOutputSource, answerKeyAnswerSource) {
      @Override
      public void unannotatedSelectedResponse(final Answerable answerable,
          final Response unannotated,
          Set<AssessedResponse> assessedResponses) {
        throw IncompleteAnnotationException.forMissingResponse(unannotated);
      }
    };
  }

}
