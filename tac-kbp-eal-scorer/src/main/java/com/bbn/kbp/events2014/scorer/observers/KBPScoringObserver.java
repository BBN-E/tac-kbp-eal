package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.kbp.events2014.EventArgScoringAlignment;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by rgabbard on 3/31/15.
 */
public abstract class KBPScoringObserver<EquivClassType> {
  private final String name;

  protected KBPScoringObserver() {
    this.name = getClass().getName();
  }

  protected KBPScoringObserver(final String name) {
    this.name = checkNotNull(name);
    checkArgument(!name.isEmpty());
  }

  public final String name() {
    return name;
  }

  /**
   * Called once, at the beginning of scoring.
   */
  public void startCorpus() {

  }

  /**
   * Called once, at the end of scoring.
   */
  public void endCorpus() {

  }

  /**
   * Writes corpus-level output to the specified directory
   */
  public void writeCorpusOutput(final File directory) throws IOException {

  }

  public abstract void observeDocument(EventArgScoringAlignment<EquivClassType> scoringAlignment,
      File perDocLogDir)
      throws IOException;

}
