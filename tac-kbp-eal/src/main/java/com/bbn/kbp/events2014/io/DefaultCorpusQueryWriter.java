package com.bbn.kbp.events2014.io;

import com.bbn.kbp.events2014.CorpusQuery2016;
import com.bbn.kbp.events2014.CorpusQuery2016Functions;
import com.bbn.kbp.events2014.CorpusQueryEntryPoint;
import com.bbn.kbp.events2014.CorpusQuerySet2016;
import com.bbn.kbp.events2014.CorpusQueryWriter;

import com.google.common.base.Joiner;
import com.google.common.collect.Ordering;
import com.google.common.io.CharSink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.bbn.bue.common.symbols.SymbolUtils.byStringOrdering;
import static com.bbn.kbp.events2014.CorpusQueryEntryPointFunctions.docID;
import static com.bbn.kbp.events2014.CorpusQueryEntryPointFunctions.entity;
import static com.bbn.kbp.events2014.CorpusQueryEntryPointFunctions.hopperID;
import static com.bbn.kbp.events2014.CorpusQueryEntryPointFunctions.role;

/**
 * Writes corpus-level queries in the canonical format for KBP EAL 2016
 */
public final class DefaultCorpusQueryWriter implements CorpusQueryWriter {

  private static final Logger log = LoggerFactory.getLogger(DefaultCorpusQueryWriter.class);

  public static CorpusQueryWriter create() {
    return new DefaultCorpusQueryWriter();
  }

  @Override
  public void writeQueries(final CorpusQuerySet2016 queries,
      final CharSink sink) throws IOException {
    final StringBuilder sb = new StringBuilder();

    int numEntryPoints = 0;
    for (final CorpusQuery2016 query : QUERY_ORDERING.sortedCopy(queries)) {
      for (final CorpusQueryEntryPoint entryPoint : ENTRY_POINT_ORDERING
          .sortedCopy(query.entryPoints())) {
        sb.append(entryPointString(query, entryPoint)).append("\n");
        ++numEntryPoints;
      }
    }
    log.info("Writing {} queries with {} entry points to {}", queries.queries().size(),
        numEntryPoints, sink);
    sink.write(sb.toString());
  }

  private static final Joiner TAB_JOINER = Joiner.on("\t");

  private String entryPointString(final CorpusQuery2016 query,
      final CorpusQueryEntryPoint entryPoint) {
    return TAB_JOINER.join(query.id(), entryPoint.docID(), entryPoint.hopperID(),
        entryPoint.role(), entryPoint.entity());
  }

  private static final Ordering<CorpusQuery2016> QUERY_ORDERING =
      byStringOrdering().onResultOf(CorpusQuery2016Functions.id());

  // order entry points by
  private static final Ordering<CorpusQueryEntryPoint> ENTRY_POINT_ORDERING =
      // docID
      byStringOrdering().onResultOf(docID())
          // then hopper ID
          .compound(byStringOrdering().onResultOf(hopperID()))
          // then the role
          .compound(byStringOrdering().onResultOf(role()))
          // then the entity ID
          .compound(byStringOrdering().onResultOf(entity()));
}
