package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedQuery2016;
import com.bbn.kbp.events2014.QueryAssessment2016;
import com.bbn.kbp.events2014.QueryResponse2016;
import com.bbn.kbp.events2014.QueryResponse2016Functions;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkState;

/**
 * An immutable collection of {@link QueryResponse2016}, {@link QueryAssessment2016}, and associated
 * metadata.
 */
@TextGroupPublicImmutable
@Value.Immutable
@Functional
public abstract class _QueryStore2016 {

  /**
   * @return A derived set from {@link #queries()} of query IDs
   */
  public ImmutableSet<Symbol> queryIDs() {
    return FluentIterable.from(queries()).index(QueryResponse2016Functions.queryID()).keySet();
  }

  /**
   * @return A derived set from {@link #queries()} of Document IDs
   */
  public ImmutableSet<Symbol> docIDs() {
    return FluentIterable.from(queries()).index(QueryResponse2016Functions.docID()).keySet();
  }

  /**
   * @return A derived set from {@link #queries()} of System IDs
   */
  public ImmutableSet<Symbol> systemIDs() {
    return FluentIterable.from(queries()).index(QueryResponse2016Functions.systemID()).keySet();
  }

  @Value.Check
  protected void check() {
    checkState(queries().containsAll(metadata().keySet()),
        "Metadata contained an unknown query; error in constructing the store!");
    checkState(queries().containsAll(assessments().keySet()),
        "Assessments contained an unknown query; error in constructing the store!");
    for (final QueryResponse2016 q : assessments().keySet()) {
      checkState(assessments().get(q).query().equals(q),
          "Query " + q + " mapped to an incorrect assessment " + assessments().get(q));
      checkState(!assessments().get(q).assessment().equals(QueryAssessment2016.UNASSASSED),
          "No storing of UNASSESSED queries in the assessments allowed");
    }
  }


  /**
   * A map of {@link QueryResponse2016} to associated metadata, if any. The map may contain null
   * entries.
   */
  public abstract ImmutableMap<QueryResponse2016, String> metadata();

  /**
   * A map of {@link QueryResponse2016} to {@link AssessedQuery2016} may never contain an {@link
   * AssessedQuery2016} with an assessment of {@link QueryAssessment2016#UNASSASSED}
   */
  public abstract ImmutableMap<QueryResponse2016, AssessedQuery2016> assessments();

  public abstract ImmutableSet<QueryResponse2016> queries();
}
