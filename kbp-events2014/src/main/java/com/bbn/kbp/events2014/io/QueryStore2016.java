package com.bbn.kbp.events2014.io;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.QueryResponse2016;

import java.util.Set;

public interface QueryStore2016 {

  Set<Symbol> queryIDs();

  Set<Symbol> docIDs();

  Set<Symbol> systemIDs();

  Set<QueryResponse2016> queries();

}
