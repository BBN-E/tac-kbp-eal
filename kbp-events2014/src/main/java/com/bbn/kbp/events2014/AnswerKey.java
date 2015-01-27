package com.bbn.kbp.events2014;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.bbn.bue.common.collections.IterableUtils.allEqual;
import static com.google.common.base.Preconditions.*;
import static com.google.common.base.Predicates.*;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;

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
    private final CorefAnnotation corefAnnotation;

	private AnswerKey(final Symbol docId, final Iterable<AssessedResponse> annotatedArgs,
		final Iterable<Response> unannotatedResponses, CorefAnnotation corefAnnotation)
	{
		this.docid = checkNotNull(docId);
		this.annotatedArgs = ImmutableSet.copyOf(annotatedArgs);
		this.unannotatedResponses = ImmutableSet.copyOf(unannotatedResponses);
        this.corefAnnotation = checkNotNull(corefAnnotation);
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

    public CorefAnnotation corefAnnotation() {
        return corefAnnotation;
    }

    /**
     * Get all responses, assessed and unassessed
     * @return
     */
    public ImmutableSet<Response> allResponses() {
        return ImmutableSet.copyOf(concat(FluentIterable.from(annotatedResponses()).transform(AssessedResponse.Response),
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
        final CorefAnnotation.Builder corefBuilder = corefAnnotation.strictCopyBuilder();
        corefBuilder.addUnannotatedCASes(transform(newTrulyUnannotated, Response.CASFunction()));

		return AnswerKey.from(docId(), annotatedResponses(),
			concat(unannotatedResponses(), newTrulyUnannotated), corefBuilder.build());
	}

//    /**
//     * Get a new AnswerKey which is a copy of this one, except:
//     *  (a) if a response was previously unannotated and an assessment is present in {@code newAnnotatedResponses},
//     *  the new assessment will be used in the result.
//     *  (b) if a response was previously annotated and an assessment is present in {@code newAnnotatedResponses},
//     *  the new assessment will replace the old one in the result.
//     *
//     * @param newAnnotatedResponses
//     * @return
//     */
//	public AnswerKey copyAnnotating(final Iterable<AssessedResponse> newAnnotatedResponses) {
//        // we use immutable maps for these things to guarantee deterministic order ~ rgabbard
//        final Map<Response, AssessedResponse> currentResponseToAnnotation =
//                Maps.uniqueIndex(annotatedResponses(), AssessedResponse.Response);
//        final Map<Response, AssessedResponse> newResponseToAnnotation =
//                Maps.uniqueIndex(newAnnotatedResponses, AssessedResponse.Response);
//
//        final Set<Response> responsesAnnotatedInResult = Sets.union(
//                currentResponseToAnnotation.keySet(), newResponseToAnnotation.keySet());
//
//        final ImmutableList.Builder<AssessedResponse> resultAnnotatedResponseB = ImmutableList.builder();
//
//        for (final Response response : responsesAnnotatedInResult) {
//            if (newResponseToAnnotation.containsKey(response)) {
//                resultAnnotatedResponseB.add(newResponseToAnnotation.get(response));
//            } else {
//                // this must be present by construction
//                resultAnnotatedResponseB.add(currentResponseToAnnotation.get(response));
//            }
//        }
//
//		return new AnswerKey(docId(),
//			resultAnnotatedResponseB.build(),
//			difference(unannotatedResponses, responsesAnnotatedInResult));
//	}

	public static AnswerKey from(final Symbol docId, final Iterable<AssessedResponse> annotatedArgs,
		final Iterable<Response> unannotatedResponses, CorefAnnotation corefAnnotation)
	{
		return new AnswerKey(docId, annotatedArgs, unannotatedResponses,
                removeCorefForAbsentStrings(annotatedArgs, unannotatedResponses, corefAnnotation));
	}

    private static CorefAnnotation removeCorefForAbsentStrings(Iterable<AssessedResponse> annotatedArgs,
                 Iterable<Response> unannotatedResponses, CorefAnnotation corefAnnotation)
    {
        return corefAnnotation.copyRemovingStringsNotIn(ImmutableSet.copyOf(
                transform(
                        concat(unannotatedResponses,
                            transform(annotatedArgs, AssessedResponse.Response)),
                        Response.CASFunction())));
    }

    /**
     * Creates an {@code AnswerKey} where the same response may appear both as assessed and unassessed.  This removes
     * any such tuples from the unassessed set before calling {@link #from(com.bbn.bue.common.symbols.Symbol, Iterable, Iterable, CorefAnnotation)}.
     * This is simply provided for convenience.
     *
     * @param docID
     * @param assessed
     * @param unassessedResponses
     * @return
     */
    public static AnswerKey fromPossiblyOverlapping(Symbol docID, Iterable<AssessedResponse> assessed,
                             Iterable<Response> unassessedResponses, CorefAnnotation corefAnnotation)
    {
        final ImmutableSet<AssessedResponse> assessedResponsesSet = ImmutableSet.copyOf(assessed);
        final ImmutableSet<Response> unassessedResponseSet = ImmutableSet.copyOf(unassessedResponses);
        final Set<Response> assessedResponses = FluentIterable.from(assessedResponsesSet)
                .transform(AssessedResponse.Response).toSet();

        if (Sets.intersection(assessedResponses, unassessedResponseSet).isEmpty()) {
            return from(docID, assessedResponsesSet, unassessedResponseSet, corefAnnotation);
        } else {
            return from(docID, assessedResponsesSet, Sets.difference(unassessedResponseSet, assessedResponses),
                    corefAnnotation);
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
					"For %s: inconsistent pooled answer key. The one or more of the following is inconsistent: %s",
                        docid, entry.getValue()));
			}
		}

        // check there are no response listed as both annotated and unannotated
        checkArgument(Sets.intersection(FluentIterable.from(annotatedArgs)
                .transform(AssessedResponse.Response).toSet(), unannotatedResponses).isEmpty(),
                "For %s, there are responses which are both unannotated and unannotated", docid);

        assertNoIncompatibleCorefAnnotations();
        checkState(corefAnnotation.docId() == docId(), "Coref doc ID does not match assessment doc ID");
	}

    private void assertNoIncompatibleCorefAnnotations() {
        final ImmutableSet<KBPString> allResponseCASes = FluentIterable
                .from(allResponses())
                .transform(Response.CASFunction())
                .toSet();
        final Set<KBPString> allCorefCASes = corefAnnotation.allCASes();
        checkState(allResponseCASes.equals(allCorefCASes),
                "CASes from responses must exactly equal CASes from coref assessment");

        // all correctly assessed responses must have coreffed CASes. Coref of others is optional.
        for (final AssessedResponse assessedResponse : annotatedResponses()) {
            if (assessedResponse.assessment().entityCorrectFiller().isPresent()
                    && assessedResponse.assessment().entityCorrectFiller().get().isAcceptable())
            {
                checkState(corefAnnotation.annotatedCASes().contains(assessedResponse.response().canonicalArgument()));
            }
        }

        // further consistency checking already done within the corefAnnotation object
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

    public Optional<AssessedResponse> assess(Response response) {
        final Optional<ResponseAssessment> ret = assessment(response);
        if (ret.isPresent()) {
            return Optional.of(AssessedResponse.from(response, ret.get()));
        } else {
            return Optional.absent();
        }
    }

    public AnswerKey filter(Filter filter) {
        final ImmutableSet<AssessedResponse> newAnnotated = FluentIterable.from(annotatedResponses())
                .filter(filter.assessedFilter()).toSet();
        final ImmutableSet<Response> newUnannotated = FluentIterable.from(unannotatedResponses())
                .filter(filter.unassessedFilter()).toSet();
        final CorefAnnotation newCorefAnnotation = removeCorefForAbsentStrings(newAnnotated,
                newUnannotated, corefAnnotation());
        return new AnswerKey(docId(), newAnnotated, newUnannotated, newCorefAnnotation);
    }

    public AnswerKey unannotatedCopy() {
        final CorefAnnotation.Builder newCorefAnnotation = CorefAnnotation.strictBuilder(docId());
        for (final Response response : allResponses()) {
            newCorefAnnotation.addUnannotatedCAS(response.canonicalArgument());
        }
        return AnswerKey.from(docId(), ImmutableSet.<AssessedResponse>of(), allResponses(), newCorefAnnotation.build());
    }

    public interface Filter {
        public Predicate<AssessedResponse> assessedFilter();
        public Predicate<Response> unassessedFilter();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(docid, annotatedArgs, unannotatedResponses, corefAnnotation);
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
        return Objects.equal(this.docid, other.docid) && Objects.equal(this.annotatedArgs, other.annotatedArgs) && Objects.equal(this.unannotatedResponses, other.unannotatedResponses) && Objects.equal(this.corefAnnotation, other.corefAnnotation);
    }

    public static AnswerKey createEmpty(Symbol docid) {
        return AnswerKey.from(docid, ImmutableSet.<AssessedResponse>of(), ImmutableSet.<Response>of(),
                CorefAnnotation.createEmpty(docid));
    }

    public AnswerKey copyMerging(AnswerKey toMerge) {
        // (1) determine which responses are newly assessed
        final Set<Response> alreadyAssessedInBaseline = FluentIterable.from(annotatedResponses())
                .transform(AssessedResponse.Response).toSet();

        final Predicate<AssessedResponse> ResponseNotAssessedInBaseline =
                compose(
                        not(in(alreadyAssessedInBaseline)),
                        AssessedResponse.Response);
        final Set<AssessedResponse> newAssessedResponses = FluentIterable.from(toMerge.annotatedResponses())
                .filter(ResponseNotAssessedInBaseline)
                .toSet();

        // add newly assessed responses together with baseline assessed responses to new
        // result, fixing the coreference indices of new assessments if needed
        final ImmutableSet.Builder<AssessedResponse> resultAssessed = ImmutableSet.builder();
        resultAssessed.addAll(annotatedResponses());
        resultAssessed.addAll(newAssessedResponses);

        final ImmutableSet<Response> responsesAssessedInAdditional =
                FluentIterable.from(toMerge.annotatedResponses())
                        .transform(AssessedResponse.Response)
                        .toSet();
        final Set<Response> stillUnannotated =
                Sets.union(
                        // things unassessed in baseline which were still not
                        // assessed in additional
                        Sets.difference(unannotatedResponses(),
                                responsesAssessedInAdditional),
                        Sets.difference(toMerge.unannotatedResponses(),
                                alreadyAssessedInBaseline));

        log.info("\t{} additional assessments found; {} remain unassessed", newAssessedResponses.size(),
                stillUnannotated.size());

        return AnswerKey.from(docId(), resultAssessed.build(), stillUnannotated,
                corefAnnotation().copyMerging(toMerge.corefAnnotation()));
    }

    public String toString() {
        return Objects.toStringHelper(this)
                .add("docId", docid)
                .add("assessedResponses", "{" + StringUtils.NewlineJoiner.join(annotatedResponses()) + "}")
                .add("unassessedResponses", "{" + StringUtils.NewlineJoiner.join(unannotatedResponses()))
                .add("coref", corefAnnotation()).toString();
    }
}
