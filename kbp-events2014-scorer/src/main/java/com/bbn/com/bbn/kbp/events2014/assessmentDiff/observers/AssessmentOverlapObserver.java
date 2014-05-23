package com.bbn.com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;

import java.util.Set;

public final class AssessmentOverlapObserver {
    private int numCommon = 0;
    private int numLeftOnly = 0;
    private int numRightOnly = 0;

    public void observe(AnswerKey left, AnswerKey right) {
        final Set<Response> leftResponses = FluentIterable.from(left.annotatedResponses())
            .transform(AssessedResponse.Response)
             .toSet();
        final Set<Response> rightResponses = FluentIterable.from(right.annotatedResponses())
             .transform(AssessedResponse.Response)
             .toSet();

        numCommon += Sets.intersection(leftResponses, rightResponses).size();
        numLeftOnly += Sets.difference(leftResponses, rightResponses).size();
        numRightOnly += Sets.difference(rightResponses, leftResponses).size();
    }

    public String report() {
        return String.format("%d response in common, %d in left only, %d in right only",
                numCommon, numLeftOnly, numRightOnly);
    }
}
