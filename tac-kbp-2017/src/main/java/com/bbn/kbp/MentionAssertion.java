package com.bbn.kbp;

/**
 * An assertion of the format "[NodeID] [mention predicate] [QuotedString mention] [provenances]
 * (confidence)". The mention predicate can be one of "mention", "canonical_mention",
 * "normalized_mention", "nominal_mention", or "pronominal_mention"; and in the case of an event
 * mention must also have a realis appended to it with a ".". For all three node types
 * ({@code EventNode}, {@code EntityNode}, and {@code StringNode}), each node must have at least
 * one mention or nominal mention and exactly one canonical mention.
 */
interface MentionAssertion extends ProvenancedAssertion {

  String mention();

}
