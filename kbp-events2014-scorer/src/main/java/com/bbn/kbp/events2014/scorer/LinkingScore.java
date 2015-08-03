package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.linking.ExplicitFMeasureInfo;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by rgabbard on 4/3/15.
 */
public final class LinkingScore {

  private final Symbol docID;
  private final ExplicitFMeasureInfo scores;
  private final int referenceLinkingSize;

  private LinkingScore(final Symbol docID, int referenceLinkingSize,
      final ExplicitFMeasureInfo scores) {
    checkArgument(referenceLinkingSize >= 0);
    this.referenceLinkingSize = referenceLinkingSize;
    this.scores = checkNotNull(scores);
    this.docID = checkNotNull(docID);
  }

  public int referenceLinkingSize() {
    return referenceLinkingSize;
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
    return docID;
  }

  @Override
  public String toString() {
    return String.format("LinkingScore[%.2f/%.2f/%.2f]",
        100 * precision(), 100 * recall(), 100 * F1());
  }

  public static LinkingScore from(
      final EventArgumentLinking referenceArgumentLinking,
      final ExplicitFMeasureInfo scores) {
    return new LinkingScore(referenceArgumentLinking.docID(),
        referenceArgumentLinking.allLinkedEquivalenceClasses().size(), scores);
  }
}
