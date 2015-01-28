package com.bbn.kbp.events2014.assessmentDiff.diffLoggers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;

public interface DiffLogger {

  public void logDifference(Response response,
      Symbol leftKey, Symbol rightKey, StringBuilder out);
}
