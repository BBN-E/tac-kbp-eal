package com.bbn.kbp.events2014.scorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

import org.immutables.value.Value;

import java.util.Collection;

import javax.annotation.Generated;

import static com.google.common.base.Preconditions.checkArgument;

@JsonSerialize(as = ImmutableAggregate2015ScoringResult.class)
@JsonDeserialize(as = ImmutableAggregate2015ScoringResult.class)
public abstract class Aggregate2015ScoringResult {

  public abstract ImmutableAggregate2015ArgScoringResult argument();

  public abstract ImmutableAggregate2015LinkScoringResult linking();

  public abstract double overall();

  @Value.Check
  protected void check() {
    checkArgument(overall() >= 0.0);
  }
}

