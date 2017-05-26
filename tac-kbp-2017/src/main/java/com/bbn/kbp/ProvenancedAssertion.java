package com.bbn.kbp;

import com.google.common.collect.ImmutableSet;

/**
 * An assertion that contains provenances. The format of this assertion is "[NodeID] [predicate]
 * [NodeID or QuotedString] [provenances]". Multiple provenances must separated by commas.
 */
interface ProvenancedAssertion extends Assertion {

  ImmutableSet<Provenance> provenances();

}
