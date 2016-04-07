package com.bbn.kbp.events2014;

import com.google.common.io.CharSource;

import java.io.IOException;

/**
 * Loads 2016 corpus-level queries.
 */
public interface CorpusQueryLoader {

  CorpusQuerySet2016 loadQueries(CharSource source) throws IOException;
}

