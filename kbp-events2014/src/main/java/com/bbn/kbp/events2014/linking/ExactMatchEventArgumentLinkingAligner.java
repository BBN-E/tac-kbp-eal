package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.*;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.*;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;

public final class ExactMatchEventArgumentLinkingAligner implements EventArgumentLinkingAligner {
    private boolean correctOnly;
    private final ImmutableSet<KBPRealis> realisesWhichMustBeAligned;

    private ExactMatchEventArgumentLinkingAligner(boolean correctOnly,
                                                  Iterable<KBPRealis> realisesWhichMustBeAligned)
    {
        this.correctOnly = correctOnly;
        this.realisesWhichMustBeAligned = ImmutableSet.copyOf(realisesWhichMustBeAligned);
    }

    /**
     * Creates an aligner which will expect the response linking to account for all
     * correctly assessed responses with actual realis and no others.
     */
    public static ExactMatchEventArgumentLinkingAligner createForCorrectWithRealises(Iterable<KBPRealis> realises) {
        return new ExactMatchEventArgumentLinkingAligner(true, realises);
    }

    /** Converts a {@link ResponseLinking} to an {@link EventArgumentLinking} using
     * a {@link CorefAnnotation} from an {@link com.bbn.kbp.events2014.AnswerKey}
     * to canonicalize the responses.  If the canonicalization
     * is inconsistent with the response linking, an {@link java.lang.IllegalArgumentException}
     * will be thrown.
     */
    public EventArgumentLinking align(ResponseLinking responseLinking,
                       AnswerKey answerKey) throws InconsistentLinkingException
    {
        checkArgument(answerKey.docId() == responseLinking.docID());
        final ImmutableSet<Response> relevantResponses = getRelevantResponses(answerKey);

        if (!responseLinking.allResponses().equals(relevantResponses)) {
            final Set<Response> inLinkingButNotKey = Sets.difference(
                    responseLinking.allResponses(), relevantResponses);
            final Set<Response> inKeyButNotLinking = Sets.difference(
                    relevantResponses, responseLinking.allResponses());
            throw new InconsistentLinkingException("Response linking and answer key do "
                    + "not cover exactly the same responses. In key only: "
                    + inKeyButNotLinking + ", in linking only: " + inLinkingButNotKey);
        }

        final CorefAnnotation corefAnnotation = answerKey.corefAnnotation();

        final ImmutableMultimap<TypeRoleFillerRealis, Response> canonicalToResponses =
                Multimaps.index(responseLinking.allResponses(),
                        TypeRoleFillerRealis.extractFromSystemResponse(
                                corefAnnotation.strictCASNormalizerFunction()));

        final Multimap<Response, TypeRoleFillerRealis> responsesToCanonical =
                canonicalToResponses.inverse();

        final ImmutableSet.Builder<TypeRoleFillerRealisSet> coreffedArgs = ImmutableSet.builder();
        for (final ResponseSet responseSet : responseLinking.responseSets()) {
            coreffedArgs.add(TypeRoleFillerRealisSet.from(
                    canonicalizeResponseSet(responseSet.asSet(),
                            canonicalToResponses, responsesToCanonical)));
        }
        final ImmutableSet<TypeRoleFillerRealis> incompleteResponses = canonicalizeResponseSet(
                responseLinking.incompleteResponses(), canonicalToResponses, responsesToCanonical);

        return EventArgumentLinking.create(responseLinking.docID(), coreffedArgs.build(),
                incompleteResponses);
    }

    private ImmutableSet<Response> getRelevantResponses(AnswerKey answerKey) {
        final FluentIterable<Response> responses;

        if (correctOnly) {
            responses = FluentIterable.from(answerKey.annotatedResponses())
                .filter(AssessedResponse.IsCorrectUpToInexactJustifications)
                .transform(AssessedResponse.Response);
        } else {
            responses = FluentIterable.from(answerKey.allResponses());
        }

        final Predicate<Response> HasRelevantRealis = compose(
                in(realisesWhichMustBeAligned),
                Response.realisFunction());

        return responses.filter(HasRelevantRealis).toSet();
    }

    @Override
    public ResponseLinking alignToResponseLinking(EventArgumentLinking eventArgumentLinking,
                                                  AnswerKey answerKey)
    {
        final ImmutableMultimap<TypeRoleFillerRealis, Response> canonicalToResponses =
                Multimaps.index(getRelevantResponses(answerKey),
                        TypeRoleFillerRealis.extractFromSystemResponse(
                                answerKey.corefAnnotation().strictCASNormalizerFunction()));

        final ImmutableSet.Builder<Response> incompletes = ImmutableSet.builder();
        for (final TypeRoleFillerRealis incompleteEquivClass : eventArgumentLinking.incomplete()) {
            incompletes.addAll(canonicalToResponses.get(incompleteEquivClass));
        }

        final ImmutableSet.Builder<ResponseSet> responseSets = ImmutableSet.builder();
        for (final TypeRoleFillerRealisSet equivClassSet : eventArgumentLinking.linkedAsSet()) {
            final ImmutableSet.Builder<Response> setBuilder = ImmutableSet.builder();
            for (final TypeRoleFillerRealis trfr : equivClassSet.asSet()) {
                setBuilder.addAll(canonicalToResponses.get(trfr));
            }
            responseSets.add(ResponseSet.from(setBuilder.build()));
        }

        return ResponseLinking.from(answerKey.docId(), responseSets.build(),
                incompletes.build());
    }

    /** Maps a {@code Set<Response>} to a {@code Set<TypeRoleFillerRealis>} which
     is supported by exactly the same responses.  If any response supporting
     a TRFR is present in {@code responseGroup}, that TRFR will appear in the
     returned set. However, if such a TRFR is supported by responses but within
     and outside {@code responseSet}, an {@link java.lang.IllegalArgumentException} is thrown.
     */
    private static ImmutableSet<TypeRoleFillerRealis> canonicalizeResponseSet(
            Set<Response> responseGroup,
            Multimap<TypeRoleFillerRealis, Response> canonicalToResponses,
            Multimap<Response, TypeRoleFillerRealis> responsesToCanonical)
            throws InconsistentLinkingException
    {
        final ImmutableSet.Builder<TypeRoleFillerRealis> canonicalizationsWithResponsesInGroupBuilder =
                ImmutableSet.builder();
        for (final Response response : responseGroup) {
            for (final TypeRoleFillerRealis canonicalization : responsesToCanonical.get(response)) {
                canonicalizationsWithResponsesInGroupBuilder.add(canonicalization);
            }
        }

        final ImmutableSet<TypeRoleFillerRealis> ret =
                canonicalizationsWithResponsesInGroupBuilder.build();

        // sanity check
        for (final TypeRoleFillerRealis canonicalization : ret) {
            for (final Response response : canonicalToResponses.get(canonicalization)) {
                if (!responseGroup.contains(response)) {
                    throw new InconsistentLinkingException("Response linking inconsistent with coreference annotation:");
                }
            }
        }

        return ret;
    }
}
