package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.evaluation.FMeasureInfo;

import com.google.common.base.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by rgabbard on 4/3/15.
 */
public final class LinkingScore {

  private final FMeasureInfo fMeasureInfo;

  public LinkingScore(final FMeasureInfo fMeasureInfo) {
    this.fMeasureInfo = checkNotNull(fMeasureInfo);
  }

  public static LinkingScore fromFMeasureInfo(final FMeasureInfo score) {
    return null;
  }

  public double precision() {
    return fMeasureInfo.precision();
  }

  public double recall() {
    return fMeasureInfo.recall();
  }

  public double F1() {
    return fMeasureInfo.recall();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(fMeasureInfo);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final LinkingScore other = (LinkingScore) obj;
    return Objects.equal(this.fMeasureInfo, other.fMeasureInfo);
  }

  @Override
  public String toString() {
    return String.format("LinkingScore[%.2f/%.2f/%.2f]",
        100 * precision(), 100 * recall(), 100 * F1());
  }
}
