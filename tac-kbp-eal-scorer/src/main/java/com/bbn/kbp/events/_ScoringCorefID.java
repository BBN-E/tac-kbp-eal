package com.bbn.kbp.events;

import com.bbn.bue.common.TextGroupPackageImmutable;

import org.immutables.value.Value;

/**
 * Represents a participant in a document-level entity for purposes of scoring.  This can be either
 * an entity, a value-like filler, or something in the system output which fails to align to
 * anything in the ERE.  We form global identifiers over all these types by concatenating the
 * scoring entity type with its within-type ID.
 */
@TextGroupPackageImmutable
@Value.Immutable
abstract class _ScoringCorefID {

  @Value.Parameter
  public abstract ScoringEntityType scoringEntityType();

  @Value.Parameter
  public abstract String withinTypeID();

  @Value.Derived
  public String globalID() {
    return scoringEntityType().name() + "-" + withinTypeID();
  }
}
