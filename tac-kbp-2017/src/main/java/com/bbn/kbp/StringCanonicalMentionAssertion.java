package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that a particular mention of an string node is its canonical mention. The format of
 * this assertion is "[StringNodeID] canonical_mention [QuotedString mention] [provenances]
 * (confidence)". Each string node must have exactly one canonical mention per document, which
 * corresponds to some other non-canonical mention (i.e. the subject, object, and provenances must
 * all match exactly those of another mention).
 */
@TextGroupImmutable
@Value.Immutable
public abstract class StringCanonicalMentionAssertion extends MentionAssertion {

  @Override
  public abstract StringNode subject();

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject());
  }

  public static StringCanonicalMentionAssertion of(final StringNode subject, final String mention,
      final JustificationSpan predicateJustification) {

    return ImmutableStringCanonicalMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .predicateJustification(predicateJustification)
        .build();
  }

}
