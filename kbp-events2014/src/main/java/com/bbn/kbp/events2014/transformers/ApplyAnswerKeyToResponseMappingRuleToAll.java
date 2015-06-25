package com.bbn.kbp.events2014.transformers;

import com.bbn.kbp.events2014.ScoringData;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by rgabbard on 6/25/15.
 */
public class ApplyAnswerKeyToResponseMappingRuleToAll implements ScoringDataTransformation {
  private final AnswerKeyToResponseMappingRule rule;

  private ApplyAnswerKeyToResponseMappingRuleToAll(
      final AnswerKeyToResponseMappingRule rule) {
    this.rule = checkNotNull(rule);
  }

  public static ScoringDataTransformation forRuleApplyingToBoth(AnswerKeyToResponseMappingRule rule) {
    return new ApplyAnswerKeyToResponseMappingRuleToAll(rule);
  }

  @Override
  public ScoringData transform(ScoringData input) {
    checkArgument(input.answerKey().isPresent(), "Cannot apply a transformation based on "
        + "the answer key if it is absent");
    input.answerKey().get().checkCompletelyAssesses(input.systemOutput().get());
    final ResponseMapping responseMapping = rule.computeResponseTransformation(input.answerKey().get());
    ScoringData.Builder ret = input.modifiedCopy()
        .withAnswerKey(responseMapping.apply(input.answerKey().get()));
    if (input.systemOutput().isPresent()) {
      ret.withSystemOutput(responseMapping.apply(input.systemOutput().get()));
    }
    if (input.systemLinking().isPresent()) {
      ret.withSystemLinking(responseMapping.apply(input.systemLinking().get()));
    }
    if (input.referenceLinking().isPresent()) {
      ret.withReferenceLinking(responseMapping.apply(input.referenceLinking().get()));
    }
    return ret.build();
  }

  @Override
  public void logStats() {

  }
}
