package com.bbn.kbp.events2014.io;

import com.bbn.kbp.events2014.CorpusEventLinking;

import com.google.common.io.CharSink;

import java.io.IOException;

public interface CorpusEventFrameWriter {

  void writeCorpusEventFrames(CorpusEventLinking corpusEventFrames, CharSink sink)
      throws IOException;
}
