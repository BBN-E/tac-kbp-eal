package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;

public interface Query {
  Symbol queryID();
  Symbol docID();
  Symbol systemID();
}
