package com.bbn.kbp.events2014;


import com.bbn.bue.common.TextGroupPublicImmutable;

import com.google.common.collect.ImmutableList;

import org.immutables.func.Functional;
import org.immutables.value.Value;


@TextGroupPublicImmutable
@Value.Immutable
@Functional
public abstract class _Query2016 implements Query {

  public abstract ImmutableList<CharOffsetSpan> predicateJustifications();
  public abstract QueryAssessment queryAssessment();
}
