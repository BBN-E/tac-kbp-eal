package com.bbn.kbp.events2014.scorer;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.ArgumentOutput;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public interface ScoringAligner<EquivClassType> {

  EventArgScoringAlignment<EquivClassType> align(AnswerKey answerKey, ArgumentOutput argumentOutput);
}

