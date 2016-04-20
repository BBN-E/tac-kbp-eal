package com.bbn.kbp.events2014.scorer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@JsonSerialize(as = ImmutableAggregate2015LinkScoringResult.class)
@JsonDeserialize(as = ImmutableAggregate2015LinkScoringResult.class)
public abstract class Aggregate2015LinkScoringResult {

  public abstract double precision();

  public abstract double recall();

  public abstract double overall();

  protected void check() {
    checkArgument(precision() >= 0.0);
    checkArgument(recall() >= 0.0);
    checkArgument(overall() >= 0.0);
  }
}
