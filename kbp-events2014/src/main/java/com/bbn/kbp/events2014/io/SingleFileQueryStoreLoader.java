package com.bbn.kbp.events2014.io;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedQuery2016;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.QueryAssessment2016;
import com.bbn.kbp.events2014.QueryResponse2016;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public final class SingleFileQueryStoreLoader {

  private final static Splitter commaSplitter = Splitter.on(",");
  private final static Splitter dashSplitter = Splitter.on("-");


  private SingleFileQueryStoreLoader() {

  }

  public QueryStore2016 open2016(final File f) throws IOException {
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
        final QueryResponse2016 query =
            QueryResponse2016.builder().queryID(queryID).docID(docID).systemID(systemID)
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
    return QueryStore2016.builder().addAllQueries(queries).assessments(assessments)
        .metadata(metadata).build();
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

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {


    private Builder() {
    }

    public SingleFileQueryStoreLoader build() {
      return new SingleFileQueryStoreLoader();
    }
  }
}
