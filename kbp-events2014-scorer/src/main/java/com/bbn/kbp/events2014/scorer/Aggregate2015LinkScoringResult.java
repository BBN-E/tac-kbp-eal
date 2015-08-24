package com.bbn.kbp.events2014.scorer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@JsonSerialize(as = com.bbn.kbp.events2014.scorer.bin.ImmutableAggregateResult.class)
@JsonDeserialize(as = com.bbn.kbp.events2014.scorer.bin.ImmutableAggregateResult.class)
public abstract class Aggregate2015LinkScoringResult {

  abstract double precision();

  abstract double recall();

  abstract double overall();

  protected void check() {
    checkArgument(precision() >= 0.0);
    checkArgument(recall() >= 0.0);
    checkArgument(overall() >= 0.0);
  }
}
