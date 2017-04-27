package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

interface Kbp2017EntityTypeTriple extends Kbp2017Triple {
  @Override Kbp2017EntityNode subject();
  Symbol entityType();  // one of PER, ORG, GPE, FAC, and LOC
}
