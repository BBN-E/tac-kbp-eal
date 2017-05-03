package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

/**
 * - (QUESTION) Event Argument inverse predicates
 */
interface Kbp2017EventArgumentTriple extends Kbp2017ProvenancedTriple {
  @Override
  EventNode subject();
  Symbol eventType();
  Symbol role();
  Symbol realis();
  EventArgument argument();  // Either a Kbp2017EntityNode or a Kbp2017StringNode
}
