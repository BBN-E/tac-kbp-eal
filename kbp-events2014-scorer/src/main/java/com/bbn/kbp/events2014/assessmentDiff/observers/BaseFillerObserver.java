package com.bbn.kbp.events2014.assessmentDiff.observers;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;

public final class BaseFillerObserver extends ConfusionMatrixAssessmentPairObserver {

  @Override
  protected boolean filter(Response response, ResponseAssessment left, ResponseAssessment right) {
    // BF judgement n/a if event type or role unjustified
    return left.baseFillerCorrect().isPresent() && right.baseFillerCorrect().isPresent();
  }

  @Override
  protected Symbol toKey(ResponseAssessment assessment) {
    // get safe by filter
    return Symbol.from(assessment.baseFillerCorrect().get().toString());
  }
}
