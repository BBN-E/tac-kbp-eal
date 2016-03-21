package com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.assessmentDiff.KBPAssessmentDiff;

public final class AERObserver extends ConfusionMatrixAssessmentPairObserver {

  private final boolean inexactAsCorrect;

  public AERObserver(final boolean inexactAsCorrect) {
    this.inexactAsCorrect = inexactAsCorrect;
  }

  @Override
  protected boolean filter(Response response, ResponseAssessment left, ResponseAssessment right) {
    // AER assessment is n/a if AET assessment failed
    return left.justificationSupportsRole().isPresent()
        && right.justificationSupportsRole().isPresent();
  }

  @Override
  protected Symbol toKey(ResponseAssessment assessment) {

    // get is safe by filter check
    if (inexactAsCorrect) {
      return KBPAssessmentDiff.acceptableSym(assessment.justificationSupportsRole().get());
    } else {
      return Symbol.from(assessment.justificationSupportsRole().get().toString());
    }
  }
}
