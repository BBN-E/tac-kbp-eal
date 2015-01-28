package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput;

public interface LinkingStrategy {

  public ResponseLinking linkResponses(final SystemOutput systemOutput, final AnswerKey answerKey);
}
