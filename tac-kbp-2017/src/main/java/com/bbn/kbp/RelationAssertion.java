package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that two entities (or an entity and a string value) are related.
 *
 * TODO: this implementation is not quite right because it assumes all arguments are
 * entity nodes when in fact they can be string nodes. But there is no reason to fix this
 * until Adept E2E starts producing the correct output. issue kbp/#530
 */
@TextGroupImmutable
@Value.Immutable
public abstract class RelationAssertion implements ProvenancedAssertion {

  public abstract EntityNode subject();

  public abstract Symbol relationType();

  public abstract EntityNode object();

  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject(), object());
  }


  public static class Builder extends ImmutableRelationAssertion.Builder {

  }
}
