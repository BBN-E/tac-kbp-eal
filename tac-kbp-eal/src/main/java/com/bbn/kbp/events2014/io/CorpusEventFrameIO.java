package com.bbn.kbp.events2014.io;

public final class CorpusEventFrameIO {

  private CorpusEventFrameIO() {
    throw new UnsupportedOperationException();
  }

  public static CorpusEventFrameLoader loaderFor2016() {
    return new CorpusEventFrameLoader2016();
  }

  public static CorpusEventFrameWriter writerFor2016() {
    return new CorpusEventFrameWriter2016();
  }
}
