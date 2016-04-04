package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

class LinkableResponseFilter2015ForSystem implements AnswerKey.Filter {

  @Override
  public Predicate<AssessedResponse> assessedFilter() {
    return LinkableResponseFilter2015ForGold.responsesRealisIsLinkable;
  }

  @Override
  public Predicate<Response> unassessedFilter() {
    return Predicates.alwaysFalse();
  }
}
