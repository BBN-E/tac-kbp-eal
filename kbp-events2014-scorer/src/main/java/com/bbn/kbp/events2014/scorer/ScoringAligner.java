package com.bbn.kbp.events2014.scorer;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public interface ScoringAligner<EquivClassType> {

  EventArgScoringAlignment<EquivClassType> align(AnswerKey answerKey, SystemOutput systemOutput);
}

final class StandardScoringAligner<EquivClassType> implements ScoringAligner<EquivClassType> {

  private final Function<Response, EquivClassType> equivClassFunction;


  private StandardScoringAligner(final Function<Response, EquivClassType> equivClassFunction) {
    this.equivClassFunction = checkNotNull(equivClassFunction);
  }

  public static <EquivClassType> StandardScoringAligner forEquivalenceClassFunction(
      Function<Response, EquivClassType> equivClassFunction) {
    return new StandardScoringAligner(equivClassFunction);
  }


  @Override
  public EventArgScoringAlignment<EquivClassType> align(final AnswerKey answerKey,
      final SystemOutput systemOutput) {
    checkArgument(answerKey.docId() == systemOutput.docId());
    final ImmutableMultimap<EquivClassType, Response> equivClassToSystemResponses = Multimaps.index(
        systemOutput.responses(), equivClassFunction);
    final ImmutableMultimap<EquivClassType, AssessedResponse> equivClassToAnswerKeyResponses =
        Multimaps.index(
            answerKey.annotatedResponses(),
            Functions.compose(equivClassFunction, AssessedResponse.Response));

    final ImmutableSet<EquivClassType> allEquivClasses =
        Sets.union(equivClassToSystemResponses.keySet(),
            equivClassToAnswerKeyResponses.keySet()).immutableCopy();

    final ImmutableSet.Builder<EquivClassType> truePositives = ImmutableSet.builder();
    final ImmutableSet.Builder<EquivClassType> falsePositives = ImmutableSet.builder();
    final ImmutableSet.Builder<EquivClassType> falseNegatives = ImmutableSet.builder();
    final ImmutableSet.Builder<EquivClassType> unassessed = ImmutableSet.builder();

    for (final EquivClassType eqivClass : allEquivClasses) {
      // a key equivalence class is correct if anyone found a correct response in that class
      final ImmutableCollection<AssessedResponse> answerKeyResponsesForEC =
          equivClassToAnswerKeyResponses.get(eqivClass);
      final boolean isCorrectEquivClass = !FluentIterable.
          from(answerKeyResponsesForEC)
          .filter(AssessedResponse.IsCorrectUpToInexactJustifications)
          .isEmpty();

      final ImmutableCollection<Response> systemResponsesForEC = equivClassToSystemResponses.get(
          eqivClass);

      if (isCorrectEquivClass) {
        // only the top-scoring system response for an equivalence class counts
        final Optional<Response> selectedSystemResponse =
            systemOutput.selectFromMultipleSystemResponses(
                equivClassToSystemResponses.get(eqivClass));

        if (selectedSystemResponse.isPresent()) {
          final Optional<AssessedResponse> assessmentOfSelectedResponse =
              AssessedResponse.findAnnotationForArgument(
                  selectedSystemResponse.get(), answerKeyResponsesForEC);
          if (assessmentOfSelectedResponse.isPresent()) {
            if (assessmentOfSelectedResponse.get().isCorrectUpToInexactJustifications()) {
              truePositives.add(eqivClass);
            } else {
              // it was a correct equivalence class, but we the system response for that
              // equivalence class was incorrect (e.g. due to bad justifications).
              // This counts as both a false positive and a false negative. Note that there
              // could be a correct system response for the equivalence class which has a lower
              // confidence, but it won't count.
              falseNegatives.add(eqivClass);
              falsePositives.add(eqivClass);
            }
          } else {
            // the best system response for this equivalence class is unassessed
            unassessed.add(eqivClass);
          }
        } else {
          // it was a correct equivalence class, but we didn't find any responses
          falseNegatives.add(eqivClass);
        }
      } else {
        // if the equivalence class is incorrect, the system is wrong if it returned *any* response
        if (!systemResponsesForEC.isEmpty()) {
          falsePositives.add(eqivClass);
        } else {
          // do nothing - it was a true negative
        }
      }
    }

    return EventArgScoringAlignment
        .create(systemOutput.docId(), truePositives.build(), falsePositives.build(),
            falseNegatives.build(), unassessed.build(), equivClassToAnswerKeyResponses,
            equivClassToSystemResponses);
  }
}
