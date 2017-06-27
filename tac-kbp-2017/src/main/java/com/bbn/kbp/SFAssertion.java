package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * A slot filling (SF) assertion that expresses a relation between a subject entity and object
 * entity or string node. The format of this assertion is "[EntityNodeID] [EntityType:Relation]
 * [EntityNodeID or StringNodeID] [provenances] (confidence)".
 * For a full list of valid SF predicates, see the SF predicates subsection of section 2.10
 * List of Predicates in the 2017 revisions to the KBP TAC 2016 ColdStart task description.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class SFAssertion implements ProvenancedAssertion {

  @Override
  public abstract EntityNode subject();

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.of(subject(), object().asNode());
  }

  public abstract SFArgument object();

  public abstract Symbol subjectEntityType();

  public abstract Symbol relation();

  public static SFAssertion.Builder builder() {
    return new SFAssertion.Builder();
  }

  public static class Builder extends ImmutableSFAssertion.Builder {

    public final Builder object(EntityNode node) {
      this.object(SFArgument.of(node));
      return this;
    }

    public final Builder object(StringNode node) {
      this.object(SFArgument.of(node));
      return this;
    }

  }

}
