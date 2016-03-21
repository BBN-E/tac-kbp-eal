package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.linking.ExplicitFMeasureInfo;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by rgabbard on 4/3/15.
 */
public final class LinkingScore {

  private final EventArgumentLinking referenceArgumentLinking;
  private final ExplicitFMeasureInfo scores;

  private LinkingScore(final EventArgumentLinking referenceArgumentLinking,
      final ExplicitFMeasureInfo scores) {
    this.scores = checkNotNull(scores);
    this.referenceArgumentLinking = checkNotNull(referenceArgumentLinking);
  }

  public int referenceLinkingSize() {
    return referenceArgumentLinking.allLinkedEquivalenceClasses().size();
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

  public Symbol docID() {
    return referenceArgumentLinking.docID();
  }

  public EventArgumentLinking referenceArgumentLinking() {
    return referenceArgumentLinking;
  }

  @Override
  public String toString() {
    return String.format("LinkingScore[%.2f/%.2f/%.2f]",
        100 * precision(), 100 * recall(), 100 * F1());
  }

  public static LinkingScore from(
      final EventArgumentLinking referenceArgumentLinking,
      final ExplicitFMeasureInfo scores) {
    return new LinkingScore(referenceArgumentLinking, scores);
  }
}
