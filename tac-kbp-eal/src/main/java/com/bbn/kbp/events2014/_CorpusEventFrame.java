package com.bbn.kbp.events2014;

import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents all mentions of a single event grouped across multiple documents.
 */
@Value.Immutable(prehash = true)
@Functional
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@com.bbn.bue.common.TextGroupPublicImmutable
abstract class _CorpusEventFrame {

  @Value.Parameter
  public abstract String id();

  @Value.Parameter
  public abstract ImmutableSet<DocEventFrameReference> docEventFrames();

  @Value.Check
  protected void checkPreconditions() {
    checkArgument(!id().isEmpty(), "Corpus event frame IDs may not be empty");
    checkArgument(!id().contains("\t"), "Corpus event frame IDs may not contain tabs");
    checkArgument(!docEventFrames().isEmpty(), "A corpus-level event frame may not be empty");
  }
}
