package com.bbn.kbp.events2014;


import com.bbn.bue.common.TextGroupPublicImmutable;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.SortedSet;


@TextGroupPublicImmutable
@Value.Immutable
@Functional
public abstract class _Query2016 implements Query {

  @Value.NaturalOrder
  public abstract SortedSet<CharOffsetSpan> predicateJustifications();

  public abstract QueryAssessment queryAssessment();
}
