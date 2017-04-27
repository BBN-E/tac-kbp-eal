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
  ImmutableSet<Kbp2017Node> nodes();
  ImmutableSet<Kbp2017Triple> triples();
  ImmutableMap<Kbp2017Triple, Double> confidence();

  interface Builder {
    Kbp2017EventNode makeEvent();
    Kbp2017EventTypeTriple setEventType(Kbp2017EventNode eventNode, Symbol type);
    Kbp2017ProvenancedTriple setEventArgument(Kbp2017EventNode eventNode, Kbp2017Argument argument,
        Symbol eventType, Symbol role, Symbol realis, Set<Kbp2017Provenance> provenances);
    Kbp2017EntityNode makeEntity();
    Kbp2017EntityTypeTriple setEntityType(Kbp2017EntityNode entityNode, Symbol type);
    Kbp2017MentionTriple setMention(Kbp2017Node node, String mention,
        Set<Kbp2017Provenance> provenances);

    void setConfidence(Kbp2017Triple triple, double confidence);
  }
}
