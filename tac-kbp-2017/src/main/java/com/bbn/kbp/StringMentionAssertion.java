package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that a string node is mentioned in a particular location in text. The format of this
 * assertion is "[StringNodeID] mention [QuotedString mention] [provenances] (confidence)".
 * Each string node must have at least one mention.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class StringMentionAssertion implements MentionAssertion {

  @Override
  public abstract StringNode subject();

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject());
  }

  public static StringMentionAssertion of(final StringNode subject, final String mention,
      final Set<Provenance> provenances) {

    return ImmutableStringMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .provenances(provenances)
        .build();
  }

}
