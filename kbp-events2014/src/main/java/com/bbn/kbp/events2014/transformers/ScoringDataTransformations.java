package com.bbn.kbp.events2014.transformers;

import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ScoringData;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Preconditions.checkArgument;

public class ScoringDataTransformations {

  private ScoringDataTransformations() {
    throw new UnsupportedOperationException();
  }

  public static ScoringDataTransformation applyPredicatetoSystemOutput(
      final Predicate<Response> responsePredicate) {
    return new ScoringDataTransformation() {
      @Override
      public ScoringData transform(final ScoringData scoringData) {
        checkArgument(scoringData.argumentOutput().isPresent(), "System output must be present");
        final ImmutableSet.Builder<Response> toDelete = ImmutableSet.builder();
        for (final Response r : scoringData.argumentOutput().get().responses()) {
          if (!responsePredicate.apply(r)) {
            toDelete.add(r);
          }
        }
        return scoringData.modifiedCopy().withArgumentOutput(
            ResponseMapping.delete(toDelete.build())
                .apply(scoringData.argumentOutput().get())).build();
      }

      @Override
      public void logStats() {

      }
    };
  }
}
