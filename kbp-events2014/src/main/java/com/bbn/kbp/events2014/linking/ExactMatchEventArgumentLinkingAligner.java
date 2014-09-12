package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.*;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public final class ExactMatchEventArgumentLinkingAligner implements EventArgumentLinkingAligner {
    private ExactMatchEventArgumentLinkingAligner() {

    }

    public static ExactMatchEventArgumentLinkingAligner create() {
        return new ExactMatchEventArgumentLinkingAligner();
    }

    /** Converts a {@link ResponseLinking} to an {@link EventArgumentLinking} using
     * a {@link CorefAnnotation} to canonicalize the responses.  If the canonicalization
     * is inconsistent with the response linking, an {@link java.lang.IllegalArgumentException}
     * will be thrown.
     */
    public EventArgumentLinking align(ResponseLinking responseLinking,
                       CorefAnnotation corefAnnotation) throws InconsistentLinkingException
    {
        checkArgument(corefAnnotation.docId() == responseLinking.docID());


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
