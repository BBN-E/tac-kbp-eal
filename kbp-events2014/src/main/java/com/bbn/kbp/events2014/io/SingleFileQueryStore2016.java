package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedQuery2016;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.QueryAssessment2016;
import com.bbn.kbp.events2014.QueryResponse2016;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A store for 2016 Queries, supporting reading/writing to/from a single file
 */
public final class SingleFileQueryStore2016 implements QueryStore2016 {

  private final static Splitter commaSplitter = Splitter.on(",");
  private final static Splitter dashSplitter = Splitter.on("-");
  private final static Joiner commaJoiner = Joiner.on(",");
  private final static Joiner dashJoiner = Joiner.on("-");
  private final static Joiner tabJoiner = Joiner.on("\t");

  private final Set<QueryResponse2016> queries;
  private final Map<QueryResponse2016, String> metadata;
  private final Multimap<Symbol, QueryResponse2016> bySystemID;
  private final Multimap<Symbol, QueryResponse2016> byDocID;
  private final Multimap<Symbol, QueryResponse2016> byQueryID;
  // may never contain an AssessedQuery2016 with an assessment of QueryAssessment2016.UNASSASSED
  private final Map<QueryResponse2016, AssessedQuery2016> assessments;


  private SingleFileQueryStore2016(final Iterable<QueryResponse2016> queries,
      final Map<QueryResponse2016, String> metadata, final Map<QueryResponse2016, AssessedQuery2016> assessments) {
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

    for (final QueryResponse2016 q : this.queries) {
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

  public ImmutableSet<QueryResponse2016> queries() {
    return ImmutableSet.copyOf(queries);
  }

  public void saveTo(final File f) throws FileNotFoundException {
    final PrintWriter out = new PrintWriter(f);
    for (final QueryResponse2016 q : queries) {
      final Optional<String> metadata = Optional.fromNullable(this.metadata.get(q));
      if (metadata.isPresent()) {
        out.println("#" + metadata.get());
      }
      final Optional<AssessedQuery2016> assessmentOpt =
          Optional.fromNullable(this.assessments.get(q));
      final QueryAssessment2016 assessment;
      if (assessmentOpt.isPresent()) {
        assessment = assessmentOpt.get().assessment();
      } else {
        assessment = QueryAssessment2016.UNASSASSED;
      }
      final ImmutableList.Builder<String> pjStrings = ImmutableList.builder();
      for (final CharOffsetSpan pj : q.predicateJustifications()) {
        pjStrings.add(dashJoiner.join(pj.startInclusive(), pj.endInclusive()));
      }
      final String pjString = commaJoiner.join(pjStrings.build());
      final String line =
          tabJoiner.join(q.queryID(), q.docID(), q.systemID(), pjString, assessment.name());
      out.println(line);
    }
    out.close();
  }

  public static SingleFileQueryStore2016 open(final File f) throws IOException {
    final List<String> lines = Files.readLines(f, Charsets.UTF_8);
    final List<QueryResponse2016> queries = Lists.newArrayList();
    final Map<QueryResponse2016, String> metadata = Maps.newHashMap();
    final Map<QueryResponse2016, AssessedQuery2016> assessments = Maps.newHashMap();
    Optional<String> lastMetadata = Optional.absent();
    for (final String line : lines) {
      if (line.startsWith("#")) {
        lastMetadata = Optional.of(line.trim().substring(1));
      } else {
        final String[] parts = line.trim().split("\t");
        checkArgument(parts.length == 5, "expected five columns, but got " + parts.length);
        final Symbol queryID = Symbol.from(parts[0]);
        final Symbol docID = Symbol.from(parts[1]);
        final Symbol systemID = Symbol.from(parts[2]);
        final ImmutableSortedSet<CharOffsetSpan> spans = extractPJSpans(parts[3]);
        final QueryAssessment2016 assessment = QueryAssessment2016.valueOf(parts[4]);
        final QueryResponse2016 query = QueryResponse2016.builder().queryID(queryID).docID(docID).systemID(systemID)
            .addAllPredicateJustifications(spans).build();
        queries.add(query);
        if (!assessment.equals(QueryAssessment2016.UNASSASSED)) {
          assessments
              .put(query, AssessedQuery2016.builder().query(query).assessment(assessment).build());
        }
        if (lastMetadata.isPresent()) {
          metadata.put(query, lastMetadata.get());
          lastMetadata = Optional.absent();
        }
      }
    }

    return new SingleFileQueryStore2016(queries, metadata, assessments);
  }


  public static SingleFileQueryStore2016 createEmpty() {
    return new SingleFileQueryStore2016(Lists.<QueryResponse2016>newArrayList(), Maps.<QueryResponse2016, String>newHashMap(),
        Maps.<QueryResponse2016, AssessedQuery2016>newHashMap());
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


  private static Ordering<QueryResponse2016> byQueryID() {
    return new Ordering<QueryResponse2016>() {
      @Override
      public int compare(@Nullable final QueryResponse2016 left, @Nullable final QueryResponse2016 right) {
        checkNotNull(left);
        checkNotNull(right);
        return left.queryID().asString().compareTo(right.queryID().asString());
      }
    };
  }

  private static Ordering<QueryResponse2016> byDocID() {
    return new Ordering<QueryResponse2016>() {
      @Override
      public int compare(@Nullable final QueryResponse2016 left, @Nullable final QueryResponse2016 right) {
        checkNotNull(left);
        checkNotNull(right);
        return left.docID().asString().compareTo(right.docID().asString());
      }
    };
  }

  private static Ordering<QueryResponse2016> bySystemID() {
    return new Ordering<QueryResponse2016>() {
      @Override
      public int compare(@Nullable final QueryResponse2016 left, @Nullable final QueryResponse2016 right) {
        checkNotNull(left);
        checkNotNull(right);
        return left.systemID().asString().compareTo(right.systemID().asString());
      }
    };
  }

  private static Ordering<QueryResponse2016> byPJOffsets() {
    return new Ordering<QueryResponse2016>() {
      @Override
      public int compare(@Nullable final QueryResponse2016 left, @Nullable final QueryResponse2016 right) {
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
