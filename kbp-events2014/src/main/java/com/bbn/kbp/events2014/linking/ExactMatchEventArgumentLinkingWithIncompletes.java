package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * Created by jdeyoung on 4/3/15.
 */
final class ExactMatchEventArgumentLinkingWithIncompletes  extends ExactMatchEventArgumentLinkingAligner {

  ExactMatchEventArgumentLinkingWithIncompletes() {
    super();
  }
  private EventArgumentLinking addNewResponsesAsIncompletesFrom(EventArgumentLinking eventArgumentLinking, AnswerKey answerKey) {
    EventArgumentLinking minimalLinking = EventArgumentLinking.createMinimalLinkingFrom(answerKey);
    ImmutableSet<TypeRoleFillerRealis> allLinked = ImmutableSet
        .copyOf(Iterables.concat(eventArgumentLinking.linkedAsSet()));
    ImmutableSet<TypeRoleFillerRealis> minimalUnlinked = Sets
        .difference(minimalLinking.incomplete(), allLinked).immutableCopy();
    ImmutableSet<TypeRoleFillerRealis> allUnlinked = Sets.union(minimalUnlinked, eventArgumentLinking.incomplete()).immutableCopy();
    return EventArgumentLinking.create(eventArgumentLinking.docID(), eventArgumentLinking.linkedAsSet(), allUnlinked);
  }
  @Override
  public EventArgumentLinking align(ResponseLinking responseLinking,
      AnswerKey answerKey) throws InconsistentLinkingException {
    EventArgumentLinking missingIncompletes = super.align(responseLinking, answerKey);
    return addNewResponsesAsIncompletesFrom(missingIncompletes, answerKey);
  }
}
