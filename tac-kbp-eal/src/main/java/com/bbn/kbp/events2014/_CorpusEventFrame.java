package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;

import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents all mentions of a single event grouped across multiple documents.
 */
@Value.Immutable(prehash = true)
@Functional
@TextGroupPublicImmutable
abstract class _CorpusEventFrame {

  public abstract ImmutableSet<DocEventFrameReference> docEventFrames();

  @Value.Check
  protected void checkPreconditions() {
    checkArgument(!docEventFrames().isEmpty(), "A corpus-level event frame may not be empty");
  }
}
