package com.bbn.kbp.events2014.transformers;


import com.bbn.bue.common.io.ByteArraySink;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestQuoteFilter {

  @Test
  public void testQuotedRegionComputation() throws IOException {
    final Map<String, ImmutableRangeSet<Integer>> testCases = ImmutableMap.of(
        "Foo <quote>bar <quote>baz</quote> <quote>meep</quote></quote> blah <quote>another</quote>",
        ImmutableRangeSet.<Integer>builder().add(Range.closed(4, 60)).add(Range.closed(67, 88))
            .build(),
        "<quote>lalala</quote>", ImmutableRangeSet.of(Range.closed(0, 20)),
        "No quotes!", ImmutableRangeSet.<Integer>of());

    for (final Map.Entry<String, ImmutableRangeSet<Integer>> entry : testCases.entrySet()) {
      final Symbol docid = Symbol.from("dummy");

      final QuoteFilter reference =
          QuoteFilter.createFromBannedRegions(ImmutableMap.of(docid, entry.getValue()));
      final QuoteFilter computed = QuoteFilter.createFromOriginalText(ImmutableMap.of(docid,
          CharSource.wrap(entry.getKey())));

      assertEquals(reference, computed);
    }
  }

  @Test
  public void testSerialization() throws IOException {
    final QuoteFilter reference = QuoteFilter.createFromBannedRegions(
        ImmutableMap.of(
            Symbol.from("dummy"),
            ImmutableRangeSet.<Integer>builder().add(Range.closed(4, 60)).add(Range.closed(67, 88))
                .build(),
            Symbol.from("dummy2"), ImmutableRangeSet.of(Range.closed(0, 20))));

    final ByteArraySink sink = new ByteArraySink();
    reference.saveTo(sink);
    final ByteSource source = ByteSource.wrap(sink.toByteArray());
    final Object restored = QuoteFilter.loadFrom(source);
    assertEquals(reference, restored);
  }
}
