package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that an entity is mentioned by a common noun phrase in a particular location in text.
 * The format of this assertion is "[EntityNodeID] nominal_mention [QuotedString mention]
 * [provenances] (confidence)". Each entity node must have at least one mention or nominal mention.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class NominalMentionAssertion extends MentionAssertion {

  @Override
  public abstract EntityNode subject();

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject());
  }

  public static NominalMentionAssertion of(final EntityNode subject, final String mention,
      final JustificationSpan predicateJustification) {

    return ImmutableNominalMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .predicateJustification(predicateJustification)
        .build();
  }

}
