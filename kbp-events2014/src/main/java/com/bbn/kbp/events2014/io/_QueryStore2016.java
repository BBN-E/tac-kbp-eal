package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedQuery2016;
import com.bbn.kbp.events2014.QueryResponse2016;
import com.bbn.kbp.events2014.QueryResponse2016Functions;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

/**
 * A store for 2016 Queries, supporting reading/writing to/from a single file
 */
@TextGroupPublicImmutable
@Value.Immutable
@Functional
public abstract class _QueryStore2016 {

  public ImmutableSet<Symbol> queryIDs() {
    return FluentIterable.from(queries()).index(QueryResponse2016Functions.queryID()).keySet();
  }

  public ImmutableSet<Symbol> docIDs() {
    return FluentIterable.from(queries()).index(QueryResponse2016Functions.docID()).keySet();
  }

  public ImmutableSet<Symbol> systemIDs() {
    return FluentIterable.from(queries()).index(QueryResponse2016Functions.systemID()).keySet();
  }

  // may never contain an AssessedQuery2016 with an assessment of QueryAssessment2016.UNASSASSED
  public abstract ImmutableMap<QueryResponse2016, String> metadata();

  public abstract ImmutableMap<QueryResponse2016, AssessedQuery2016> assessments();

  public abstract ImmutableSet<QueryResponse2016> queries();
}
