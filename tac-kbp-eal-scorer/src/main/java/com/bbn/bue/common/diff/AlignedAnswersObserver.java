package com.bbn.bue.common.diff;

import java.io.IOException;

/**
 * Code which observes two collections of answers on the same collection of answerable items.  For
 * example, you could use this to compute F-measure, accuracy, etc.
 *
 * @author rgabbard
 */
public interface AlignedAnswersObserver<AnswerableType, LeftAnswer, RightAnswer> {

  public void observe(AlignedAnswers<AnswerableType, LeftAnswer, RightAnswer> observable);

  public void report() throws IOException;
}
