package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Query;

import com.google.common.collect.ImmutableSet;

public interface QueryStore<T extends Query> {

  ImmutableSet<Symbol> queryIDs();
  ImmutableSet<Symbol> docIDs();
  ImmutableSet<Symbol> systemIDs();
  ImmutableSet<T> queries();
}
