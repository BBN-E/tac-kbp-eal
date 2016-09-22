package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CorpusQuery2016;
import com.bbn.kbp.events2014.CorpusQueryEntryPoint;
import com.bbn.kbp.events2014.CorpusQueryLoader;
import com.bbn.kbp.events2014.CorpusQuerySet2016;
import com.bbn.kbp.events2014.TACKBPEALException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.bbn.bue.common.symbols.SymbolUtils.byStringOrdering;

public final class DefaultCorpusQueryLoader implements CorpusQueryLoader {

  private static final Logger log = LoggerFactory.getLogger(DefaultCorpusQueryLoader.class);

  private DefaultCorpusQueryLoader() {
  }

  public static DefaultCorpusQueryLoader create() {
    return new DefaultCorpusQueryLoader();
  }

  @Override
  public CorpusQuerySet2016 loadQueries(final CharSource source) throws IOException {
    final ImmutableMultimap.Builder<Symbol, CorpusQueryEntryPoint> queriesToEntryPoints =
        ImmutableMultimap.<Symbol, CorpusQueryEntryPoint>builder().orderKeysBy(byStringOrdering());

    int numEntryPoints = 0;
    int lineNo = 1;
    for (final String line : source.readLines()) {
      try {
        final List<String> parts = StringUtils.onTabs().splitToList(line);
        if (parts.size() != 5) {
          throw new TACKBPEALException("Expected 5 tab-separated fields but got " + parts.size());
        }

        final Symbol queryID = Symbol.from(parts.get(0));
        final Symbol docID = Symbol.from(parts.get(1));
        final Symbol hopperID = Symbol.from(parts.get(2));
        final Symbol role = Symbol.from(parts.get(3));
        final Symbol entityID = Symbol.from(parts.get(4));

        queriesToEntryPoints
            .put(queryID, CorpusQueryEntryPoint.of(docID, hopperID, role, entityID));

        ++lineNo;
        ++numEntryPoints;
      } catch (Exception e) {
        throw new IOException("Invalid query line " + lineNo + " in " + source + ": " + line, e);
      }
    }

    final ImmutableSet.Builder<CorpusQuery2016> ret = ImmutableSet.builder();
    for (final Map.Entry<Symbol, Collection<CorpusQueryEntryPoint>> e
        : queriesToEntryPoints.build().asMap().entrySet()) {
      ret.add(new CorpusQuery2016.Builder().id(e.getKey()).addAllEntryPoints(e.getValue()).build());
    }

    final CorpusQuerySet2016 corpusQuerySet = CorpusQuerySet2016.of(ret.build());
    log.info("Loaded {} queries with {} entry points from {}", corpusQuerySet.queries().size(),
        numEntryPoints, source);
    return corpusQuerySet;
  }

  private static ImmutableList<OffsetRange<CharOffset>> offsets(final String parts) {
    final ImmutableSet.Builder<OffsetRange<CharOffset>> ret = ImmutableSet.builder();
    for (final String part : parts.split(",")) {
      ret.add(TACKBPEALIOUtils.parseCharOffsetRange(part));
    }

    return OffsetRange.<CharOffset>byEarlierStartLaterEndOrdering()
        .immutableSortedCopy(ret.build());
  }
}

