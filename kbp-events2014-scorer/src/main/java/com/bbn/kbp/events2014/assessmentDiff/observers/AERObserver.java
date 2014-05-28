package com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.assessmentDiff.observers.ConfusionMatrixAssessmentPairObserver;
import com.google.common.base.Objects;

public final class AERObserver extends ConfusionMatrixAssessmentPairObserver {

    @Override
    protected boolean filter(Response response, ResponseAssessment left, ResponseAssessment right) {
        // AER assessment is n/a if AET assessment failed
        return left.justificationSupportsRole().isPresent()
                && right.justificationSupportsRole().isPresent();
    }

    @Override
    protected Symbol toKey(ResponseAssessment assessment) {
        // get is safe by filter check
        return Symbol.from(assessment.justificationSupportsRole().get().toString());

    }
}
