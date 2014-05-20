package com.bbn.com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;

public class AETObserver extends ConfusionMatrixAssessmentPairObserver {

    @Override
    protected boolean filter(Response response, ResponseAssessment left, ResponseAssessment right) {
        return true;
    }

    @Override
    protected Symbol toKey(ResponseAssessment assessment) {
        return Symbol.from(assessment.justificationSupportsEventType().toString());
    }
}
