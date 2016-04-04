package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.EventArgumentLinking;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;

public interface EventArgumentEquivalenceStore {

  public ImmutableSet<Symbol> docIDs() throws IOException;

  public void write(EventArgumentLinking toWrite) throws IOException;
}
