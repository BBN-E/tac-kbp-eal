package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

/**
 * sentiment predicates
 */
interface Kbp2017SentimentTriple extends Kbp2017ProvenancedTriple {
  @Override
  EntityNode subject();
  EntityNode object();
  Symbol sentiment();
}
