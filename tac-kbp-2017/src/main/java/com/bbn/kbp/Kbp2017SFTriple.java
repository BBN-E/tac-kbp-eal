package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

/**
 * slot filling
 */
interface Kbp2017SFTriple extends Kbp2017ProvenancedTriple {
  @Override
  EntityNode subject();
  EventArgument object();  // Either a Kbp2017EntityNode or a Kbp2017StringNode
  Symbol predicate();
}
