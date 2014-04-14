package com.bbn.kbp.events2014.filters;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.io.CharSource;
import java.io.IOException;
import java.util.Map;

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
    private static final String BANNED_REGION_START = "<quote>";
    private static final String BANNED_REGION_END = "</quote>";

    private final Map<Symbol, ImmutableRangeSet<Integer>> docIdToBannedRegions;

    @Override
    public SystemOutput apply(SystemOutput input) {
        final ImmutableRangeSet<Integer> bannedRegions = docIdToBannedRegions.get(input.docId());
        if (bannedRegions == null) {
            throw new RuntimeException(String.format(
               "QuoteFilter does not know about document ID %s", input.docId()));
        }

        return input.copyWithFilteredResponses(
           new Predicate<Scored<Response>>() {
               public boolean apply(Scored<Response> x) {
                   return !inBannedSet(x.item().baseFiller(), bannedRegions)
                    && !inBannedSet(x.item().canonicalArgument().charOffsetSpan(), bannedRegions);
               }
           }
        );
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
        int regionStart = s.indexOf(BANNED_REGION_START, curPos);

        // if we found a <quote> tag
        while (regionStart != -1) {
            curPos = regionStart;
            int nestingCount = 1;

            // until we find the matching </quote> tag..
            while (nestingCount > 0) {
                final int nextStart = s.indexOf(BANNED_REGION_START, curPos+1);
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

            regionStart = s.indexOf(BANNED_REGION_START, curPos+1);
        }

        return ret.build();
    }

    public static QuoteFilter createFromBannedRegions(Map<Symbol, ImmutableRangeSet<Integer>> docIdToBannedRegions) {
        return new QuoteFilter(docIdToBannedRegions);
    }

    public QuoteFilter(Map<Symbol,ImmutableRangeSet<Integer>> docIdToBannedRegions) {
        this.docIdToBannedRegions = ImmutableMap.copyOf(docIdToBannedRegions);
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
}
