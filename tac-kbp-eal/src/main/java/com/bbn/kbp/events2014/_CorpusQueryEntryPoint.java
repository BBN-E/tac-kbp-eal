package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An "entry point" of a corpus-level query.  Every query will have one or more entry points by
 * which a query can be aligned to system event output.
 */
@Value.Immutable
@TextGroupPublicImmutable
@Functional
abstract class _CorpusQueryEntryPoint {

  @Value.Parameter
  public abstract Symbol docID();

  @Value.Parameter
  public abstract Symbol hopperID();

  @Value.Parameter
  public abstract Symbol role();

  @Value.Parameter
  public abstract Symbol entity();

  @Value.Check
  protected void check() {
    checkArgument(!docID().asString().isEmpty(), "Doc ID may not be empty");
    checkArgument(!docID().asString().contains("\t"), "Doc ID may not contain a tab");
    checkArgument(!hopperID().asString().isEmpty(), "Hopper ID may not be empty");
    checkArgument(!hopperID().asString().contains("\t"), "Hopper ID may not contain a tab");
    checkArgument(!role().asString().isEmpty(), "Role may not be empty");
    checkArgument(!role().asString().contains("\t"), "Role may not contain a tab");
    checkArgument(!entity().asString().isEmpty(), "Entity may not be empty");
    checkArgument(!entity().asString().contains("\t"), "Entity may not contain a tab");
  }
}
