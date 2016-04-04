package com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.assessmentDiff.KBPAssessmentDiff;

public class AETObserver extends ConfusionMatrixAssessmentPairObserver {

  private final boolean inexactAsCorrect;

  public AETObserver(final boolean inexactAsCorrect) {
    this.inexactAsCorrect = inexactAsCorrect;
  }

  @Override
  protected boolean filter(Response response, ResponseAssessment left, ResponseAssessment right) {
    return left.justificationSupportsEventType().isPresent() && right
        .justificationSupportsEventType().isPresent();
  }

  @Override
  protected Symbol toKey(ResponseAssessment assessment) {
    if (inexactAsCorrect) {
      return KBPAssessmentDiff.acceptableSym(assessment.justificationSupportsEventType().get());
    } else {
      return Symbol.from(assessment.justificationSupportsEventType().get().toString());
    }
  }
}
