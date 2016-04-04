package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.transformers.ResponseMapping;

import static com.google.common.base.Preconditions.checkNotNull;

public final class DocumentSystemOutput2014 implements DocumentSystemOutput {
  private final ArgumentOutput argumentOutput;

  private DocumentSystemOutput2014(final ArgumentOutput argumentOutput) {
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
  public DocumentSystemOutput copyTransformedBy(final ResponseMapping responseMapping) {
    return new DocumentSystemOutput2014(responseMapping.apply(argumentOutput));
  }

  public static DocumentSystemOutput2014 from(final ArgumentOutput argumentOutput) {
    return new DocumentSystemOutput2014(argumentOutput);
  }
}
