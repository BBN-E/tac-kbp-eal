package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a reference to an event frame in a document.
 */
@Value.Immutable
@Functional
@TextGroupPublicImmutable
abstract class _DocEventFrameReference {

  @Value.Parameter
  public abstract Symbol docID();

  @Value.Parameter
  public abstract String eventFrameID();

  @Value.Check
  protected void checkPreconditions() {
    checkArgument(!docID().asString().isEmpty());
    checkArgument(!docID().asString().contains("-"));
    checkArgument(!docID().asString().contains("\t"));
    checkArgument(!eventFrameID().isEmpty());
    checkArgument(!eventFrameID().contains("-"));
    checkArgument(!eventFrameID().contains("\t"));
  }
}
