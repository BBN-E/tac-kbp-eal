package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import org.immutables.value.Value;

/**
 * An assertion specifying the specific type of an event. The format of this assertion is
 * "[EventNodeID] type [EventType]". Type assertions do not have provenances. Each event node must h
 * ave exactly one type assertion.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class EventTypeAssertion implements Assertion {

  @Override
  public abstract EventNode subject();

  public abstract Symbol eventType();

  public static EventTypeAssertion of(final EventNode subject, final Symbol eventType) {
    return ImmutableEventTypeAssertion.builder().subject(subject).eventType(eventType).build();
  }

}
