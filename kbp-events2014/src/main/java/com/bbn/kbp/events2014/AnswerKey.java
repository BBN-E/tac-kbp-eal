package com.bbn.kbp.events2014;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
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
    private final ImmutableSet<AssessedResponse> annotatedArgs;
    private final ImmutableSet<Response> unannotatedResponses;

	private AnswerKey(final Symbol docId, final Iterable<AssessedResponse> annotatedArgs,
		final Iterable<Response> unannotatedResponses)
	{
		this.docid = checkNotNull(docId);
		this.annotatedArgs = ImmutableSet.copyOf(annotatedArgs);
		this.unannotatedResponses = ImmutableSet.copyOf(unannotatedResponses);
		assertConsistency();
	}

    /** Get all assessed responses in this answer key **/
	public ImmutableSet<AssessedResponse> annotatedResponses() {
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
        return ImmutableSet.copyOf(Iterables.concat(FluentIterable.from(annotatedResponses()).transform(AssessedResponse.Response),
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
                .transform(AssessedResponse.Response).toSet();
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
	public AnswerKey copyAnnotating(final Iterable<AssessedResponse> newAnnotatedResponses) {
        // we use immutable maps for these things to guarantee deterministic order ~ rgabbard
        final Map<Response, AssessedResponse> currentResponseToAnnotation =
                Maps.uniqueIndex(annotatedResponses(), AssessedResponse.Response);
        final Map<Response, AssessedResponse> newResponseToAnnotation =
                Maps.uniqueIndex(newAnnotatedResponses, AssessedResponse.Response);

        final Set<Response> responsesAnnotatedInResult = Sets.union(
                currentResponseToAnnotation.keySet(), newResponseToAnnotation.keySet());

        final ImmutableList.Builder<AssessedResponse> resultAnnotatedResponseB = ImmutableList.builder();

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

	public static AnswerKey from(final Symbol docId, final Iterable<AssessedResponse> annotatedArgs,
		final Iterable<Response> unannotatedResponses)
	{
		return new AnswerKey(docId, annotatedArgs, unannotatedResponses);
	}

    /**
     * Creates an {@code AnswerKey} where the same response may appear both as assessed and unassessed.  This removes
     * any such tuples from the unassessed set before calling {@link #from(com.bbn.bue.common.symbols.Symbol, Iterable, Iterable)}.
     * This is simply provided for convenience.
     *
     * @param docID
     * @param assessed
     * @param unassessedResponses
     * @return
     */
    public static AnswerKey fromPossiblyOverlapping(Symbol docID, Iterable<AssessedResponse> assessed,
                                                    Iterable<Response> unassessedResponses)
    {
        final ImmutableSet<AssessedResponse> assessedResponsesSet = ImmutableSet.copyOf(assessed);
        final ImmutableSet<Response> unassessedResponseSet = ImmutableSet.copyOf(unassessedResponses);
        final Set<Response> assessedResponses = FluentIterable.from(assessedResponsesSet)
                .transform(AssessedResponse.Response).toSet();

        if (Sets.intersection(assessedResponses, unassessedResponseSet).isEmpty()) {
            return from(docID, assessedResponsesSet, unassessedResponseSet);
        } else {
            return from(docID, assessedResponsesSet, Sets.difference(unassessedResponseSet, assessedResponses));
        }

    }


    private void assertConsistency() {
		// maps responses (ignoring confidence) to their annotations
		final Multimap<Response, AssessedResponse> responseToAnn =
			Multimaps.index(annotatedArgs, AssessedResponse.Response);

		// check if there are any responses with multiple incompatible annotations.
		for (final Map.Entry<Response, Collection<AssessedResponse>> entry : responseToAnn.asMap().entrySet()) {
			if (!allEqual(transform(entry.getValue(), AssessedResponse.Annotation))) {
				throw new RuntimeException(String.format(
					"Inconsistent pooled answer key. The one or more of the following is inconsistent: %s", entry.getValue()));
			}
		}

        // check there are no response listed as both annotated and unannotated
        checkArgument(Sets.intersection(FluentIterable.from(annotatedArgs)
                .transform(AssessedResponse.Response).toSet(), unannotatedResponses).isEmpty(),
                "There are responses which are both unannotated and unannotated");

        assertNoIncompatibleCorefAnnotations();
	}

    private void assertNoIncompatibleCorefAnnotations() {
        final Map<KBPString, Integer> corefMappings = Maps.newHashMap();
        for (final AssessedResponse assessedResponse : annotatedResponses()) {
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

    public Optional<ResponseAssessment> assessment(Response response) {
        checkNotNull(response);
        for (final AssessedResponse assessedResponse : annotatedResponses()) {
            if (assessedResponse.response().equals(response)) {
                return Optional.of(assessedResponse.assessment());
            }
        }
        return Optional.absent();
    }

    public ImmutableMap<KBPString,Integer> makeCASToCorefMap() {
        final ImmutableMap.Builder<KBPString, Integer> ret = ImmutableMap.builder();

        // we use an ImmutableMap, even though it requires this auxilliary set
        // to avoid double-adding the same mappings, because it aids in determinism
        // by guaranteeing iteration order
        final Set<KBPString> seen = Sets.newHashSet();

        // we know this will result in a valid map due to the consistency checks
        // at construction
        for (final AssessedResponse assessedResponse : annotatedResponses()) {
            if (assessedResponse.assessment().coreferenceId().isPresent()
                    &&!seen.contains(assessedResponse.response().canonicalArgument()))
            {
                ret.put(assessedResponse.response().canonicalArgument(),
                        assessedResponse.assessment().coreferenceId().get());
                seen.add(assessedResponse.response().canonicalArgument());
            }
        }

        return ret.build();
    }

    public Multimap<Integer, KBPString> makeCorefToCASMultimap() {
        return makeCASToCorefMap().asMultimap().inverse();
    }


    @Override
    public int hashCode() {
        return Objects.hashCode(docid, annotatedArgs, unannotatedResponses);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final AnswerKey other = (AnswerKey) obj;
        return Objects.equal(this.docid, other.docid) && Objects.equal(this.annotatedArgs, other.annotatedArgs) && Objects.equal(this.unannotatedResponses, other.unannotatedResponses);
    }

}
