package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

/**
 * - mention and nominal_mention: subject is an entity and the object is a String
 *   - each entity (and event) must have at least one mention or nominal_mention triple
 * - canonical_mention: entity to String
 *   - each entity (and event) must have exactly one canonical_mention per document that it appears in
 *   - must also be a mention or nominal_mention
 * - normalized_mention
 *   - copy of another mention for a string node (provenance should be the same)
 *   - in normalized Timex2 format
 *   - one normalized_mention per string node
 */
interface Kbp2017MentionTriple extends Kbp2017ProvenancedTriple {
  String mention();
}

interface Kbp2017EventMentionTriple extends Kbp2017MentionTriple {
  @Override
  EventNode subject();
  Symbol realis();
}

interface Kbp2017EntityMentionTriple extends Kbp2017MentionTriple {
  @Override
  EntityNode subject();
}

interface Kbp2017StringMentionTriple extends Kbp2017MentionTriple {
  @Override
  StringNode subject();
}

interface Kbp2017EventCanonicalMentionTriple extends Kbp2017EventMentionTriple {
}

interface Kbp2017EntityCanonicalMentionTriple extends Kbp2017EntityMentionTriple {
}

interface Kbp2017StringCanonicalMentionTriple extends Kbp2017StringMentionTriple {
}

interface Kbp2017NormalizedMentionTriple extends Kbp2017StringMentionTriple {
}

interface Kbp2017NominalMentionTriple extends Kbp2017EntityMentionTriple {
}

interface Kbp2017PronominalMentionTriple extends Kbp2017EntityMentionTriple {
}
