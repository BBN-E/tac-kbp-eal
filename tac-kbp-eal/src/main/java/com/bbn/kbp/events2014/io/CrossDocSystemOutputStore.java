package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;

import java.io.IOException;

public interface CrossDocSystemOutputStore extends SystemOutputStore {

  CorpusEventLinking readCorpusEventFrames() throws IOException;

  void writeCorpusEventFrames(CorpusEventLinking corpusEventFrames) throws IOException;

  // TODO is this a thing we really want?
  @Override
  DocumentSystemOutput2015 read(Symbol docID) throws IOException;
}
