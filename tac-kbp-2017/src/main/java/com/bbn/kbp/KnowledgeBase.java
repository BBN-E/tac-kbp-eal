package com.bbn.kbp;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;

import org.immutables.value.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

  /**
   * Maps nodes to identifiers.  This is for convenience in order to maintain node names
   * from input to output through KB transformations.  It is not necessary to assign a name to
   * all (or even any) nodes.  Because of deletion, it is acceptable for nodes which
   * are not in the database to appear in this bimap, so don't iterate over it. This is
   * why it is not public and we have {@link #nameForNode(Node)}
   */
  abstract ImmutableBiMap<Node, String> nodesToNames();

  public abstract ImmutableSet<Assertion> assertions();

  public abstract ImmutableMap<Assertion, Double> confidence();

  /**
   * Gets the stable name assigned to a node, if any.
   * This is for convenience in order to maintain node names
   * from input to output through KB transformations.
   */
  public final Optional<String> nameForNode(Node node) {
    return Optional.fromNullable(nodesToNames().get(node));
  }


  /**
   * Get the entity canonical mentions found in a given document. This is useful for
   * aligning external system output to this knowledge base.
   */
  @Value.Lazy
  public ImmutableMultimap<Symbol, EntityCanonicalMentionAssertion> documentToEntityCanonicalMentions() {
    return FluentIterable.from(assertions())
        .filter(EntityCanonicalMentionAssertion.class)
        .index(EntityCanonicalMentionAssertionFunctions.docId());
  }


  @Value.Check
  protected void check() {
    // check every assertion that has a confidence is also in the set of assertions
    checkArgument(assertions().containsAll(confidence().keySet()));

    // check that all nodes used in assertions are also in nodes
    for (final Assertion assertion : assertions()) {
      checkArgument(nodes().containsAll(assertion.allNodes()),
          "Assertion %s contains unknown nodes %s.",
          assertion, prettyNodes(Sets.difference(assertion.allNodes(), nodes())));
    }

    // check that confidence scores are valid (between 0.0 and 1.0)
    // temporarily disabled to process buggy input
    // kbp#530 will re-enable
    /*for (final Map.Entry<Assertion, Double> e : confidence().entrySet()) {
      checkArgument(e.getValue() > 0.0 && e.getValue() <= 1.0,
          "%f is an invalid value as a confidence score for %s. "
              + "A confidence score must be between 0.0 (exclusive) and 1.0 (inclusive).",
          e.getValue(), e.getKey());
    }*/

    checkTypeAssertions();
    checkMentionAssertions();

    for (final String name : nodesToNames().values()) {
      checkArgument(!name.isEmpty(),
          "You do not have to specify names for nodes, but if you do they can't be empty");
    }
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
          prettyNode(e.getKey()), e.getValue());
    }

    // check every node has a type assertion
    checkArgument(nodes().equals(typeAssertionsByNode.keySet()),
        "Nodes lack a corresponding type assertion: %s.",
        prettyNodes(Sets.difference(nodes(), typeAssertionsByNode.keySet())));
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
    // check disabled for bad Adept E2E output. Will be restored in #530
    /*checkArgument(nodes().equals(mentionedNodes),
        "The following nodes lack a mention assertions: %s.",
        prettyNodes(Sets.difference(nodes(), mentionedNodes)));*/

    // TODO: check each canonical mention has a corresponding non-canonical mention. Issue #537
    // TODO: check each normalized mention has a corresponding non-normalized mention. Issue #537
  }

  /**
   * Provides a name for a node, if possible. For use in error messages.
   */
  private String prettyNode(Node node) {
    final Optional<String> name = nameForNode(node);
    if (name.isPresent()) {
      return name.get();
    } else {
      return "unnamed node";
    }
  }

  /**
   * Provides a human-readable representation of a set of nodes, using names if possible.
   * For use in error messages.
   */
  private String prettyNodes(Collection<Node> nodes) {
    final List<String> parts = new ArrayList<>();

    int numUnnamed = 0;
    for (final Node node : nodes) {
      final Optional<String> name = nameForNode(node);
      if (name.isPresent()) {
        parts.add(name.get());
      } else {
        ++numUnnamed;
      }
    }

    if (numUnnamed > 0) {
      parts.add(((parts.size() > 0) ? " and " : "")
          + numUnnamed + " unnamed nodes");
    }

    return "[" + StringUtils.commaJoiner().join(parts) + "]";
  }

  public static Builder builder() {
    return new Builder();
  }


  public static class Builder extends ImmutableKnowledgeBase.Builder {

    public Builder nameNode(Node node, String name) {
      putNodesToNames(node, name);
      return this;
    }

    /**
     * Convenience method to add an assertion to the KB and record its confidence at the
     * same time.
     */
    public Builder registerAssertion(Assertion assertion, double confidence) {
      this.addAssertions(assertion);
      this.putConfidence(assertion, confidence);
      return this;
    }

    // .of() only deprecated to warn external users
    @SuppressWarnings("deprecation")
    public StringNode newStringNode() {
      final StringNode ret = StringNode.of();
      addNodes(ret);
      return ret;
    }

    // .of() only deprecated to warn external users
    @SuppressWarnings("deprecation")
    public EntityNode newEntityNode() {
      final EntityNode ret = EntityNode.of();
      addNodes(ret);
      return ret;
    }

    // .of() only deprecated to warn external users
    @SuppressWarnings("deprecation")
    public EventNode newEventNode() {
      final EventNode ret = EventNode.of();
      addNodes(ret);
      return ret;
    }
  }
}
