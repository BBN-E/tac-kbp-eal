package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Ordering;

import org.immutables.func.Functional;
import org.immutables.value.Value;

/**
 * Represents a single event argument together with its assessment (assessment). This class is
 * immutable. This is obsolete in the 2016 evaluation, since assessments are no longer used.
 *
 * @author rgabbard
 */
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@TextGroupPublicImmutable
@Value.Immutable
@Functional
abstract class _AssessedResponse {

  @Value.Parameter
  public abstract Response response();

  @Value.Parameter
  public abstract ResponseAssessment assessment();

  /**
   * Returns true if and only if the AET, AER, base filler, and CAS assessments are all correct and
   * the annotated realis matches the response realis.
   */
  public final boolean isCompletelyCorrect() {
    final ResponseAssessment label = assessment();
    return label.realis().isPresent() && label.realis().get() == response().realis()
        && label.justificationSupportsEventType().orNull() == FieldAssessment.CORRECT
        && label.justificationSupportsRole().orNull() == FieldAssessment.CORRECT
        && label.entityCorrectFiller().orNull() == FieldAssessment.CORRECT
        && label.baseFillerCorrect().orNull() == FieldAssessment.CORRECT;
  }

  /**
   * Returns true if and only if the AET, AER, base filler, and CAS assessments are all either
   * correct or inexact, and the annotated realis matches the response realis.
   */
  public final boolean isCorrectUpToInexactJustifications() {
    final ResponseAssessment label = assessment();
    return label.realis().isPresent() && label.realis().get() == response().realis()
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
    return "[" + response().toString() + " = " + assessment().toString() + "]";
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

  /**
   * @deprecated
   */
  @Deprecated
  public static final Ordering<AssessedResponse> ByOld2014Id =
      com.bbn.kbp.events2014.Response.ByOld2014Id.onResultOf(
          AssessedResponseFunctions.response());

  public static final Ordering<AssessedResponse> byUniqueIdentifierOrdering() {
    return com.bbn.kbp.events2014.Response.byUniqueIdOrdering().onResultOf(
        AssessedResponseFunctions.response());
  }


  /**
   * Creates an {@code AssessedResponse} which assesses the provided response as completely correct
   * and marks it with the given mention type. This is useful for tests and not much else.
   */
  public static AssessedResponse assessCorrectly(Response r,
      FillerMentionType mentionType) {
    return AssessedResponse.of(r, ResponseAssessment.of(Optional.of(FieldAssessment.CORRECT),
        Optional.of(FieldAssessment.CORRECT), Optional.of(FieldAssessment.CORRECT),
        Optional.of(r.realis()), Optional.of(FieldAssessment.CORRECT),
        Optional.of(mentionType)));
  }

  /**
   * Assesse the provided {@link Response} with an incorrect event type. This is only for use in
   * tests.
   */
  public static AssessedResponse assessWithIncorrectEventType(final Response response) {
    return AssessedResponse.of(response, ResponseAssessment.of(Optional.of(
            FieldAssessment.INCORRECT),
        Optional.<FieldAssessment>absent(), Optional.<FieldAssessment>absent(),
        Optional.<KBPRealis>absent(), Optional.<FieldAssessment>absent(),
        Optional.<FillerMentionType>absent()));
  }
}
