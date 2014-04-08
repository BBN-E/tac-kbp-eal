package com.bbn.kbp.events2014;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Ordering;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a single event argument together with its assessment (assessment). This class is immutable.
 *
 * @author rgabbard
 *
 */
public class AsssessedResponse {
    private final Response item;
    private final ResponseAssessment label;

    private AsssessedResponse(final Response item, final ResponseAssessment label) {
		this.item = checkNotNull(item);
		this.label = checkNotNull(label);
	}

    /**
     * Create an {@code AsssessedResponse} which applies the given {@code ResponseAssessment} to
     * the given {@code Response}.
     * @param argument May not be null.
     * @param annotation May not be null.
     * @return
     */
	public static AsssessedResponse from(final Response argument, final ResponseAssessment annotation) {
		return new AsssessedResponse(argument, annotation);
	}

	public Response response() {
		return item;
	}

	public ResponseAssessment assessment() {
		return label;
	}

    /**
     * Returns true if and only if the AET, AER, base filler, and CAS assessments are
     * all correct and the annotated realis matches the response realis.
     * @return
     */
	public boolean isCompletelyCorrect() {
		return label.realis().isPresent() && label.realis().get() == item.realis()
				&& label.justificationSupportsEventType() == FieldAssessment.CORRECT
				&& label.justificationSupportsRole().orNull() == FieldAssessment.CORRECT
				&& label.entityCorrectFiller().orNull() == FieldAssessment.CORRECT
				&& label.baseFillerCorrect().orNull() == FieldAssessment.CORRECT;
	}

    /**
     * Returns true if and only if the AET, AER, base filler, and CAS assessments are
     * all either correct or inexact, and the annotated realis matches the response realis.
     * @return
     */
	public boolean isCorrectUpToInexactJustifications() {
		return label.realis().isPresent() && label.realis().get() == item.realis()
				&& label.justificationSupportsEventType().isAcceptable()
				&& FieldAssessment.isAcceptable(label.justificationSupportsRole())
				&& FieldAssessment.isAcceptable(label.entityCorrectFiller())
				&& FieldAssessment.isAcceptable(label.baseFillerCorrect());
	}

    /**
     * Searches a list of annotated responses for the one which corresponds to the provided response.
     * If there are multiple {@code AsssessedResponse}s which match the provided response, only the
     * first will be returned.  However, the definition of the task guarantees there is a single
     * assessment for each response, so this should never occur. If the list contains no matching annotated
     * response, {@code Optional.absent()} is returned.
     *
     * @param argument May not be null.
     * @param annotatedArgs May not be null.
     * @return
     */
	public static Optional<AsssessedResponse> findAnnotationForArgument(final Response argument,
		final Iterable<AsssessedResponse> annotatedArgs)
	{
		for (final AsssessedResponse annotatedArg : annotatedArgs) {
			if (annotatedArg.response().equals(argument)) {
				return Optional.of(annotatedArg);
			}
		}
		return Optional.absent();
	}


	@Override
	public String toString() {
		return "[" + item.toString() + " = " + label.toString() + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(item, label);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final AsssessedResponse other = (AsssessedResponse) obj;
		return Objects.equal(item, other.item)
			&& Objects.equal(label, other.label);
	}



	public static final Predicate<AsssessedResponse> IsCompletelyCorrect = new Predicate<AsssessedResponse> () {
		@Override
		public boolean apply(final AsssessedResponse input) {
			return input.isCompletelyCorrect();
		}
	};

	public static final Predicate<AsssessedResponse> IsCorrectUpToInexactJustifications = new Predicate<AsssessedResponse> () {
		@Override
		public boolean apply(final AsssessedResponse input) {
			return input.isCorrectUpToInexactJustifications();
		}
	};

	public static final Function<AsssessedResponse, ResponseAssessment> Annotation = new Function<AsssessedResponse, ResponseAssessment> () {
		@Override
		public ResponseAssessment apply(final AsssessedResponse x) {
			return x.assessment();
		}
	};

	public static final Function<AsssessedResponse, Response> Response = new Function<AsssessedResponse, Response> () {
		@Override
		public Response apply(final AsssessedResponse x) {
			return x.response();
		}
	};

    /**
     * Orders {@code AsssessedResponse}s by the ID of their response.
     */
    public static final Ordering<AsssessedResponse> ById = com.bbn.kbp.events2014.Response.ById.onResultOf(
            AsssessedResponse.Response);
}
