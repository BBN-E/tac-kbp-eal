package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion specifying the specific type of a node. The format of this assertion is "[NodeID]
 * type [type]". Type assertions do not have provenances. Each node must have exactly one type
 * assertion. For a full list of valid SF predicates, see the SF predicates subsection of section
 * 2.11 List of allowable types for each kind of node in the 2017 revisions to the KBP TAC 2016
 * ColdStart task description.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class TypeAssertion implements Assertion {

  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.of(subject());
  }

  public abstract Symbol type();

  public static TypeAssertion of(final Node subject, final Symbol type) {
    return ImmutableTypeAssertion.builder().subject(subject).type(type).build();
  }

}
