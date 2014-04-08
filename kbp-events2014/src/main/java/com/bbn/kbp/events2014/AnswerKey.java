package com.bbn.kbp.events2014;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.*;

import static com.bbn.bue.common.collections.IterableUtils.allEqual;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.difference;

/**
 * Represents an answer key which may contain both responses with assessments and unannotated
 * responses which need assessing. Each answer key is tied to a particular document.  It is
 * guaranteed that no response will be both assessed and unassessed.
 */
public final class AnswerKey {
	private static final Logger log = LoggerFactory.getLogger(AnswerKey.class);

    private final Symbol docid;
    private final ImmutableSet<AsssessedResponse> annotatedArgs;
    private final ImmutableSet<Response> unannotatedResponses;

	private AnswerKey(final Symbol docId, final Iterable<AsssessedResponse> annotatedArgs,
		final Iterable<Response> unannotatedResponses)
	{
		this.docid = checkNotNull(docId);
		this.annotatedArgs = ImmutableSet.copyOf(annotatedArgs);
		this.unannotatedResponses = ImmutableSet.copyOf(unannotatedResponses);
		assertConsistency();
	}

    /** Get all assessed responses in this answer key **/
	public ImmutableSet<AsssessedResponse> annotatedResponses() {
		return annotatedArgs;
	}

    /** Get all unassessed response in this answer key **/
	public ImmutableSet<Response> unannotatedResponses() {
		return unannotatedResponses;
	}

    /**
     * Get all responses, assessed and unassessed
     * @return
     */
    public ImmutableSet<Response> allResponses() {
        return ImmutableSet.copyOf(Iterables.concat(FluentIterable.from(annotatedResponses()).transform(AsssessedResponse.Response),
                unannotatedResponses()));
    }

    /**
     * The document ID of the document this answer key corresponds to. Will never be null.
     * @return
     */
	public Symbol docId() {
		return docid;
	}

    /**
     * Returns whether all responses in this answer key have been assessed.
     * @return
     */
	public boolean completelyAnnotated() {
		return unannotatedResponses.isEmpty();
	}

	public AnswerKey copyAddingPossiblyUnannotated(final Iterable<Response> newUnannotated) {
        final Set<Response> currentlyAnnotated = FluentIterable.from(annotatedArgs)
                .transform(AsssessedResponse.Response).toSet();
        final Iterable<Response> newTrulyUnannotated =
            Iterables.filter(newUnannotated, not(in(currentlyAnnotated)));

		return AnswerKey.from(docId(), annotatedResponses(),
			concat(unannotatedResponses(), newTrulyUnannotated));
	}

    /**
     * Get a new AnswerKey which is a copy of this one, except:
     *  (a) if a response was previously unannotated and an assessment is present in {@code newAnnotatedResponses},
     *  the new assessment will be used in the result.
     *  (b) if a response was previously annotated and an assessment is present in {@code newAnnotatedResponses},
     *  the new assessment will replace the old one in the result.
     *
     * @param newAnnotatedResponses
     * @return
     */
	public AnswerKey copyAnnotating(final Iterable<AsssessedResponse> newAnnotatedResponses) {
        // we use immutable maps for these things to guarantee deterministic order ~ rgabbard
        final Map<Response, AsssessedResponse> currentResponseToAnnotation =
                Maps.uniqueIndex(annotatedResponses(), AsssessedResponse.Response);
        final Map<Response, AsssessedResponse> newResponseToAnnotation =
                Maps.uniqueIndex(newAnnotatedResponses, AsssessedResponse.Response);

        final Set<Response> responsesAnnotatedInResult = Sets.union(
                currentResponseToAnnotation.keySet(), newResponseToAnnotation.keySet());

        final ImmutableList.Builder<AsssessedResponse> resultAnnotatedResponseB = ImmutableList.builder();

        for (final Response response : responsesAnnotatedInResult) {
            if (newResponseToAnnotation.containsKey(response)) {
                resultAnnotatedResponseB.add(newResponseToAnnotation.get(response));
            } else {
                // this must be present by construction
                resultAnnotatedResponseB.add(currentResponseToAnnotation.get(response));
            }
        }

		return new AnswerKey(docId(),
			resultAnnotatedResponseB.build(),
			difference(unannotatedResponses, responsesAnnotatedInResult));
	}

	public static AnswerKey from(final Symbol docId, final Iterable<AsssessedResponse> annotatedArgs,
		final Iterable<Response> unannotatedResponses)
	{
		return new AnswerKey(docId, annotatedArgs, unannotatedResponses);
	}

	private void assertConsistency() {
		// maps responses (ignoring confidence) to their annotations
		final Multimap<Response, AsssessedResponse> responseToAnn =
			Multimaps.index(annotatedArgs, AsssessedResponse.Response);

		// check if there are any responses with multiple incompatible annotations.
		for (final Map.Entry<Response, Collection<AsssessedResponse>> entry : responseToAnn.asMap().entrySet()) {
			if (!allEqual(transform(entry.getValue(), AsssessedResponse.Annotation))) {
				throw new RuntimeException(String.format(
					"Inconsistent pooled answer key. The one or more of the following is inconsistent: %s", entry.getValue()));
			}
		}

        // check there are no response listed as both annotated and unannotated
        checkArgument(Sets.intersection(FluentIterable.from(annotatedArgs)
                .transform(AsssessedResponse.Response).toSet(), unannotatedResponses).isEmpty(),
                "There are responses which are both unannotated and unannotated");

        assertNoIncompatibleCorefAnnotations();
	}

    private void assertNoIncompatibleCorefAnnotations() {
        final Map<KBPString, Integer> corefMappings = Maps.newHashMap();
        for (final AsssessedResponse assessedResponse : annotatedResponses()) {
            if (assessedResponse.assessment().coreferenceId().isPresent()) {
                final KBPString CAS = assessedResponse.response().canonicalArgument();
                final int corefId = assessedResponse.assessment().coreferenceId().get();
                final Integer currentMapping = corefMappings.get(CAS);
                if (currentMapping == null) {
                    corefMappings.put(CAS, corefId);
                } else {
                    if (currentMapping.intValue() != corefId) {
                        throw new RuntimeException(String.format(
                       "When attempting to create AnswerKey for document %s, CAS %s had two coref IDs, %d and %d",
                            docId(), CAS, currentMapping, corefId));
                    }
                }
            }
        }
    }
}
