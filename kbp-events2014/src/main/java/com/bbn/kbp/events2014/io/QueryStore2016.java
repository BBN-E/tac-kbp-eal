package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedQuery2016;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.Query2016;
import com.bbn.kbp.events2014.QueryAssessment;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class QueryStore2016 {

  private final static Splitter commaSplitter = Splitter.on(",");
  private final static Splitter dashSplitter = Splitter.on("-");

  private final Set<Query2016> queries;
  private final Map<Query2016, String> metadata;
  private final Multimap<Symbol, Query2016> bySystemID;
  private final Multimap<Symbol, Query2016> byDocID;
  private final Multimap<Symbol, Query2016> byQueryID;
  // may never contain an AssessedQuery2016 with an assessment of QueryAssessment.UNASSASSED
  private final Map<Query2016, AssessedQuery2016> assessments;


  private QueryStore2016(final Iterable<Query2016> queries,
      final Map<Query2016, String> metadata, final Map<Query2016, AssessedQuery2016> assessments) {
    this.assessments = checkNotNull(assessments);
    this.metadata = checkNotNull(metadata);
    checkNotNull(queries);
    this.queries = new TreeSet<>(Orderings.by2016Ordering.ordering());
    this.queries.addAll(ImmutableList.copyOf(queries));

    checkArgument(this.queries.containsAll(assessments.keySet()),
        "Assessments have an unknown query!");
    checkArgument(this.queries.containsAll(metadata.keySet()), "Metadata has an unknown query!");

    // keep these sorted
    this.byQueryID = TreeMultimap.create(Ordering.usingToString(), by2016Ordering());
    this.byDocID = TreeMultimap.create(Ordering.usingToString(), by2016Ordering());
    this.bySystemID = TreeMultimap.create(Ordering.usingToString(), by2016Ordering());

    for (final Query2016 q : this.queries) {
      byQueryID.put(q.queryID(), q);
      byDocID.put(q.docID(), q);
      bySystemID.put(q.systemID(), q);
    }
  }

  public ImmutableSet<Symbol> queryIDs() {
    return ImmutableSet.copyOf(byQueryID.keySet());
  }

  public ImmutableSet<Symbol> docIDs() {
    return ImmutableSet.copyOf(byDocID.keySet());
  }

  public ImmutableSet<Symbol> systemIDs() {
    return ImmutableSet.copyOf(bySystemID.keySet());
  }

  public ImmutableSet<Query2016> queries() {
    return ImmutableSet.copyOf(queries);
  }


  public void saveTo(final File f) {
    // TODO
  }

  public static QueryStore2016 open(final File f) throws IOException {
    final List<String> lines = Files.readLines(f, Charsets.UTF_8);
    final List<Query2016> queries = Lists.newArrayList();
    final Map<Query2016, String> metadata = Maps.newHashMap();
    final Map<Query2016, AssessedQuery2016> assessments = Maps.newHashMap();
    Optional<String> lastMetadata = Optional.absent();
    for (final String line : lines) {
      if (line.startsWith("#")) {
        lastMetadata = Optional.of(line.trim());
      } else {
        final String[] parts = line.trim().split("\t");
        checkArgument(parts.length == 5, "expected five columns, but got " + parts.length);
        final Symbol queryID = Symbol.from(parts[0]);
        final Symbol docID = Symbol.from(parts[1]);
        final Symbol systemID = Symbol.from(parts[2]);
        final ImmutableSortedSet<CharOffsetSpan> spans = extractPJSpans(parts[3]);
        final QueryAssessment assessment = QueryAssessment.valueOf(parts[4]);
        final Query2016 query = Query2016.builder().queryID(queryID).docID(docID).systemID(systemID)
            .addAllPredicateJustifications(spans).build();
        assessments.put(query, AssessedQuery2016.create(query, assessment));
        queries.add(query);
        if (lastMetadata.isPresent()) {
          metadata.put(query, lastMetadata.get());
          lastMetadata = Optional.absent();
        }
      }
    }

    return new QueryStore2016(queries, metadata, assessments);
  }


  public static QueryStore2016 createEmpty() {
    // TODO should this take a file?
    return null;
  }

  private static ImmutableSortedSet<CharOffsetSpan> extractPJSpans(final String part) {
    final ImmutableSortedSet.Builder<CharOffsetSpan> spans = ImmutableSortedSet.naturalOrder();
    for (final String spanString : commaSplitter.split(part.trim())) {
      final List<String> spanParts = ImmutableList.copyOf(dashSplitter.split(spanString));
      checkState(spanParts.size() == 2,
          "Expected two components to the span, but got " + spanParts.size());
      final int start = Integer.parseInt(spanParts.get(0));
      final int end = Integer.parseInt(spanParts.get(1));
      spans.add(CharOffsetSpan.fromOffsetsOnly(start, end));
    }
    return spans.build();
  }


  private static Ordering<Query2016> byQueryID() {
    return new Ordering<Query2016>() {
      @Override
      public int compare(@Nullable final Query2016 left, @Nullable final Query2016 right) {
        checkNotNull(left);
        checkNotNull(right);
        return left.queryID().asString().compareTo(right.queryID().asString());
      }
    };
  }

  private static Ordering<Query2016> byDocID() {
    return new Ordering<Query2016>() {
      @Override
      public int compare(@Nullable final Query2016 left, @Nullable final Query2016 right) {
        checkNotNull(left);
        checkNotNull(right);
        return left.docID().asString().compareTo(right.docID().asString());
      }
    };
  }

  private static Ordering<Query2016> bySystemID() {
    return new Ordering<Query2016>() {
      @Override
      public int compare(@Nullable final Query2016 left, @Nullable final Query2016 right) {
        checkNotNull(left);
        checkNotNull(right);
        return left.systemID().asString().compareTo(right.systemID().asString());
      }
    };
  }

  private static Ordering<Query2016> byPJOffsets() {
    return new Ordering<Query2016>() {
      @Override
      public int compare(@Nullable final Query2016 left, @Nullable final Query2016 right) {
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

  private static Ordering<Query2016> by2016Ordering() {
    return Orderings.byQueryID.ordering().compound(Orderings.byDocID.ordering())
        .compound(Orderings.bySystemID.ordering()).compound(Orderings.byPJOffsets.ordering());
  }


  enum Orderings {
    byQueryID(QueryStore2016.<Query2016>byQueryID()),
    byDocID(QueryStore2016.<Query2016>byDocID()),
    bySystemID(QueryStore2016.<Query2016>bySystemID()),
    byPJOffsets(QueryStore2016.byPJOffsets()),
    by2016Ordering(QueryStore2016.by2016Ordering());

    private final Ordering<Query2016> ordering;

    Orderings(final Ordering<Query2016> ordering) {
      this.ordering = ordering;
    }

    public final Ordering<Query2016> ordering() {
      return ordering;
    }
  }

}
