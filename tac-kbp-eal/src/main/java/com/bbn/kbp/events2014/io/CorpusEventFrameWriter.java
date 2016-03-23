package com.bbn.kbp.events2014.io;

import com.bbn.kbp.events2014.CorpusEventFrame;

import com.google.common.io.CharSink;

import java.io.IOException;

public interface CorpusEventFrameWriter {

  void writeCorpusEventFrames(Iterable<CorpusEventFrame> corpusEventFrames, CharSink sink)
      throws IOException;
}
