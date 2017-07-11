package com.bbn.kbp;

import com.bbn.bue.common.HasDocID;
import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.immutables.value.Value;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An assertion that two entities (or an entity and a string value) are related.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class RelationAssertion implements Assertion, HasDocID {

  @Override
  public abstract EntityNode subject();

  public abstract Symbol relationType();

  public abstract RelationArgument object();

  public Symbol docId() {
    return Iterables.getFirst(predicateJustification(), null).documentId();
  }

  public abstract Optional<JustificationSpan> fillerString();

  public abstract ImmutableSet<JustificationSpan> predicateJustification();

  @Value.Check
  protected void check() {
    // filler string only used for string arguments
    checkArgument(fillerString().isPresent() == object().stringNode().isPresent());
    checkArgument(predicateJustification().size() >= 1);
    checkArgument(predicateJustification().size() <= 3);
  }

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.of(subject(), object().asNode());
  }


  public static class Builder extends ImmutableRelationAssertion.Builder {

    public Builder object(EntityNode entityNode) {
      this.object(RelationArgument.of(entityNode));
      return this;
    }

    public Builder object(StringNode stringNode) {
      object(RelationArgument.of(stringNode));
      return this;
    }
  }
}

