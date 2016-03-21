package com.bbn.kbp.events2014.assessmentDiff.observers;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.assessmentDiff.KBPAssessmentDiff;

public final class CASObserver extends ConfusionMatrixAssessmentPairObserver {

  private final boolean inexactAsCorrect;

  public CASObserver(final boolean inexactAsCorrect) {
    this.inexactAsCorrect = inexactAsCorrect;
  }

  @Override
  protected boolean filter(Response response, ResponseAssessment left, ResponseAssessment right) {
    return left.entityCorrectFiller().isPresent() && right.entityCorrectFiller().isPresent();
  }

  @Override
  protected Symbol toKey(ResponseAssessment assessment) {
    // get safe from filter
    if (inexactAsCorrect) {
      return KBPAssessmentDiff.acceptableSym(assessment.entityCorrectFiller().get());
    } else {
      return Symbol.from(assessment.entityCorrectFiller().get().toString());
    }
  }
}
