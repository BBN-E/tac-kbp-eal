package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * A class to hold the entire knowledge-base of assertions as objects. Confidence is stored as a map
 * from an Assertion object to a double value despite being an (optional) part of an assertion.
 * Confidences range from 0.0 (exclusive) to 1.0 (inclusive). The knowledge-base must not have any
 * duplicate assertions, which are defined as assertions that have identical "subject predicate object"
 * triples and identical provenance(s). (Note: If two assertions only differ in confidence, they
 * are still considered duplicates. Confidence is not part of the assertion identity.)
 */
interface KnowledgeBase {
  // TODO: Add to this. This is a stub.
  // TODO: Make this an immutable.

  ImmutableSet<Node> nodes();

  ImmutableSet<Assertion> assertions();

  ImmutableMap<Assertion, Double> confidence();

  interface Builder {
    // TODO: Add to this. This is a stub.
    // TODO: Make this an immutable.

    EventNode makeEvent();

    EventTypeAssertion setEventType(EventNode eventNode, Symbol type);

    ProvenancedAssertion setEventArgument(EventNode eventNode, EventArgument eventArgument,
        Symbol eventType, Symbol role, Symbol realis, Set<Provenance> provenances);

    EntityNode makeEntity();

    EntityTypeAssertion setEntityType(EntityNode entityNode, Symbol type);

    MentionAssertion setMention(Node node, String mention,
        Set<Provenance> provenances);

    void setConfidence(Assertion assertion, double confidence);
  }

}
