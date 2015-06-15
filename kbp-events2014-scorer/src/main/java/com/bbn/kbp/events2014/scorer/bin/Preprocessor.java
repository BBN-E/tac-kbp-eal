package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.SystemOutput;

import com.google.common.base.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jdeyoung on 3/23/15.
 * Interface to define preprocessing steps for KBP
 */
public interface Preprocessor {

  Preprocessor.Result preprocess(final SystemOutput systemOutput,
      final AnswerKey answerKey);

  void logStats();

  class Result {
    private final AnswerKey answerKey;
    private final SystemOutput systemOutput;
    private final Function<KBPString, KBPString> normalizer;

    protected Result(SystemOutput systemOutput, AnswerKey answerKey,
        Function<KBPString, KBPString> normalizer) {
      this.answerKey = checkNotNull(answerKey);
      this.systemOutput = checkNotNull(systemOutput);
      this.normalizer = checkNotNull(normalizer);
    }

    public AnswerKey answerKey() {
      return answerKey;
    }

    public SystemOutput systemOutput() {
      return systemOutput;
    }

    public Function<KBPString, KBPString> normalizer() {
      return normalizer;
    }

  }
}
