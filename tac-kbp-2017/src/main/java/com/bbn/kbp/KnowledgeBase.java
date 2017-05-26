package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;

import org.immutables.value.Value;

import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A class to hold the entire knowledge-base of assertions as objects. Confidence is stored as a map
 * from an Assertion object to a double value despite being an (optional) part of an assertion.
 * Confidences range from 0.0 (exclusive) to 1.0 (inclusive). The knowledge-base must not have any
 * duplicate assertions, which are defined as assertions that have identical "subject predicate object"
 * triples and identical provenance(s). (Note: If two assertions only differ in confidence, they
 * are still considered duplicates. Confidence is not part of the assertion identity.)
 */
@TextGroupImmutable
@Value.Immutable
public abstract class KnowledgeBase {

  public abstract Symbol runId();

  public abstract ImmutableSet<Node> nodes();

  public abstract ImmutableSet<Assertion> assertions();

  public abstract ImmutableMap<Assertion, Double> confidence();

  @Value.Check
  protected void check() {
    // check every assertion that has a confidence is also in the set of assertions
    checkArgument(assertions().containsAll(confidence().keySet()));

    // check that all nodes used in assertions are also in nodes
    for (final Assertion assertion : assertions()) {
      checkArgument(nodes().containsAll(assertion.allNodes()),
          "Assertion %s contains unknown nodes %s.",
          assertion, Sets.difference(assertion.allNodes(), nodes()));
    }

    // check that confidence scores are valid (between 0.0 and 1.0)
    for (final Map.Entry<Assertion, Double> e : confidence().entrySet()) {
      checkArgument(e.getValue() > 0.0 && e.getValue() <= 1.0,
          "%f is an invalid value as a confidence score for %s. "
              + "A confidence score must be between 0.0 (exclusive) and 1.0 (inclusive).",
          e.getValue(), e.getKey());
    }

    checkTypeAssertions();
    checkMentionAssertions();
  }

  private void checkTypeAssertions() {
    final ImmutableSetMultimap.Builder<Node, Assertion> typeAssertionsByNodeB =
        ImmutableSetMultimap.builder();
    for (final Assertion assertion : assertions()) {
      if (assertion instanceof TypeAssertion) {
        typeAssertionsByNodeB.put(assertion.subject(), assertion);
      }
    }
    final ImmutableSetMultimap<Node, Assertion> typeAssertionsByNode =
        typeAssertionsByNodeB.build();

    // check each node has no more than one type assertion
    for (final Map.Entry<Node, Collection<Assertion>> e : typeAssertionsByNode.asMap().entrySet()) {
      checkArgument(e.getValue().size() == 1,
          "Got multiple type assertions for node %s: %s.",
          e.getKey(), e.getValue());
    }

    // check every node has a type assertion
    checkArgument(nodes().equals(typeAssertionsByNode.keySet()),
        "The following nodes lack a corresponding type assertion: %s.",
        Sets.difference(nodes(), typeAssertionsByNode.keySet()));
  }

  private void checkMentionAssertions() {
    final ImmutableSet.Builder<Node> mentionedNodesB = ImmutableSet.builder();
    for (final Assertion assertion : assertions()) {
      if (assertion instanceof MentionAssertion) {
        mentionedNodesB.add(assertion.subject());
      }
    }
    final ImmutableSet<Node> mentionedNodes = mentionedNodesB.build();

    // check every node has at least one mention
    checkArgument(nodes().equals(mentionedNodes),
        "The following nodes lack a mention assertions: %s.",
        Sets.difference(nodes(), mentionedNodes));

    // TODO: check each canonical mention has a corresponding non-canonical mention
    // TODO: check each normalized mention has a corresponding non-normalized mention
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends ImmutableKnowledgeBase.Builder {

  }
}
