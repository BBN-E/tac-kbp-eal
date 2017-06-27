package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that an event is mentioned in a particular location in text. The format of this
 * assertion is "[EventNodeID] mention.[realis] [QuotedString mention] [provenances] (confidence)".
 * Each event node must have at least one mention.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class EventMentionAssertion implements MentionAssertion {

  @Override
  public abstract EventNode subject();

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject());
  }

  public abstract Symbol realis();

  public static EventMentionAssertion of(final EventNode subject, final String mention,
      final Symbol realis, final Set<Provenance> provenances) {

    return ImmutableEventMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .realis(realis)
        .provenances(provenances)
        .build();
  }

}
