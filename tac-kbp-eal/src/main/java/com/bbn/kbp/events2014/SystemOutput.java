package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.transformers.ResponseMapping;

public interface SystemOutput {
  Symbol docID();
  ArgumentOutput arguments();
  SystemOutput copyTransformedBy(ResponseMapping responseMapping);

}
