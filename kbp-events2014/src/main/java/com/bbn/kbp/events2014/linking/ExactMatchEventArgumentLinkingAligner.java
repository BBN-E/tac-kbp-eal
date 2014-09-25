package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.*;
import com.google.common.base.Predicate;
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

    public static ExactMatchEventArgumentLinkingAligner createWithRealises(Iterable<KBPRealis> realises) {
        return new ExactMatchEventArgumentLinkingAligner(false, realises);
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
        
        // if I don't care about correctness, then I actually get back all (annotated and unannotated) responses
        final ImmutableSet<Response> relevantResponses = getRelevantResponses(answerKey);

        // responseLinking.allResponses() : ResponseSets and Incomplete responses
        // responseLinking should be a subset of answerKey, so inLinkingButNotKey should be empty
        final Set<Response> inLinkingButNotKey = Sets.difference(responseLinking.allResponses(), relevantResponses);
        if(!inLinkingButNotKey.isEmpty()) {
        	//StringBuffer inLinkingButNotKeyBuffer = new StringBuffer();
        	//for(final Response r : inLinkingButNotKey) {
        	//	inLinkingButNotKeyBuffer.append(r.toString());
        	//	inLinkingButNotKeyBuffer.append("\n");
        	//}
        	//StringBuffer inAnswerBuffer = new StringBuffer();
        	//for(final Response r : relevantResponses) {
        	//	inAnswerBuffer.append(r.toString());
        	//	inAnswerBuffer.append("\n");
        	//}
        	
        	//System.out.println("In linking only:\n" + inLinkingButNotKeyBuffer.toString() + " In answer:\n" + inAnswerBuffer.toString());
        	
            throw new InconsistentLinkingException("Response linking should be a subset of answer key."
            		+ " In linking only:\n" + inLinkingButNotKey);
        }

        final CorefAnnotation corefAnnotation = answerKey.corefAnnotation();

        final ImmutableMultimap<TypeRoleFillerRealis, Response> canonicalToResponses =
                Multimaps.index(responseLinking.allResponses(),
                        TypeRoleFillerRealis.extractFromSystemResponse(
                                corefAnnotation.strictCASNormalizerFunction()));

        final Multimap<Response, TypeRoleFillerRealis> responsesToCanonical =
                canonicalToResponses.inverse();

        //System.out.println("== Mapped each Response to its TypeRoleFillerRealis ==");
        //for(final Map.Entry<Response, TypeRoleFillerRealis> entry : responsesToCanonical.entries()) {
        //	System.out.println(entry.getKey() + " ==> " + entry.getValue());
        //}
        
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
    	// For every Response in answerKey, answerKey.corefAnnotation().strictCASNormalizerFunction() will try to find 
    	// a canonical coreferent for the Response's CAS (KBPString), by checking CorefAnnotation.CASesToIDs
    	// If the KBPString does not exist in CASesToIDs, then an Exception will be thrown 
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
