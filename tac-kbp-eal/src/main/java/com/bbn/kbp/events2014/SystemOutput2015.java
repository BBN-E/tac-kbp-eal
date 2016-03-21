package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.transformers.ResponseMapping;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class SystemOutput2015 implements SystemOutput {
  private final ArgumentOutput argumentOutput;
  private final ResponseLinking linking;

  private SystemOutput2015(final ArgumentOutput argumentOutput, final ResponseLinking linking) {
    this.argumentOutput = checkNotNull(argumentOutput);
    this.linking = checkNotNull(linking);
    checkArgument(linking.docID().equals(argumentOutput.docId()));
    for (final Response response : linking.allResponses()) {
      checkArgument(argumentOutput.responses().contains(response), "Response %s in linking is not "
          + "present in argument output", response);
    }
  }

  public static SystemOutput2015 from(final ArgumentOutput argumentOutput, final ResponseLinking linking) {
    return new SystemOutput2015(argumentOutput, linking);
  }

  @Override
  public Symbol docID() {
    return argumentOutput.docId();
  }

  @Override
  public ArgumentOutput arguments() {
    return argumentOutput;
  }

  public ResponseLinking linking() {
    return linking;
  }

  @Override
  public SystemOutput2015 copyTransformedBy(final ResponseMapping responseMapping) {
    return new SystemOutput2015(responseMapping.apply(argumentOutput),
        responseMapping.apply(linking));
  }
}
