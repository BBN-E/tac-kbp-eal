package com.bbn.kbp.events2014;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Ordering;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a single event argument together with its assessment (assessment). This class is
 * immutable.
 *
 * @author rgabbard
 */
public class AssessedResponse {

  private final Response item;
  private final ResponseAssessment label;

  private AssessedResponse(final Response item, final ResponseAssessment label) {
    this.item = checkNotNull(item);
    this.label = checkNotNull(label);
  }

  /**
   * Create an {@code AssessedResponse} which applies the given {@code ResponseAssessment} to the
   * given {@code Response}.
   *
   * @param argument   May not be null.
   * @param annotation May not be null.
   */
  public static AssessedResponse from(final Response argument,
      final ResponseAssessment annotation) {
    return new AssessedResponse(argument, annotation);
  }

  public Response response() {
    return item;
  }

  public ResponseAssessment assessment() {
    return label;
  }

  /**
   * Returns true if and only if the AET, AER, base filler, and CAS assessments are all correct and
   * the annotated realis matches the response realis.
   */
  public boolean isCompletelyCorrect() {
    return label.realis().isPresent() && label.realis().get() == item.realis()
        && label.justificationSupportsEventType().orNull() == FieldAssessment.CORRECT
        && label.justificationSupportsRole().orNull() == FieldAssessment.CORRECT
        && label.entityCorrectFiller().orNull() == FieldAssessment.CORRECT
        && label.baseFillerCorrect().orNull() == FieldAssessment.CORRECT;
  }

  /**
   * Returns true if and only if the AET, AER, base filler, and CAS assessments are all either
   * correct or inexact, and the annotated realis matches the response realis.
   */
  public boolean isCorrectUpToInexactJustifications() {
    return label.realis().isPresent() && label.realis().get() == item.realis()
        && FieldAssessment.isAcceptable(label.justificationSupportsEventType())
        && FieldAssessment.isAcceptable(label.justificationSupportsRole())
        && FieldAssessment.isAcceptable(label.entityCorrectFiller())
        && FieldAssessment.isAcceptable(label.baseFillerCorrect());
  }

  /**
   * Searches a list of annotated responses for the one which corresponds to the provided response.
   * If there are multiple {@code AssessedResponse}s which match the provided response, only the
   * first will be returned.  However, the definition of the task guarantees there is a single
   * assessment for each response, so this should never occur. If the list contains no matching
   * annotated response, {@code Optional.absent()} is returned.
   *
   * @param argument      May not be null.
   * @param annotatedArgs May not be null.
   */
  public static Optional<AssessedResponse> findAnnotationForArgument(final Response argument,
      final Iterable<AssessedResponse> annotatedArgs) {
    for (final AssessedResponse annotatedArg : annotatedArgs) {
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
    final AssessedResponse other = (AssessedResponse) obj;
    return Objects.equal(item, other.item)
        && Objects.equal(label, other.label);
  }


  public static final Predicate<AssessedResponse> IsCompletelyCorrect =
      new Predicate<AssessedResponse>() {
        @Override
        public boolean apply(final AssessedResponse input) {
          return input.isCompletelyCorrect();
        }
      };

  public static final Predicate<AssessedResponse> IsCorrectUpToInexactJustifications =
      new Predicate<AssessedResponse>() {
        @Override
        public boolean apply(final AssessedResponse input) {
          return input.isCorrectUpToInexactJustifications();
        }
      };

  public static final Function<AssessedResponse, ResponseAssessment> Annotation =
      new Function<AssessedResponse, ResponseAssessment>() {
        @Override
        public ResponseAssessment apply(final AssessedResponse x) {
          return x.assessment();
        }
      };

  public static final Function<AssessedResponse, Response> Response =
      new Function<AssessedResponse, Response>() {
        @Override
        public Response apply(final AssessedResponse x) {
          return x.response();
        }
      };

  /**
   * @deprecated
   */
  @Deprecated
  public static final Ordering<AssessedResponse> ByOld2014Id =
      com.bbn.kbp.events2014.Response.ByOld2014Id.onResultOf(
          AssessedResponse.Response);

  public static final Ordering<AssessedResponse> byUniqueIdentifierOrdering() {
    return com.bbn.kbp.events2014.Response.byUniqueIdOrdering().onResultOf(
        AssessedResponse.Response);
  }


  /**
   * Creates an {@code AssessedResponse} which assesses the provided response as completely correct
   * and marks it with the given mention type. This is useful for tests and not much else.
   */
  public static AssessedResponse assessCorrectly(Response r,
      ResponseAssessment.MentionType mentionType) {
    return AssessedResponse.from(r, ResponseAssessment.create(Optional.of(FieldAssessment.CORRECT),
        Optional.of(FieldAssessment.CORRECT), Optional.of(FieldAssessment.CORRECT),
        Optional.of(r.realis()), Optional.of(FieldAssessment.CORRECT),
        Optional.of(mentionType)));
  }

  /**
   * Assesse the provided {@link Response} with an incorrect event type. This is only for use in
   * tests.
   */
  public static AssessedResponse assessWithIncorrectEventType(final Response response) {
    return AssessedResponse.from(response, ResponseAssessment.create(Optional.of(
            FieldAssessment.INCORRECT),
        Optional.<FieldAssessment>absent(), Optional.<FieldAssessment>absent(),
        Optional.<KBPRealis>absent(), Optional.<FieldAssessment>absent(),
        Optional.<ResponseAssessment.MentionType>absent()));
  }
}
