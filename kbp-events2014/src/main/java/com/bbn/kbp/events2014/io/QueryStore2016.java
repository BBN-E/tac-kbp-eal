package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.Query2016;
import com.bbn.kbp.events2014.QueryAssessment;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public class QueryStore2016 implements QueryStore<Query2016> {

  private final static Splitter commaSplitter = Splitter.on(",");

  // TODO should this be immutable? probably not
  private final ImmutableSet<Query2016> queries;
  private final ImmutableMap<Query2016, String> metadata;
  private final ImmutableMultimap<Symbol, Query2016> bySystemID;
  private final ImmutableMultimap<Symbol, Query2016> byDocID;
  private final ImmutableMap<Symbol, Query2016> byQueryID;


  private QueryStore2016(final Iterable<Query2016> queries,
      final Map<Query2016, String> metadata) {
    this.metadata = ImmutableMap.copyOf(metadata);
    this.queries = ImmutableSet.copyOf(queries);

    final ImmutableMap.Builder<Symbol, Query2016> byQueryID = ImmutableMap.builder();
    final ImmutableMultimap.Builder<Symbol, Query2016> byDocID = ImmutableMultimap.builder();
    final ImmutableMultimap.Builder<Symbol, Query2016> bySystemID = ImmutableMultimap.builder();

    for(final Query2016 q: this.queries) {
      byQueryID.put(q.queryID(), q);
      byDocID.put(q.docID(), q);
      bySystemID.put(q.systemID(), q);
    }

    this.byQueryID = byQueryID.build();
    this.byDocID = byDocID.build();
    this.bySystemID = bySystemID.build();

  }

  @Override
  public ImmutableSet<Symbol> queryIDs() {
    return byQueryID.keySet();
  }

  @Override
  public ImmutableSet<Symbol> docIDs() {
    return byDocID.keySet();
  }

  @Override
  public ImmutableSet<Symbol> systemIDs() {
    return bySystemID.keySet();
  }

  @Override
  public ImmutableSet<Query2016> queries() {
    return queries;
  }


  public void saveTo(final File f) {
    // TODO
  }

  public static QueryStore2016 open(final File f) throws IOException {
    final List<String> lines = Files.readLines(f, Charsets.UTF_8);
    final ImmutableList.Builder<Query2016> queries = ImmutableList.builder();
    final ImmutableMap.Builder<Query2016, String> metadata = ImmutableMap.builder();
    Optional<String> lastMetadata = Optional.absent();
    for(final String line: lines) {
      if(line.startsWith("#")) {
        lastMetadata = Optional.of(line.trim());
      } else {
        final String[] parts = line.trim().split("\t");
        checkArgument(parts.length == 5, "expected five columns, but got " + parts.length);
        final Symbol queryID = Symbol.from(parts[0]);
        final Symbol docID = Symbol.from(parts[1]);
        final Symbol systemID = Symbol.from(parts[2]);
        final ImmutableList<CharOffsetSpan> spans = extractPJSpans(parts[3]);
        final QueryAssessment assessment = QueryAssessment.valueOf(parts[4]);
        // TODO make a query
      }
    }

    return new QueryStore2016(queries.build(), metadata.build());
  }


  private static ImmutableList<CharOffsetSpan> extractPJSpans(final String part) {
    // TODO implement
    return null;
  }

  public static QueryStore2016 createEmpty() {
    // TODO should this take a file?
    return null;
  }

}
