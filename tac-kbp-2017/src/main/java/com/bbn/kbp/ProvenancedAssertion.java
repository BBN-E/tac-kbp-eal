package com.bbn.kbp;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;

/**
 * An assertion that contains provenances. The format of this assertion is "[NodeID] [predicate]
 * [NodeID or QuotedString] [provenances]". Multiple provenances must separated by commas.
 */
interface ProvenancedAssertion extends Assertion {

  ImmutableSet<Provenance> provenances();

  Function<ProvenancedAssertion, ImmutableSet<Provenance>> TO_PROVENANCES =
      new Function<ProvenancedAssertion, ImmutableSet<Provenance>>() {
        @Override
        public ImmutableSet<Provenance> apply(final ProvenancedAssertion x) {
          return x.provenances();
        }
      };
}
