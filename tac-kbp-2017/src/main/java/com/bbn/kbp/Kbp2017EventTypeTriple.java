package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

/**
 * - type assertion
 *   - each entity (and event) must have exactly one type triple
 *   - type does not have provenance
 */
interface Kbp2017EventTypeTriple extends Kbp2017Triple {
  @Override
  EventNode subject();
  Symbol eventType();
}
