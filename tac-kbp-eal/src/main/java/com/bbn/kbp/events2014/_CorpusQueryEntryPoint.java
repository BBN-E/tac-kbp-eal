package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@TextGroupPublicImmutable
@Functional
abstract class _CorpusQueryEntryPoint {

  @Value.Parameter
  public abstract Symbol docID();

  @Value.Parameter
  public abstract Symbol eventType();

  @Value.Parameter
  public abstract Symbol role();

  @Value.Parameter
  public abstract KBPString canonicalArgumentString();

  @Value.Parameter
  public abstract CharOffsetSpan predicateJustification();

  @Value.Check
  protected void check() {
    checkArgument(!docID().asString().isEmpty(), "Doc ID may not be empty");
    checkArgument(!docID().asString().contains("\t"), "Doc ID may not contain a tab");
    checkArgument(!eventType().asString().isEmpty(), "Event type may not be empty");
    checkArgument(!eventType().asString().contains("\t"), "Event type may not contain a tab");
    checkArgument(!role().asString().isEmpty(), "Role may not be empty");
    checkArgument(!role().asString().contains("\t"), "Role may not contain a tab");
  }
}
