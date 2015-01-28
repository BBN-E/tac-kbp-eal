package com.bbn.bue.common.diff;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An object for taking two collections of answers and aligns them by querying each answer about
 * what 'question' ({@code Answerable}) it is trying to answer.
 *
 * Answerables should define hashcode and equals appropriately. For example, suppose you have a
 * system for predicting tuples (x,y,z), but you wish to do scoring backing off to only (x,y). You
 * would define an Answerable class representing (x,y) and specify an extraction function (x,y,z)
 * --> (x,y).
 *
 * @author rgabbard
 */
public class AnswerableExtractorAligner<AnswerableType, LeftAnswerType, RightAnswerType> {

  private final Function<? super LeftAnswerType, AnswerableType> leftAnswerExtractor;
  private final Function<? super RightAnswerType, AnswerableType> rightAnswerExtractor;

  /**
   * Create an {@code AnswerableExtractorAligner} where the same type of answer is used for the left
   * and the right.
   *
   * @param extractor Function to transform an answer into an object representing the question it is
   *                  intended to answer.
   */
  public static <AnswerableType, AnswerType> AnswerableExtractorAligner<AnswerableType, AnswerType, AnswerType> forCommonAnswerExtractor(
      final Function<AnswerType, AnswerableType> extractor) {
    return new AnswerableExtractorAligner<AnswerableType, AnswerType, AnswerType>(extractor,
        extractor);
  }

  /**
   * Create an {@code AnswerableExtractorAligner} where the different type of answer is used for the
   * left and the right.
   *
   * @param leftExtractor  Function to transform a left answer into an object representing the
   *                       question it is intended to answer.
   * @param rightExtractor Function to transform a right answer into an object representing the
   *                       question it is intended to answer.
   */
  public static <AnswerableType, LeftAnswerType, RightAnswerType>
  AnswerableExtractorAligner<AnswerableType, LeftAnswerType, RightAnswerType> forAnswerExtractors(
      final Function<LeftAnswerType, AnswerableType> leftExtractor,
      final Function<RightAnswerType, AnswerableType> rightExtractor) {
    return new AnswerableExtractorAligner<AnswerableType, LeftAnswerType, RightAnswerType>(
        leftExtractor, rightExtractor);
  }

  public static <AnswerableType> AnswerableExtractorAligner<AnswerableType, AnswerableType, AnswerableType> forIdentityMapping() {
    return new AnswerableExtractorAligner<AnswerableType, AnswerableType, AnswerableType>(
        Functions.<AnswerableType>identity(), Functions.<AnswerableType>identity());
  }

  /**
   * Generates an alignment between two collections of answers using this aligner.
   *
   * Every left and right answer will appear in the resulting alignment.  All left answers and right
   * answers with the same answerables under the left and right answerable extraction functions will
   * end up aligned.
   */
  public <LeftTrueAnswerType extends LeftAnswerType, RightTrueAnswerType extends RightAnswerType>
  AnswerAlignment<AnswerableType, LeftTrueAnswerType, RightTrueAnswerType> align(
      final Iterable<LeftTrueAnswerType> leftAnswers,
      final Iterable<RightTrueAnswerType> rightAnswers) {
    final Multimap<AnswerableType, LeftTrueAnswerType> equivClassesToLeft =
        Multimaps.index(leftAnswers, leftAnswerExtractor);
    final Multimap<AnswerableType, RightTrueAnswerType> equivClassesToRight =
        Multimaps.index(rightAnswers, rightAnswerExtractor);

    return AnswerAlignment.create(equivClassesToLeft, equivClassesToRight);
  }

  private AnswerableExtractorAligner(
      final Function<? super LeftAnswerType, AnswerableType> leftAnswerExtractor,
      final Function<? super RightAnswerType, AnswerableType> rightAnswerExtractor) {
    this.leftAnswerExtractor = checkNotNull(leftAnswerExtractor);
    this.rightAnswerExtractor = checkNotNull(rightAnswerExtractor);
  }
}
