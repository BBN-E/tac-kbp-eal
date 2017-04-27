package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

/**
 * sentiment predicates
 */
interface Kbp2017SentimentTriple extends Kbp2017ProvenancedTriple {
  @Override Kbp2017EntityNode subject();
  Kbp2017EntityNode object();
  Symbol sentiment();
}
