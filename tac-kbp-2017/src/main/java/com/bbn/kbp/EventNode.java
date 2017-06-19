package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import org.immutables.value.Value;

/**
 * A reified event object that can be used as a subject or object in an {@code Assertion}.
 * Event nodes must use identity-based equals and hashcode.
 *
 * Event nodes should be created using a {@link KnowledgeBase.Builder}
 */
@TextGroupImmutable
@Value.Immutable
public abstract class EventNode implements Node {

  /**
   * @deprecated This is for internal use only.  Create {@link EventNode}s using {@link
   * KnowledgeBase.Builder}
   */
  @Deprecated
  static EventNode of() {
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
