package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.scoring.Scoreds;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.*;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;

/**
 * Deletes Life.Injure events corresponding to correct Life.Die events.  From the 2014 KBP EAA task guidelines:
 *
 * Life.Injure and Life.Die. Life.Die events are frequently (perhaps always) preceded by a Life.Injure event.  In ACE annotation, Life.Injure became a distinct event-mention if there was a distinct trigger “Bob was shot dead” → Life.Die and Life.Injure; “Assassins killed Bob” → only Life.Die.  In this evaluation, for scoring purposes we assume Life.Die incorporates Life.Injure. If the assessed pool contains a correct Life.Die tuple, the scorer will ignore Life.Injure tuple(s) that are identical to the Life.Die tuple in CAS-id, role, and realis marker. Thus, if (Life.Die, Place, Springfield, Actual) is correct if (Life.Injure, Place, Springfield, Actual) will be ignored. This rule only applies when the  Life.Die is assessed as correct.
 Example 2:  Bob was shot and killed.
 Correct: (Life.Die, Victim, Bob, Actual) → rule applied
 Ignore: (Life.Injure, Victim, Bob, Actual)
 Example 2:  Bob was beheaded, but miraculously they sewed his head back on and he survived.
 Wrong: (Life.Die, Victim, Bob, Actual) à rule not applied
 Correct: (Life.Injure, Victim, Bob, Actual)
 Example 3: The friendship ended when Bob brutally destroyed Joe in a game of cards.
 Wrong: (Life.Die, Victim, Bob, Actual) à rule not applied
 Wrong: (Life.Injure, Victim, Bob, Actual)
 */
public final class DeleteInjureForCorrectDie {
    private static final Logger log = LoggerFactory.getLogger(DeleteInjureForCorrectDie.class);
    // given a response, this will extract its type, role, filler, and realis while
    // using coref to normalize the filler. This is necessary because when we delete a
    // Life.Injure event because of a Life.Die, we want to delete all its coreferent forms
    private final Function<Response, TypeRoleFillerRealis> normalizedFingerprintExtractor;

    private DeleteInjureForCorrectDie(Function<Response, TypeRoleFillerRealis> normalizedFingerprintExtractor) {
        this.normalizedFingerprintExtractor = checkNotNull(normalizedFingerprintExtractor);
    }

    public static  DeleteInjureForCorrectDie fromEntityNormalizer(Function<KBPString, KBPString> entityNormalizer) {
        return new DeleteInjureForCorrectDie(
                TypeRoleFillerRealis.extractFromSystemResponse(entityNormalizer));
    }


    public Function<AnswerKey,AnswerKey> answerKeyTransformer() {
        return new Function<AnswerKey, AnswerKey>() {
            @Override
            public AnswerKey apply(AnswerKey input) {
                final ImmutableSet<TypeRoleFillerRealis> toDelete = responsesToDelete(input);
                return AnswerKey.from(input.docId(), removeInjuresMatchingDies(input.annotatedResponses(), toDelete),
                        input.unannotatedResponses(), input.corefAnnotation());
            };

            private Iterable<AssessedResponse> removeInjuresMatchingDies(Iterable<AssessedResponse> currentResponses,
                                                                         ImmutableSet<TypeRoleFillerRealis> toDelete)
            {
                final ImmutableSet<AssessedResponse> currentResponseSet = ImmutableSet.copyOf(currentResponses);
                // we want to remove all responses whose type-role-realis-normalized-fillers are
                // scheduled for deletion
                final ImmutableSet<AssessedResponse> filtered =  FluentIterable.from(currentResponseSet)
                        .filter(Predicates.compose(not(in(toDelete)),
                                Functions.compose(normalizedFingerprintExtractor, AssessedResponse.Response)))
                        .toSet();

                if (currentResponseSet.size() != filtered.size()) {
                    log.info("Deleted {} Life.Injure events from answer key due to matching Life.Die events: {}",
                            currentResponseSet.size() - filtered.size(),
                            Sets.difference(currentResponseSet, filtered));
                }
                return filtered;
            }
        };
    }

    public Function<SystemOutput, SystemOutput> systemOutputTransformerForAnswerKey(AnswerKey answerKey) {
        final ImmutableSet<TypeRoleFillerRealis> toDelete = responsesToDelete(answerKey);

        return new Function<SystemOutput, SystemOutput>() {
            @Override
            public SystemOutput apply(SystemOutput input) {
                final Function<Scored<Response>, TypeRoleFillerRealis> scoredResponseToNormalizedFingerPrint =
                        Functions.compose(normalizedFingerprintExtractor, Scoreds.<Response>itemsOnly());

                final SystemOutput ret = input.copyWithFilteredResponses(
                        Predicates.compose(
                                not(in(toDelete)),
                                scoredResponseToNormalizedFingerPrint));

                if (ret.size() != input.size()) {
                    log.info("Deleted {} Life.Injure events from system output due to matching Life.Die events in answer key: {}",
                            input.size() - ret.size(),
                            Sets.difference(input.responses(), ret.responses()));
                }

                return ret;
            }
        };
    }

    private static final Symbol LIFE_DIE = Symbol.from("Life.Die");
    private static final Symbol LIFE_INJURE = Symbol.from("Life.Injure");
    private ImmutableSet<TypeRoleFillerRealis> responsesToDelete(AnswerKey answerKey) {
        final ImmutableSet.Builder<TypeRoleFillerRealis> ret = ImmutableSet.builder();

        for (final AssessedResponse assessedResponse : answerKey.annotatedResponses()) {
            if (assessedResponse.response().type() == LIFE_DIE
                && assessedResponse.isCorrectUpToInexactJustifications())
            {
                ret.add(normalizedFingerprintExtractor.apply(assessedResponse.response()).
                        copyWithModifiedType(LIFE_INJURE));
            }
        }
        return ret.build();
    }
}
