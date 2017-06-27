package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.DocumentSystemOutput;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

public interface SystemOutputStore extends Closeable {

  Symbol systemID();
  Set<Symbol> docIDs() throws IOException;

  DocumentSystemOutput read(Symbol docID) throws IOException;

  void write(DocumentSystemOutput output) throws IOException;

  @Override
  void close() throws IOException;
}

