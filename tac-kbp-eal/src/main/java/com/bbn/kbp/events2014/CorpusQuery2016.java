package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@Functional
@TextGroupImmutable
public abstract class  CorpusQuery2016 {

  @Value.Parameter
  public abstract Symbol id();

  @Value.Parameter
  public abstract ImmutableSet<CorpusQueryEntryPoint> entryPoints();

  @Value.Check
  protected void check() {
    checkArgument(!id().asString().isEmpty(), "Query ID may not be empty");
    checkArgument(!id().asString().contains("\t"), "Query ID may not contain a tab");
    checkArgument(!entryPoints().isEmpty(), "Query may not lack entry points");
  }

  public static class Builder extends ImmutableCorpusQuery2016.Builder {}
}
