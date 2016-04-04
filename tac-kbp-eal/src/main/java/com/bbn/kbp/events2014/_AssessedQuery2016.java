package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;

import org.immutables.func.Functional;
import org.immutables.value.Value;

@TextGroupPublicImmutable
@Value.Immutable
@Functional
abstract class _AssessedQuery2016 {

  public abstract QueryResponse2016 query();

  public abstract QueryAssessment2016 assessment();

}
