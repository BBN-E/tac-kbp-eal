package com.bbn.kbp.events2014;


import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableList;

import org.immutables.func.Functional;
import org.immutables.value.Value;


@TextGroupPublicImmutable
@Value.Immutable
@Functional
public abstract class _Query2016 {

  public abstract Symbol queryID();
  public abstract Symbol docID();
  public abstract Symbol systemID();
  public abstract ImmutableList<CharOffsetSpan> predicateJustifications();
  public abstract QueryAssessment queryAssessment();
}
