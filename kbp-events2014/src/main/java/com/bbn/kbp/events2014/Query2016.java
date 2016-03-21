package com.bbn.kbp.events2014;


import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;


public final class Query2016 implements Query {

  private final ImmutableList<CharOffsetSpan> spans;
  private final Symbol queryID;
  private final Symbol docID;
  private final Symbol systemID;

  private Query2016(final Iterable<CharOffsetSpan> spans, final Symbol queryID,
      final Symbol docID, final Symbol systemID) {
    this.queryID = checkNotNull(queryID);
    this.docID = checkNotNull(docID);
    this.systemID = checkNotNull(systemID);
    this.spans = Ordering.natural().immutableSortedCopy(spans);
  }

  public static Query2016 create(final Symbol queryID, final Symbol docID, final Symbol systemID,
      final Iterable<CharOffsetSpan> spans) {
    return new Query2016(spans, queryID, docID, systemID);
  }

  /**
   * @return a list of predicate justifications, sorted using the natural Ordering of the
   * CharOffsetSpan
   */
  public ImmutableList<CharOffsetSpan> predicateJustifications() {
    return spans;
  }

  @Override
  public Symbol queryID() {
    return queryID;
  }

  @Override
  public Symbol docID() {
    return docID;
  }

  @Override
  public Symbol systemID() {
    return systemID;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Query2016 query2016 = (Query2016) o;
    return Objects.equals(spans, query2016.spans) &&
        Objects.equals(queryID, query2016.queryID) &&
        Objects.equals(docID, query2016.docID) &&
        Objects.equals(systemID, query2016.systemID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spans, queryID, docID, systemID);
  }
}
