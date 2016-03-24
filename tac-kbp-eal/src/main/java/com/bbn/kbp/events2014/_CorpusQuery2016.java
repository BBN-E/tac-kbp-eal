package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;

import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@Functional
@TextGroupPublicImmutable
abstract class _CorpusQuery2016 {

  @Value.Parameter
  public abstract String id();

  @Value.Parameter
  public abstract ImmutableSet<CorpusQueryEntryPoint> entryPoints();

  @Value.Check
  protected void check() {
    checkArgument(!id().isEmpty(), "Query ID may not be empty");
    checkArgument(!id().contains("\t"), "Query ID may not contain a tab");
    checkArgument(!entryPoints().isEmpty(), "Query may not lack entry points");
  }
}
