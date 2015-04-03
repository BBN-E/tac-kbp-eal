package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.TypeRoleFillerRealisSet;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

class ExactMatchEventArgumentLinkingAligner implements EventArgumentLinkingAligner {

  public ExactMatchEventArgumentLinkingAligner() {
  }

  /**
   * Converts a {@link ResponseLinking} to an {@link EventArgumentLinking} using a {@link
   * CorefAnnotation} from an {@link com.bbn.kbp.events2014.AnswerKey} to canonicalize the
   * responses.  If the canonicalization is inconsistent with the response linking, an {@link
   * java.lang.IllegalArgumentException} will be thrown.
   */
  public EventArgumentLinking align(ResponseLinking responseLinking,
      AnswerKey answerKey) throws InconsistentLinkingException {
    checkArgument(answerKey.docId() == responseLinking.docID());
    assertLinkingSubsetOfAnswerKey(responseLinking, answerKey);

    final ImmutableMultimap<TypeRoleFillerRealis, Response> canonicalToResponses =
        Multimaps.index(responseLinking.allResponses(),
            TypeRoleFillerRealis.extractFromSystemResponse(
                answerKey.corefAnnotation().strictCASNormalizerFunction()));

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

  private void assertLinkingSubsetOfAnswerKey(ResponseLinking responseLinking, AnswerKey answerKey)
      throws InconsistentLinkingException {
    final Set<Response> inLinkingButNotKey =
        Sets.difference(responseLinking.allResponses(), answerKey.allResponses());
    if (!inLinkingButNotKey.isEmpty()) {
      throw new InconsistentLinkingException("Response linking should be a subset of answer key."
          + " In linking only:\n" + inLinkingButNotKey);
    }
  }

  @Override
  public ResponseLinking alignToResponseLinking(EventArgumentLinking eventArgumentLinking,
      AnswerKey answerKey) {
    // For every Response in answerKey, answerKey.corefAnnotation().strictCASNormalizerFunction() will try to find
    // a canonical coreferent for the Response's CAS (KBPString), by checking CorefAnnotation.CASesToIDs
    // If the KBPString does not exist in CASesToIDs, then an Exception will be thrown
    final ImmutableMultimap<TypeRoleFillerRealis, Response> canonicalToResponses =
        Multimaps.index(answerKey.allResponses(),
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

  /**
   * Maps a {@code Set<Response>} to a {@code Set<TypeRoleFillerRealis>} which is supported by
   * exactly the same responses.  If any response supporting a TRFR is present in {@code
   * responseGroup}, that TRFR will appear in the returned set. However, if such a TRFR is supported
   * by responses but within and outside {@code responseSet}, an {@link
   * java.lang.IllegalArgumentException} is thrown.
   */
  private static ImmutableSet<TypeRoleFillerRealis> canonicalizeResponseSet(
      Set<Response> responseGroup,
      Multimap<TypeRoleFillerRealis, Response> canonicalToResponses,
      Multimap<Response, TypeRoleFillerRealis> responsesToCanonical)
      throws InconsistentLinkingException {
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
          throw new InconsistentLinkingException(
              "Response linking inconsistent with coreference annotation:");
        }
      }
    }

    return ret;
  }
}
