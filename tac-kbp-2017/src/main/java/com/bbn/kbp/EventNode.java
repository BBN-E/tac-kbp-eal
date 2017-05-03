package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import org.immutables.value.Value;

/**
 * A reified event object that can be used as a subject or object in an {@code Assertion}.
 * Event nodes must use identity-based equals and hashcode.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class EventNode implements Node {

  public static EventNode of() {
    return ImmutableEventNode.builder().build();
  }

  @Override
  public final boolean equals(Object other) {
    return this == other;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

}
