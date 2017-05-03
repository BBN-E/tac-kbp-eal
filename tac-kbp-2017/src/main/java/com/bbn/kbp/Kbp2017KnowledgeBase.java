package com.bbn.kbp;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * - (QUESTION) What is section 2.6 of Revisions
 * - don't care about 2.7 pronominal mentions
 * - confidence measure
 *   - column right after provenances, last column in a line
 *   - optional
 *   - From 0.0 (exclusive) to 1.0 (inclusive), decimal point not a comma
 *   - to be considered a duplicate, the triple and provenance must be the same and nothing else matters
 */
interface Kbp2017KnowledgeBase {
  ImmutableSet<Node> nodes();
  ImmutableSet<Kbp2017Triple> triples();
  ImmutableMap<Kbp2017Triple, Double> confidence();

  interface Builder {
    EventNode makeEvent();
    Kbp2017EventTypeTriple setEventType(EventNode eventNode, Symbol type);
    Kbp2017ProvenancedTriple setEventArgument(EventNode eventNode, EventArgument eventArgument,
        Symbol eventType, Symbol role, Symbol realis, Set<Kbp2017Provenance> provenances);
    EntityNode makeEntity();
    Kbp2017EntityTypeTriple setEntityType(EntityNode entityNode, Symbol type);
    Kbp2017MentionTriple setMention(Node node, String mention,
        Set<Kbp2017Provenance> provenances);

    void setConfidence(Kbp2017Triple triple, double confidence);
  }
}
