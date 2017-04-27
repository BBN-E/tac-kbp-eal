package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

/**
 * - (QUESTION) Event Argument inverse predicates
 */
interface Kbp2017EventArgumentTriple extends Kbp2017ProvenancedTriple {
  @Override Kbp2017EventNode subject();
  Symbol eventType();
  Symbol role();
  Symbol realis();
  Kbp2017Argument argument();  // Either a Kbp2017EntityNode or a Kbp2017StringNode
}
