package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import org.immutables.value.Value;

/**
 * A reified string object that can be used as an object in an {@code Assertion}.
 * String nodes must use identity-based equals and hashcode.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class StringNode implements Node {

  public static StringNode of() {
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
