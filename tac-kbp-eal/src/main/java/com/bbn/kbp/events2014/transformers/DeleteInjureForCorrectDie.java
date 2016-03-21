package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;

/**
 * Deletes Life.Injure events corresponding to correct Life.Die events.  From the 2014 KBP EAA task
 * guidelines:
 *
 * Life.Injure and Life.Die. Life.Die events are frequently (perhaps always) preceded by a
 * Life.Injure event.  In ACE annotation, Life.Injure became a distinct event-mention if there was a
 * distinct trigger “Bob was shot dead” → Life.Die and Life.Injure; “Assassins killed Bob” → only
 * Life.Die.  In this evaluation, for scoring purposes we assume Life.Die incorporates Life.Injure.
 * If the assessed pool contains a correct Life.Die tuple, the scorer will ignore Life.Injure
 * tuple(s) that are identical to the Life.Die tuple in CAS-id, role, and realis marker. Thus, if
 * (Life.Die, Place, Springfield, Actual) is correct if (Life.Injure, Place, Springfield, Actual)
 * will be ignored. This rule only applies when the  Life.Die is assessed as correct. Example 2:
 * Bob was shot and killed. Correct: (Life.Die, Victim, Bob, Actual) → rule applied Ignore:
 * (Life.Injure, Victim, Bob, Actual) Example 2:  Bob was beheaded, but miraculously they sewed his
 * head back on and he survived. Wrong: (Life.Die, Victim, Bob, Actual) à rule not applied Correct:
 * (Life.Injure, Victim, Bob, Actual) Example 3: The friendship ended when Bob brutally destroyed
 * Joe in a game of cards. Wrong: (Life.Die, Victim, Bob, Actual) à rule not applied Wrong:
 * (Life.Injure, Victim, Bob, Actual)
 */
public final class DeleteInjureForCorrectDie implements AnswerKeyToResponseMappingRule {

  private DeleteInjureForCorrectDie() {
  }

  public static ScoringDataTransformation asTransformationForBoth() {
    return ApplyAnswerKeyToResponseMappingRuleToAll.forRuleApplyingToBoth(new DeleteInjureForCorrectDie());
  }

  private static final Symbol LIFE_DIE = Symbol.from("Life.Die");
  private static final Symbol LIFE_INJURE = Symbol.from("Life.Injure");

  private ImmutableSet<TypeRoleFillerRealis> responsesToDelete(AnswerKey answerKey,
      final Function<Response, TypeRoleFillerRealis> normalizedFingerprintExtractor) {
    final ImmutableSet.Builder<TypeRoleFillerRealis> ret = ImmutableSet.builder();

    for (final AssessedResponse assessedResponse : answerKey.annotatedResponses()) {
      if (assessedResponse.response().type() == LIFE_DIE
          && assessedResponse.isCorrectUpToInexactJustifications()) {
        ret.add(normalizedFingerprintExtractor.apply(assessedResponse.response()).
            copyWithModifiedType(LIFE_INJURE));
      }
    }
    return ret.build();
  }

  @Override
  public ResponseMapping computeResponseTransformation(final AnswerKey answerKey) {
    final Function<Response, TypeRoleFillerRealis> normalizedFingerprintExtractor =
        TypeRoleFillerRealis.extractFromSystemResponse(
            answerKey.corefAnnotation().strictCASNormalizerFunction());

    final ImmutableSet<TypeRoleFillerRealis> trfrsToDelete = responsesToDelete(answerKey,
        normalizedFingerprintExtractor);
    final Predicate<Response> isInDeletedTRFR =
        compose(in(trfrsToDelete), normalizedFingerprintExtractor);
    final ImmutableSet<Response> responsesToDelete = FluentIterable.from(answerKey.allResponses())
        .filter(isInDeletedTRFR)
        .toSet();

    return ResponseMapping.create(ImmutableMap.<Response, Response>of(), responsesToDelete);
  }
}
