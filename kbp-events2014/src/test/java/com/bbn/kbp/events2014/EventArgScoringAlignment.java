package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;

import static com.google.common.base.Preconditions.checkNotNull;

public final class EventArgScoringAlignment<EquivClassType> {

  private final Symbol docID;
  private final ImmutableSet<EquivClassType> truePositiveECs;
  private final ImmutableSet<EquivClassType> falsePositiveECs;
  private final ImmutableSet<EquivClassType> falseNegativeECs;
  private final ImmutableSetMultimap<EquivClassType, AssessedResponse> ecsToAnswerKey;
  private final ImmutableSetMultimap<EquivClassType, Response> ecsToSystem;

  private EventArgScoringAlignment(final Symbol docID,
      final Iterable<EquivClassType> truePositiveECs,
      final Iterable<EquivClassType> falsePositiveECs,
      final Iterable<EquivClassType> falseNegativeECs,
      final Multimap<EquivClassType, AssessedResponse> ecsToAnswerKey,
      final Multimap<EquivClassType, Response> ecsToSystem) {
    this.docID = checkNotNull(docID);
    this.truePositiveECs = ImmutableSet.copyOf(truePositiveECs);
    this.falsePositiveECs = ImmutableSet.copyOf(falsePositiveECs);
    this.falseNegativeECs = ImmutableSet.copyOf(falseNegativeECs);
    this.ecsToAnswerKey = ImmutableSetMultimap.copyOf(ecsToAnswerKey);
    this.ecsToSystem = ImmutableSetMultimap.copyOf(ecsToSystem);
  }

  public Symbol docID() {
    return docID;
  }

  public ImmutableSetMultimap<EquivClassType, AssessedResponse> equivalenceClassesToAnswerKeyResponses() {
    return ecsToAnswerKey;
  }

  public ImmutableSetMultimap<EquivClassType, Response> equivalenceClassesToSystemResponses() {
    return ecsToSystem;
  }

  public ImmutableSet<EquivClassType> falseNegativeEquivalenceClasses() {
    return falseNegativeECs;
  }

  public ImmutableSet<EquivClassType> falsePositiveEquivalenceClasses() {
    return falsePositiveECs;
  }

  public ImmutableSet<EquivClassType> truePositiveEquivalenceClasses() {
    return truePositiveECs;
  }
}
