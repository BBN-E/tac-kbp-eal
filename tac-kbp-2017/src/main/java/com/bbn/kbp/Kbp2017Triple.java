package com.bbn.kbp;

import com.google.common.collect.ImmutableSet;

/**
 * - assertions: (type, link, and mention take literal string objects instead of string nodes)
 *   - mention assertions: involve mention, nominal_mention, pronominal_mention, canonical_mention,
 *     normalized_mention
 *   - SF assertions
 *   - sentiment assertions
 *   - event assertions
 * - predicates: table1 (in ColdStartTaskDescription), table2 (in ColdStartTaskDescription), type,
 *   mention, nominal_mention canonical_mention
 *   - table1 predicates: subject and object are both entities
 *   - table2 predicates: subject is an entity and object is a String
 * - realis
 *   - expected for event predicates and mention predicates of event node subjects
 *   - possible realises: actual, generic, other
 *   - append realis to predicate name of a triple using "."
 * - 2.10 list of predicates
 */
interface Kbp2017Triple {
  Kbp2017Node subject();
}

interface Kbp2017ProvenancedTriple extends Kbp2017Triple {
  ImmutableSet<Kbp2017Provenance> provenances();
}
