package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import org.immutables.value.Value;

/**
 * A reified string object that can be used as an object in an {@code Assertion}.
 * String nodes must use identity-based equals and hashcode.
 *
 * String nodes should be created using a {@link KnowledgeBase.Builder}
 */
@TextGroupImmutable
@Value.Immutable
public abstract class StringNode implements Node {

  /**
   * @deprecated This is for internal use only.  Create {@link StringNode}s using {@link
   * KnowledgeBase.Builder}
   */
  @Deprecated
  static StringNode of() {
    return ImmutableStringNode.builder().build();
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
