package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Function to transform a {@link com.bbn.kbp.events2014.SystemOutput} to another one
 * which is the same except all responses with base fillers or CASes from within
 * {@code <quote>} regions have been removed.
 *
 * Applying this to a {@link com.bbn.kbp.events2014.SystemOutput} with an unknown document ID
 * will throw an except in order to prevent accidental mismatches the filter and the
 * input it is applied to.
 */
public final class QuoteFilter implements Function<SystemOutput, SystemOutput>  {
    private static final Set<String> BANNED_REGION_STARTS =
            ImmutableSet.of("<quote>",
                    // handle case of <quote orig_author="foo">
                    "<quote ");
    private static final String BANNED_REGION_END = "</quote>";

    private final Map<Symbol, ImmutableRangeSet<Integer>> docIdToBannedRegions;

    @Override
    public SystemOutput apply(final SystemOutput input) {
        final ImmutableRangeSet<Integer> bannedRegions = docIdToBannedRegions.get(input.docId());
        if (bannedRegions == null) {
            throw new RuntimeException(String.format(
               "QuoteFilter does not know about document ID %s", input.docId()));
        }

        return input.copyWithFilteredResponses(
           new Predicate<Scored<Response>>() {
               public boolean apply(Scored<Response> x) {
                   return !isInQuote(input.docId(), x.item().baseFiller())
                       && !isInQuote(input.docId(), x.item().canonicalArgument().charOffsetSpan());
               }
           }
        );
    }

    public boolean isInQuote(Symbol docId, CharOffsetSpan span) {
        final ImmutableRangeSet<Integer> bannedRegions = docIdToBannedRegions.get(docId);
        if (bannedRegions == null) {
            throw new RuntimeException(String.format(
                    "QuoteFilter does not know about document ID %s", docId));
        }

        return inBannedSet(span, bannedRegions);
    }

    private static boolean inBannedSet(CharOffsetSpan span, RangeSet<Integer> bannedRegions) {
        return bannedRegions.contains(span.startInclusive())
                || bannedRegions.contains(span.endInclusive());
    }

    public static QuoteFilter createFromOriginalText(Map<Symbol, ? extends CharSource> originalTexts) throws IOException {
        checkNotNull(originalTexts);

        final ImmutableMap.Builder<Symbol, ImmutableRangeSet<Integer>> ret = ImmutableMap.builder();
        for (final Map.Entry<Symbol, ? extends CharSource> originalTextPair : originalTexts.entrySet()) {
            final Symbol docID = originalTextPair.getKey();
            final CharSource originalTextSource = originalTextPair.getValue();

            ret.put(docID, computeQuotedRegions(originalTextSource.read()));
        }
        return createFromBannedRegions(ret.build());
    }

    /**
     * Given the string contents of a document, will return the offset ranges of
     * those portions within <quote> tags. This does not pay attention to the
     * attributes of the quote tags.
     * @param s
     * @return
     */
    public static ImmutableRangeSet<Integer> computeQuotedRegions(String s) {
        checkNotNull(s);
        final ImmutableRangeSet.Builder<Integer> ret = ImmutableRangeSet.builder();

        // current search position
        int curPos = 0;
        // search for first opening <quote> tag
        int regionStart = StringUtils.earliestIndexOfAny(s, BANNED_REGION_STARTS, curPos);

        // if we found a <quote> tag
        while (regionStart != -1) {
            curPos = regionStart;
            int nestingCount = 1;

            // until we find the matching </quote> tag..
            while (nestingCount > 0) {
                final int nextStart = StringUtils.earliestIndexOfAny(s, BANNED_REGION_STARTS, curPos + 1);
                final int nextEnd = s.indexOf(BANNED_REGION_END, curPos+1);

                if (nextEnd == -1) {
                    // (a) uh-oh, we reached the end without ever finding a match
                    throw new RuntimeException(String.format("<quote> tag opened at %d is never closed.", regionStart));
                } else if (nextStart == -1 || nextEnd < nextStart) {
                    // (b) we find a </quote> before another <quote>, so
                    // we reduce the nesting level and remember the location
                    // of the closing tag
                    --nestingCount;
                    curPos = nextEnd;
                } else if (nextEnd > nextStart) {
                    // (c) we found another <quote> before the end of the current
                    // <quote>, so there must be nesting.
                    ++nestingCount;
                    curPos = nextStart;
                } else {
                    throw new RuntimeException("It is impossible for nextEnd == nextStart");
                }
            }

            // the only way we successfully exited is case (b)
            // where curPos is the beginning of the </quote> tag
            ret.add(Range.closed(regionStart, curPos + BANNED_REGION_END.length()-1));

            regionStart = StringUtils.earliestIndexOfAny(s, BANNED_REGION_STARTS, curPos + 1);
        }

        return ret.build();
    }

    protected Map<Symbol, ImmutableRangeSet<Integer>> docIdToBannedRegions() {
        return docIdToBannedRegions;
    }

    protected static QuoteFilter createFromBannedRegions(Map<Symbol, ImmutableRangeSet<Integer>> docIdToBannedRegions)
    {
        return new QuoteFilter(docIdToBannedRegions);
    }

    private QuoteFilter(Map<Symbol,ImmutableRangeSet<Integer>> docIdToBannedRegions) {
        this.docIdToBannedRegions = ImmutableMap.copyOf(docIdToBannedRegions);
        for (RangeSet<Integer> rs : docIdToBannedRegions.values()) {
            for (final Range<Integer> r : rs.asRanges()) {
                checkArgument(r.hasLowerBound());
                checkArgument(r.hasUpperBound());
                checkArgument(r.lowerEndpoint() >= 0);
            }
        }
        // these ensure we can serialize safely
        for (Symbol sym : docIdToBannedRegions.keySet()) {
            final String s= sym.toString();
            checkArgument(!s.isEmpty(), "Document IDs may not be empty");
            checkArgument(!CharMatcher.WHITESPACE.matchesAnyOf(s), "Document IDs may not contain whitespace: %s", s);
        }
    }


    @Override
    public int hashCode() {
        return Objects.hashCode(docIdToBannedRegions);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final QuoteFilter other = (QuoteFilter) obj;
        return Objects.equal(this.docIdToBannedRegions, other.docIdToBannedRegions);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("docIdToBannedRegions", docIdToBannedRegions).toString();
    }

    public void saveTo(ByteSink sink) throws IOException {
        final PrintWriter out = new PrintWriter(sink.asCharSink(Charsets.UTF_8).openBufferedStream());

        out.println(docIdToBannedRegions.size());

        for (final Map.Entry<Symbol, ImmutableRangeSet<Integer>> entry : docIdToBannedRegions.entrySet()) {
            out.println(entry.getKey());
            final List<String> parts = Lists.newArrayList();
            for (final Range<Integer> r : entry.getValue().asRanges()) {
                // we know by construction these ranges are bounded above and below
                parts.add(String.format("%d-%d", r.lowerEndpoint(), r.upperEndpoint()));
            }
            out.println(StringUtils.SpaceJoiner.join(parts));
        }

        out.close();
    }

    private static final Splitter DASH_SPLITTER = Splitter.on("-");
    public static QuoteFilter loadFrom(ByteSource source) throws IOException {
        final ImmutableList<String> input = source.asCharSource(Charsets.UTF_8).readLines();
        if (input.isEmpty()) {
            throw new IOException("Attempted to load QuoteFilter from empty file");
        }

        final int numEntries = Integer.parseInt(input.get(0));
        final int expectedLines = 2*numEntries+1;
        if (input.size() != expectedLines) {
            throw new IOException(String.format(
                    "Invalid number of lines when loading QuoteFiler. Expected %d, got %d",
                    expectedLines, input.size()));
        }

        final ImmutableMap.Builder<Symbol, ImmutableRangeSet<Integer>> ret = ImmutableMap.builder();
        int curLine = 1;
        for (int i=0; i<numEntries; ++i) {
            final Symbol docid = Symbol.from(input.get(curLine++));
            final ImmutableRangeSet.Builder<Integer> ranges = ImmutableRangeSet.builder();
            for (final String part : StringUtils.OnSpaces.split(input.get(curLine++))) {
                final List<String> endPointStrings = DASH_SPLITTER.splitToList(part);
                if (endPointStrings.size() != 2) {
                    throw new IOException(String.format("Invalid range serialization %s", part));
                }
                ranges.add(Range.closed(Integer.parseInt(endPointStrings.get(0)),
                        Integer.parseInt(endPointStrings.get(1))));
            }
            ret.put(docid, ranges.build());
        }
        return QuoteFilter.createFromBannedRegions(ret.build());
    }
}
