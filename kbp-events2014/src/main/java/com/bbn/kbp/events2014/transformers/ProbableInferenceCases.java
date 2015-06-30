package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.Response;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

public final class ProbableInferenceCases implements Function<ArgumentOutput, ArgumentOutput> {

  @Override
  public ArgumentOutput apply(ArgumentOutput input) {
    final ImmutableList.Builder<Scored<Response>> possibleInferenceCases = ImmutableList.builder();

    for (final Scored<Response> response : input.scoredResponses()) {
      if (!response.item().additionalArgumentJustifications().isEmpty()
          || response.item().predicateJustifications().size() > 1) {
        possibleInferenceCases.add(response);
      }
    }

    return ArgumentOutput.from(input.docId(), possibleInferenceCases.build(), input.allMetadata());
  }

  /**
   * Creates a filter which keeps only responses with either non-empty AJ or multi-sentence PJs
   */
  public static ProbableInferenceCases createKeepingMultisentenceJustifications() {
    return new ProbableInferenceCases();
  }

  private ProbableInferenceCases() {

  }


}
