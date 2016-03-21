package com.bbn.kbp.linking;

import com.bbn.bue.common.annotations.MoveToBUECommon;

import com.google.common.annotations.Beta;

import static com.google.common.base.Preconditions.checkArgument;

@MoveToBUECommon
@Beta
public final class ExplicitFMeasureInfo  {
  private final double precision;
  private final double recall;
  private final double F1;

  public ExplicitFMeasureInfo(final double precision, final double recall, final double F1) {
    this.F1 = F1;
    checkArgument(F1 >= 0.0);
    this.precision = precision;
    checkArgument(precision >= 0.0);
    this.recall = recall;
    checkArgument(recall >= 0.0);
  }

  public double f1() {
    return F1;
  }

  public double precision() {
    return precision;
  }

  public double recall() {
    return recall;
  }

  @Override
  public String toString() {
    return String.format("P/R/F %.2f/%.2f/%.2f", precision(), recall(), f1());
  }
}
