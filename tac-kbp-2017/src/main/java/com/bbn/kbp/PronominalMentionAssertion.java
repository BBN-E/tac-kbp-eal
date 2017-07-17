package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that an entity is mentioned by a pronoun in a particular location in text.
 * The format of this assertion is "[EntityNodeID] pronominal_mention [QuotedString mention]
 * [provenances] (confidence)".
 */
@TextGroupImmutable
@Value.Immutable
public abstract class PronominalMentionAssertion extends MentionAssertion {

  @Override
  public abstract EntityNode subject();

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject());
  }

  public static PronominalMentionAssertion of(final EntityNode subject, final String mention,
      final JustificationSpan predicateJustification) {

    return ImmutablePronominalMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .predicateJustification(predicateJustification)
        .build();
  }

}
