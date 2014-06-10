package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

public final class ProbableInferenceCases implements Function<SystemOutput, SystemOutput> {

    @Override
    public SystemOutput apply(SystemOutput input) {
        final ImmutableList.Builder<Scored<Response>> possibleInferenceCases = ImmutableList.builder();

        for (final Scored<Response> response : input.scoredResponses()) {
            if (!response.item().additionalArgumentJustifications().isEmpty()
                || response.item().predicateJustifications().size() >1 )
            {
                possibleInferenceCases.add(response);
            }
        }

        return SystemOutput.from(input.docId(), possibleInferenceCases.build());
    }

    /**
     * Creates a filter which keeps only responses with either non-empty AJ or multi-sentence
     * PJs
     * @return
     */
    public static ProbableInferenceCases createKeepingMultisentenceJustifications() {
        return new ProbableInferenceCases();
    }

    private ProbableInferenceCases() {

    }


}
