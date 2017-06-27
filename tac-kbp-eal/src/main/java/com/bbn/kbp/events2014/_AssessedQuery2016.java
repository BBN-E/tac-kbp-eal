package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;

import org.immutables.func.Functional;
import org.immutables.value.Value;

/**
 * A corpus-level query response together with its assessment.
 */
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@TextGroupPublicImmutable
@Value.Immutable
@Functional
abstract class _AssessedQuery2016 {

  public abstract QueryResponse2016 queryResponse();

  public abstract QueryAssessment2016 assessment();

}
