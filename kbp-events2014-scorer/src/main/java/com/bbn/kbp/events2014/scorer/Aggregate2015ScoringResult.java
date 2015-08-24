package com.bbn.kbp.events2014.scorer;

import com.bbn.kbp.events2014.scorer.bin.ImmutableAggregateResult;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@JsonSerialize(as = ImmutableAggregateResult.class)
@JsonDeserialize(as = ImmutableAggregateResult.class)
public abstract class Aggregate2015ScoringResult {

  abstract ImmutableAggregate2015ArgScoringResult argument();

  abstract ImmutableAggregate2015LinkScoringResult linking();

  abstract double overall();

  @Value.Check
  protected void check() {
    checkArgument(overall() >= 0.0);
  }
}
