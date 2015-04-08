package com.bbn.kbp.events2014.scorer;

import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.linking.ExplicitFMeasureInfo;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by rgabbard on 4/3/15.
 */
public final class LinkingScore {

  private final ExplicitFMeasureInfo scores;
  private final ResponseLinking referenceResponseLinking;
  private final ResponseLinking referenceSystemLinking;
  private final EventArgumentLinking referenceArgumentLinking;
  private final EventArgumentLinking systemArgumentLinking;

  private LinkingScore(final ResponseLinking referenceResponseLinking,
      final EventArgumentLinking referenceArgumentLinking,
      final ResponseLinking referenceSystemLinking,
      final EventArgumentLinking systemArgumentLinking,
      final ExplicitFMeasureInfo scores) {
    this.referenceArgumentLinking = checkNotNull(referenceArgumentLinking);
    this.scores = checkNotNull(scores);
    this.referenceResponseLinking = checkNotNull(referenceResponseLinking);
    this.referenceSystemLinking = checkNotNull(referenceSystemLinking);
    this.systemArgumentLinking = checkNotNull(systemArgumentLinking);
  }

  public EventArgumentLinking referenceArgumentLinking() {
    return referenceArgumentLinking;
  }

  public double precision() {
    return scores.precision();
  }

  public double recall() {
    return scores.recall();
  }

  public double F1() {
    return scores.f1();
  }

  @Override
  public String toString() {
    return String.format("LinkingScore[%.2f/%.2f/%.2f]",
        100 * precision(), 100 * recall(), 100 * F1());
  }

  public static LinkingScore from(final ResponseLinking referenceResponseLinking,
      final EventArgumentLinking referenceArgumentLinking,
      final ResponseLinking systemLinking,
      final EventArgumentLinking systemArgumentLinking,
      final ExplicitFMeasureInfo scores) {
    return new LinkingScore(referenceResponseLinking, referenceArgumentLinking, systemLinking,
        systemArgumentLinking, scores);
  }
}
