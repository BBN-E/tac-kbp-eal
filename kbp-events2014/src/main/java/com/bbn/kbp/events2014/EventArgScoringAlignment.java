package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public final class EventArgScoringAlignment<EquivClassType> {
  private final Symbol docID;
  private final ArgumentOutput argumentOutput;
  private final AnswerKey answerKey;
  private final ImmutableSet<EquivClassType> truePositiveECs;
  private final ImmutableSet<EquivClassType> falsePositiveECs;
  private final ImmutableSet<EquivClassType> falseNegativeECs;
  private final ImmutableSet<EquivClassType> unassessed;
  private final ImmutableSetMultimap<EquivClassType, AssessedResponse> ecsToAnswerKey;
  private final ImmutableSetMultimap<EquivClassType, Response> ecsToSystem;

  private EventArgScoringAlignment(final Symbol docID,
      final ArgumentOutput argumentOutput,
      final AnswerKey answerKey,
      final Iterable<EquivClassType> truePositiveECs,
      final Iterable<EquivClassType> falsePositiveECs,
      final Iterable<EquivClassType> falseNegativeECs,
      final Iterable<EquivClassType> unassessed,
      final Multimap<EquivClassType, AssessedResponse> ecsToAnswerKey,
      final Multimap<EquivClassType, Response> ecsToSystem) {
    this.docID = checkNotNull(docID);
    this.argumentOutput = checkNotNull(argumentOutput);
    this.answerKey = checkNotNull(answerKey);
    this.truePositiveECs = ImmutableSet.copyOf(truePositiveECs);
    this.falsePositiveECs = ImmutableSet.copyOf(falsePositiveECs);
    this.falseNegativeECs = ImmutableSet.copyOf(falseNegativeECs);
    this.unassessed = ImmutableSet.copyOf(unassessed);
    this.ecsToAnswerKey = ImmutableSetMultimap.copyOf(ecsToAnswerKey);
    this.ecsToSystem = ImmutableSetMultimap.copyOf(ecsToSystem);
  }

  public static <EquivClassType> EventArgScoringAlignment<EquivClassType> create(final Symbol docID,
      final ArgumentOutput argumentOutput,
      final AnswerKey answerKey,
      final Iterable<EquivClassType> truePositiveECs,
      final Iterable<EquivClassType> falsePositiveECs,
      final Iterable<EquivClassType> falseNegativeECs,
      final Iterable<EquivClassType> unassessed,
      final Multimap<EquivClassType, AssessedResponse> ecsToAnswerKey,
      final Multimap<EquivClassType, Response> ecsToSystem) {
    return new EventArgScoringAlignment<EquivClassType>(docID, argumentOutput, answerKey,
        truePositiveECs,
        falsePositiveECs, falseNegativeECs, unassessed, ecsToAnswerKey, ecsToSystem);
  }

  public ImmutableSet<EquivClassType> allEquivalenceClassess() {
    return ImmutableSet.copyOf(
        Iterables.concat(truePositiveECs, falseNegativeECs, falseNegativeECs));
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

  public ImmutableSet<EquivClassType> unassessed() {
    return unassessed;
  }

  public ArgumentOutput systemOutput() {
    return argumentOutput;
  }

  public AnswerKey answerKey() {
    return answerKey;
  }

  /**
   * Note this will not include system equivalence classes whose representative responses were
   * unassessed.
   */
  public ImmutableMap<EquivClassType, AssessedResponse> systemEquivClassToAssessedRepresentativeResponses() {
    final ImmutableMap.Builder<EquivClassType, AssessedResponse> ret = ImmutableMap.builder();

    for (final Map.Entry<EquivClassType, Collection<Response>> systemTRFR : equivalenceClassesToSystemResponses()
        .asMap().entrySet()) {
      final Response representativeResponse = systemOutput()
          .selectFromMultipleSystemResponses(systemTRFR.getValue()).get();
      final Optional<AssessedResponse> optAssessedRepResponse =
          answerKey().assess(representativeResponse);
      if (optAssessedRepResponse.isPresent()) {
        ret.put(systemTRFR.getKey(), optAssessedRepResponse.get());
      }
    }
    return ret.build();
  }

  public EventArgScoringAlignment<EquivClassType> copyFiltered(
      final Predicate<EquivClassType> filter) {
    return new EventArgScoringAlignment<EquivClassType>(docID(), argumentOutput, answerKey,
        Iterables.filter(truePositiveECs, filter),
        Iterables.filter(falseNegativeECs, filter),
        Iterables.filter(falseNegativeECs, filter),
        Iterables.filter(unassessed, filter),
        Multimaps.filterKeys(ecsToAnswerKey, filter),
        Multimaps.filterKeys(ecsToSystem, filter));
  }
}
