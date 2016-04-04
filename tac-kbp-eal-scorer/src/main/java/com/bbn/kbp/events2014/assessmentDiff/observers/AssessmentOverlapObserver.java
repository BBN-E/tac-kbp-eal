package com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;

import java.util.Set;

import static com.bbn.kbp.events2014.AssessedResponseFunctions.response;

public final class AssessmentOverlapObserver {

  private int numCommon = 0;
  private int numLeftOnly = 0;
  private int numRightOnly = 0;

  public void observe(AnswerKey left, AnswerKey right) {
    final Set<Response> leftResponses = FluentIterable.from(left.annotatedResponses())
        .transform(response()).toSet();
    final Set<Response> rightResponses = FluentIterable.from(right.annotatedResponses())
        .transform(response()).toSet();

    numCommon += Sets.intersection(leftResponses, rightResponses).size();
    numLeftOnly += Sets.difference(leftResponses, rightResponses).size();
    numRightOnly += Sets.difference(rightResponses, leftResponses).size();
  }

  public String report() {
    return String.format("%d response in common, %d in left only, %d in right only",
        numCommon, numLeftOnly, numRightOnly);
  }
}
