package com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;

public class AETObserver extends ConfusionMatrixAssessmentPairObserver {

  @Override
  protected boolean filter(Response response, ResponseAssessment left, ResponseAssessment right) {
    return left.justificationSupportsEventType().isPresent() && right
        .justificationSupportsEventType().isPresent();
  }

  @Override
  protected Symbol toKey(ResponseAssessment assessment) {
    return Symbol.from(assessment.justificationSupportsEventType().get().toString());
  }
}
