package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that two entities (or an entity and a string value) are related.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class RelationAssertion implements ProvenancedAssertion {

  @Override
  public abstract EntityNode subject();

  public abstract Symbol relationType();

  public abstract RelationArgument object();

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

