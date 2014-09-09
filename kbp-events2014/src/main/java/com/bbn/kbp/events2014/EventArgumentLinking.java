package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Objects;
import com.google.common.collect.*;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class EventArgumentLinking {
    private final Symbol docID;
    private final ImmutableSet<TypeRoleFillerRealisSet> coreffedArgSets;
    private final ImmutableSet<TypeRoleFillerRealis> incomplete;

    private EventArgumentLinking(Symbol docID, Iterable<TypeRoleFillerRealisSet> coreffedArgSets,
                                 Iterable<TypeRoleFillerRealis> incomplete)
    {
        this.docID = checkNotNull(docID);
        this.coreffedArgSets = ImmutableSet.copyOf(coreffedArgSets);
        this.incomplete = ImmutableSet.copyOf(incomplete);
    }

    public Symbol docID() {
        return docID;
    }

    public ImmutableSet<TypeRoleFillerRealisSet> linkedAsSet() {
        return coreffedArgSets;
    }

    public ImmutableSet<TypeRoleFillerRealis> incomplete() {
        return incomplete;
    }

    /** Converts a {@link ResponseLinking} to an {@link EventArgumentLinking} using
     * a {@link CorefAnnotation} to canonicalize the responses.  If the canonicalization
     * is inconsistent with the response linking, an {@link java.lang.IllegalArgumentException}
     * will be thrown.
     */
    public static EventArgumentLinking createFromResponseLinking(ResponseLinking responseLinking,
                                                                 CorefAnnotation corefAnnotation)
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

        return new EventArgumentLinking(responseLinking.docID(), coreffedArgs.build(),
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
                    throw new IllegalArgumentException("Response linking inconsistent with coreference annotation:");
                }
            }
        }

        return ret;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(docID, coreffedArgSets, incomplete);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final EventArgumentLinking other = (EventArgumentLinking) obj;
        return Objects.equal(this.docID, other.docID) && Objects.equal(this.coreffedArgSets, other.coreffedArgSets)
                && Objects.equal(this.incomplete, other.incomplete);
    }
}
