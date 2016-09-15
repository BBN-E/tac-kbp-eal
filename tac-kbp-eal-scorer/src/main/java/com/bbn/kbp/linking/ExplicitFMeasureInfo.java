package com.bbn.kbp.linking;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.annotations.Beta;

import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@JsonSerialize(as=ImmutableExplicitFMeasureInfo.class)
@JsonDeserialize(as=ImmutableExplicitFMeasureInfo.class)
@Beta
public abstract class ExplicitFMeasureInfo  {
  public abstract double precision();
  public abstract double recall();
  public abstract double f1();

  public static ExplicitFMeasureInfo of(double precision, double recall, double f1) {
    return new ExplicitFMeasureInfo.Builder().precision(precision)
        .recall(recall).f1(f1).build();
  }

  @Value.Check
  protected void check() {
    checkArgument(f1() >= 0.0);
    checkArgument(precision ()>= 0.0);
    checkArgument(recall() >= 0.0);
  }

  @Override
  public String toString() {
    return String.format("P/R/F %.2f/%.2f/%.2f", precision(), recall(), f1());
  }

  public static class Builder extends ImmutableExplicitFMeasureInfo.Builder {}
}
