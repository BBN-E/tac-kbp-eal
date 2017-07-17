package com.bbn.kbp;

/**
 * An assertion which is allowed to have only one predicate justification.
 */
public interface HasSinglePredicateJustification {

  JustificationSpan predicateJustification();
}
