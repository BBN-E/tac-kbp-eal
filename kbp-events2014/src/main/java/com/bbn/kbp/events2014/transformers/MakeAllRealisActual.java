package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.*;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public final class MakeAllRealisActual {
    private MakeAllRealisActual() {
        throw new UnsupportedOperationException();
    }

    public static Function<AnswerKey, AnswerKey> forAnswerKey() {
        return new Function<AnswerKey, AnswerKey>() {
            @Override
            public AnswerKey apply(AnswerKey input) {
                final ImmutableList.Builder<Response> newUnannotated = ImmutableList.builder();

                for (final Response response : input.unannotatedResponses()) {
                    newUnannotated.add(response.copyWithSwappedRealis(KBPRealis.Actual));
                }

                final ImmutableList.Builder<AssessedResponse> newAssessed = ImmutableList.builder();

                for (final AssessedResponse response : input.annotatedResponses()) {
                    newAssessed.add(AssessedResponse.from(
                            response.response().copyWithSwappedRealis(KBPRealis.Actual),
                            response.assessment().copyWithModifiedRealisAssessment(Optional.of(KBPRealis.Actual))));
                }

                return AnswerKey.from(input.docId(), newAssessed.build(), newUnannotated.build());
            }
        };
    }

    public static Function<SystemOutput, SystemOutput> forSystemOutput() {
        return new Function<SystemOutput, SystemOutput>() {
            @Override
            public SystemOutput apply(SystemOutput input) {
                final ImmutableList.Builder<Scored<Response>> newResponses = ImmutableList.builder();

                for (final Scored<Response> response : input.scoredResponses()) {
                    newResponses.add(Scored.from(response.item().copyWithSwappedRealis(KBPRealis.Actual),
                            response.score()));
                }

                return SystemOutput.from(input.docId(), newResponses.build());
            }
        };
    }
}
