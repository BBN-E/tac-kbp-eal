package com.bbn.kbp;

import com.bbn.bue.common.HasDocID;
import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that an entity has a positive or negative sentiment about another entity. The format
 * of this assertion is "[EntityNodeID] [EntityType:Sentiment] [EntityNodeID] [provenances]
 * (confidence)".
 * The sentiment predicate can be one of "likes", "dislikes", "is_liked_by", and "is_disliked_by".
 */
@TextGroupImmutable
@Value.Immutable
public abstract class SentimentAssertion implements Assertion, HasSinglePredicateJustification,
    HasDocID {

  @Override
  public abstract EntityNode subject();

  public abstract JustificationSpan predicateAssertion();

  @Override
  public Symbol docID() {
    return predicateAssertion().documentId();
  }

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject(), object());
  }

  public abstract EntityNode object();

  public abstract Symbol subjectEntityType();

  public abstract Symbol sentiment();

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends ImmutableSentimentAssertion.Builder {

  }

}
