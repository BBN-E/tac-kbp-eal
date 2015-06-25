package com.bbn.kbp.events2014.transformers;

import com.bbn.kbp.events2014.ScoringData;

import com.google.common.collect.ImmutableList;

public final class ScoringDataTransformationSequence implements ScoringDataTransformation {
  private final ImmutableList<ScoringDataTransformation> preprocessors;

  private ScoringDataTransformationSequence(
      final Iterable<? extends ScoringDataTransformation> preprocessors) {
    this.preprocessors = ImmutableList.copyOf(preprocessors);
  }

  public static ScoringDataTransformation compose(Iterable<ScoringDataTransformation> transformations) {
    return new ScoringDataTransformationSequence(transformations);
  }

  @Override
  public ScoringData transform(ScoringData scoringData) {
    ScoringData result = scoringData;
    for (final ScoringDataTransformation preprocessor : preprocessors) {
      result = preprocessor.transform(result);
    }
    return result;
  }

  @Override
  public void logStats() {
    for (final ScoringDataTransformation preprocessor : preprocessors) {
      preprocessor.logStats();
    }
  }
}
