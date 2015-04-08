package com.bbn.kbp.linking;

import com.bbn.bue.common.annotations.MoveToBUECommon;

import com.google.common.annotations.Beta;

@MoveToBUECommon
@Beta
public final class ExplicitFMeasureInfo  {
  private final double precision;
  private final double recall;
  private final double F1;

  public ExplicitFMeasureInfo(final double precision, final double recall, final double F1) {
    this.F1 = F1;
    this.precision = precision;
    this.recall = recall;
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
}
