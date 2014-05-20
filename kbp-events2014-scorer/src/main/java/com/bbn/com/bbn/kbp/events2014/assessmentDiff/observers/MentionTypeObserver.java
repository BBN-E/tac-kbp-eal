package com.bbn.com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;

/**
* Created by rgabbard on 5/20/14.
*/
public class MentionTypeObserver extends ConfusionMatrixAssessmentPairObserver {

    @Override
    protected boolean filter(Response response, ResponseAssessment left, ResponseAssessment right) {
        return left.mentionTypeOfCAS().isPresent() && right.mentionTypeOfCAS().isPresent();
    }

    @Override
    protected Symbol toKey(ResponseAssessment assessment) {
        // get is safe by filter
        return Symbol.from(assessment.realis().get().toString());
    }
}
