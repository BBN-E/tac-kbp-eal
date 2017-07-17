package com.bbn.kbp;

import com.bbn.bue.common.HasDocID;
import com.bbn.bue.common.symbols.Symbol;

/**
 * An assertion of the format "[NodeID] [mention predicate] [QuotedString mention] [provenances]
 * (confidence)". The mention predicate can be one of "mention", "canonical_mention",
 * "normalized_mention", "nominal_mention", or "pronominal_mention"; and in the case of an event
 * mention must also have a realis appended to it with a ".". For all three node types
 * ({@code EventNode}, {@code EntityNode}, and {@code StringNode}), each node must have at least
 * one mention or nominal mention and exactly one canonical mention.
 */
abstract class MentionAssertion implements Assertion, HasSinglePredicateJustification, HasDocID {

  public abstract String mention();

  @Override
  public abstract JustificationSpan predicateJustification();

  @Override
  public final Symbol docID() {
    return predicateJustification().documentId();
  }
}
