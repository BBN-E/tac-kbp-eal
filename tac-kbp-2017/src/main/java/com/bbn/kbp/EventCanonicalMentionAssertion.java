package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that a particular mention of an event is its canonical mention. The format of this
 * assertion is "[EventNodeID] canonical_mention.[realis] [QuotedString mention] [provenances]
 * (confidence)". Each event node must have exactly one canonical mention per document, which
 * corresponds to some other non-canonical mention (i.e. the subject, object, and provenances
 * (and possibly realis?) must all match exactly those of another mention).
 */
@TextGroupImmutable
@Value.Immutable
public abstract class EventCanonicalMentionAssertion implements MentionAssertion {

  @Override
  public abstract EventNode subject();

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject());
  }

  public abstract Symbol realis();

  public static EventCanonicalMentionAssertion of(final EventNode subject, final String mention,
      final Symbol realis, final Set<Provenance> provenances) {

    return ImmutableEventCanonicalMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .realis(realis)
        .provenances(provenances)
        .build();
  }

}
