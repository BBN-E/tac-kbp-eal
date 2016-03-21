package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedQuery2016;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.QueryResponse2016;
import com.bbn.kbp.events2014.QueryResponse2016Functions;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A store for 2016 Queries, supporting reading/writing to/from a single file
 */
public final class SingleFileQueryStore2016 implements QueryStore2016 {


  private final Set<QueryResponse2016> queries;
  private final Map<QueryResponse2016, String> metadata;
  // may never contain an AssessedQuery2016 with an assessment of QueryAssessment2016.UNASSASSED
  private final Map<QueryResponse2016, AssessedQuery2016> assessments;


  private SingleFileQueryStore2016(final Iterable<QueryResponse2016> queries,
      final Map<QueryResponse2016, String> metadata,
      final Map<QueryResponse2016, AssessedQuery2016> assessments) {
    this.assessments = checkNotNull(assessments);
    this.metadata = checkNotNull(metadata);
    checkNotNull(queries);
    this.queries = new TreeSet<>(Orderings.by2016Ordering.ordering());
    this.queries.addAll(ImmutableList.copyOf(queries));

    checkArgument(this.queries.containsAll(assessments.keySet()),
        "Assessments have an unknown query!");
    checkArgument(this.queries.containsAll(metadata.keySet()), "Metadata has an unknown query!");

  }

  public static SingleFileQueryStore2016 create(final Iterable<QueryResponse2016> queries,
      final Map<QueryResponse2016, String> metadata,
      final Map<QueryResponse2016, AssessedQuery2016> assessments) {
    return new SingleFileQueryStore2016(queries, metadata, assessments);
  }

  public ImmutableSet<Symbol> queryIDs() {
    return FluentIterable.from(queries).index(QueryResponse2016Functions.queryID()).keySet();
  }

  public ImmutableSet<Symbol> docIDs() {
    return FluentIterable.from(queries).index(QueryResponse2016Functions.docID()).keySet();
  }

  public ImmutableSet<Symbol> systemIDs() {
    return FluentIterable.from(queries).index(QueryResponse2016Functions.systemID()).keySet();
  }

  public ImmutableMap<QueryResponse2016, String> metadata() {
    return ImmutableMap.copyOf(metadata);
  }

  public ImmutableMap<QueryResponse2016, AssessedQuery2016> assessments() {
    return ImmutableMap.copyOf(assessments);
  }

  public ImmutableSet<QueryResponse2016> queries() {
    return ImmutableSet.copyOf(queries);
  }

  public static SingleFileQueryStore2016 createEmpty() {
    return new SingleFileQueryStore2016(Lists.<QueryResponse2016>newArrayList(),
        Maps.<QueryResponse2016, String>newHashMap(),
        Maps.<QueryResponse2016, AssessedQuery2016>newHashMap());
  }


  private static Ordering<QueryResponse2016> byQueryID() {
    return new Ordering<QueryResponse2016>() {
      @Override
      public int compare(@Nullable final QueryResponse2016 left,
          @Nullable final QueryResponse2016 right) {
        checkNotNull(left);
        checkNotNull(right);
        return left.queryID().asString().compareTo(right.queryID().asString());
      }
    };
  }

  private static Ordering<QueryResponse2016> byDocID() {
    return new Ordering<QueryResponse2016>() {
      @Override
      public int compare(@Nullable final QueryResponse2016 left,
          @Nullable final QueryResponse2016 right) {
        checkNotNull(left);
        checkNotNull(right);
        return left.docID().asString().compareTo(right.docID().asString());
      }
    };
  }

  private static Ordering<QueryResponse2016> bySystemID() {
    return new Ordering<QueryResponse2016>() {
      @Override
      public int compare(@Nullable final QueryResponse2016 left,
          @Nullable final QueryResponse2016 right) {
        checkNotNull(left);
        checkNotNull(right);
        return left.systemID().asString().compareTo(right.systemID().asString());
      }
    };
  }

  private static Ordering<QueryResponse2016> byPJOffsets() {
    return new Ordering<QueryResponse2016>() {
      @Override
      public int compare(@Nullable final QueryResponse2016 left,
          @Nullable final QueryResponse2016 right) {
        checkNotNull(left);
        checkNotNull(right);
        checkArgument(left.docID() == right.docID(),
            "Refusing to compare PJs between different documents!");
        // iterate through comparing the PJs. These are guaranteed to be sorted by lowest index, then shortest length.
        final List<CharOffsetSpan> leftSpans = ImmutableList.copyOf(left.predicateJustifications());
        final List<CharOffsetSpan> rightSpans =
            ImmutableList.copyOf(right.predicateJustifications());
        for (int i = 0; i < leftSpans.size() && i < rightSpans.size(); i++) {
          final int cmp = leftSpans.get(i)
              .compareTo(rightSpans.get(i));
          if (cmp != 0) {
            return cmp;
          }
        }
        // if everything has been the same, the shorter list appears earlier
        if (leftSpans.size() < rightSpans.size()) {
          return -1;
        } else if (leftSpans.size() > rightSpans.size()) {
          return 1;
        }
        return 0;
      }
    };
  }

  private static Ordering<QueryResponse2016> by2016Ordering() {
    return Orderings.byQueryID.ordering().compound(Orderings.byDocID.ordering())
        .compound(Orderings.bySystemID.ordering()).compound(Orderings.byPJOffsets.ordering());
  }

  enum Orderings {
    byQueryID(SingleFileQueryStore2016.<QueryResponse2016>byQueryID()),
    byDocID(SingleFileQueryStore2016.<QueryResponse2016>byDocID()),
    bySystemID(SingleFileQueryStore2016.<QueryResponse2016>bySystemID()),
    byPJOffsets(SingleFileQueryStore2016.byPJOffsets()),
    by2016Ordering(SingleFileQueryStore2016.by2016Ordering());

    private final Ordering<QueryResponse2016> ordering;

    Orderings(final Ordering<QueryResponse2016> ordering) {
      this.ordering = ordering;
    }

    public final Ordering<QueryResponse2016> ordering() {
      return ordering;
    }
  }

}
