package com.bbn.kbp.events2014.transformers;

import com.bbn.kbp.events2014.ScoringData;

public interface ScoringDataTransformation {
  ScoringData transform(ScoringData scoringData);

  void logStats();
}
