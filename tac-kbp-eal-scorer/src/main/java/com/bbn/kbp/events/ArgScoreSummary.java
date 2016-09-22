package com.bbn.kbp.events;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.evaluation.FMeasureCounts;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.immutables.func.Functional;
import org.immutables.value.Value;

@Value.Immutable
@Functional
@TextGroupImmutable
@JsonSerialize
@JsonDeserialize
public abstract class ArgScoreSummary {
  abstract FMeasureCounts fMeasureCounts();
  abstract double argScore();

  public static ArgScoreSummary of(double argScore, FMeasureCounts fMeasureCounts) {
    return new Builder().argScore(argScore)
      .fMeasureCounts(fMeasureCounts).build();
  }

  public static class Builder extends ImmutableArgScoreSummary.Builder {}
}
