package com.bbn.com.bbn.kbp.events2014.assessmentDiff.observers;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.google.common.base.Objects;

public final class CASObserver extends ConfusionMatrixAssessmentPairObserver {

    @Override
    protected boolean filter(Response response, ResponseAssessment left, ResponseAssessment right) {
        return left.entityCorrectFiller().isPresent() && right.entityCorrectFiller().isPresent();
    }

    @Override
    protected Symbol toKey(ResponseAssessment assessment) {
        // get safe from filter
        return Symbol.from(assessment.entityCorrectFiller().get().toString());
    }
}
