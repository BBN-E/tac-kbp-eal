package com.bbn.kbp.events2014;

import com.google.common.io.CharSink;

import java.io.IOException;

/**
 * Writes 2016 corpus-level queries
 */
public interface CorpusQueryWriter {

  void writeQueries(CorpusQuerySet2016 queries, CharSink sink)
      throws IOException;
}


