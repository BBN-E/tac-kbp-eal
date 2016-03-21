package com.bbn.kbp.events2014.assessmentDiff.observers;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.assessmentDiff.KBPAssessmentDiff;

public final class BaseFillerObserver extends ConfusionMatrixAssessmentPairObserver {

  private final boolean inexactAsCorrect;

  public BaseFillerObserver(final boolean inexactAsCorrect) {
    this.inexactAsCorrect = inexactAsCorrect;
  }

  @Override
  protected boolean filter(Response response, ResponseAssessment left, ResponseAssessment right) {
    // BF judgement n/a if event type or role unjustified
    return left.baseFillerCorrect().isPresent() && right.baseFillerCorrect().isPresent();
  }

  @Override
  protected Symbol toKey(ResponseAssessment assessment) {
    // get safe by filter
    if (inexactAsCorrect) {
      return KBPAssessmentDiff.acceptableSym(assessment.baseFillerCorrect().get());
    } else {
      return Symbol.from(assessment.baseFillerCorrect().get().toString());
    }
  }
}
