package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

/**
 * - link predicate
 *   - from entity node to a string of the form "ExternalKBID:ExternalNodeID"
 *   - (QUESTION) should not have provenance
 */
interface Kbp2017LinkTriple extends Kbp2017Triple {
  @Override
  EntityNode subject();
  Symbol externalKB();
  Symbol externalNodeID();
}
