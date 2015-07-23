package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.transformers.ResponseMapping;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SystemOutput2014 implements SystemOutput {
  private final ArgumentOutput argumentOutput;

  private SystemOutput2014(final ArgumentOutput argumentOutput) {
    this.argumentOutput = checkNotNull(argumentOutput);
  }

  @Override
  public Symbol docID() {
    return argumentOutput.docId();
  }

  @Override
  public ArgumentOutput arguments() {
    return argumentOutput;
  }

  @Override
  public SystemOutput copyTransformedBy(final ResponseMapping responseMapping) {
    return new SystemOutput2014(responseMapping.apply(argumentOutput));
  }

  public static SystemOutput2014 from(final ArgumentOutput argumentOutput) {
    return new SystemOutput2014(argumentOutput);
  }
}
