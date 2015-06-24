package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.SystemOutput;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Set;

public final class MakeAllRealisActual {
  private MakeAllRealisActual() {
    throw new UnsupportedOperationException();
  }

  private static ResponseAssessment neutralizeResponseAssessment(
      ResponseAssessment responseAssessment) {
    if (responseAssessment.realis().isPresent()) {
      return responseAssessment.copyWithModifiedRealisAssessment(Optional.of(KBPRealis.Actual));
    } else {
      return responseAssessment;
    }
  }

  public static Function<AnswerKey, AnswerKey> forAssessments() {
    return new Function<AnswerKey, AnswerKey>() {
      @Override
      public AnswerKey apply(AnswerKey input) {
        // seenResponses is to ensure we don't end up with two different
        // assessments for the same response, or have the same response
        // as both assessed and unassessed
        final Set<Response> seenResponses = Sets.newHashSet();
        final ImmutableSet.Builder<AssessedResponse> newAssessed = ImmutableSet.builder();

        for (final AssessedResponse response : input.annotatedResponses()) {
          final Response neutralizedResponse =
              response.response().copyWithSwappedRealis(KBPRealis.Actual);
          if (!seenResponses.contains(neutralizedResponse)) {
            newAssessed.add(AssessedResponse.from(neutralizedResponse,
                neutralizeResponseAssessment(response.assessment())));
          }
          seenResponses.add(neutralizedResponse);
        }

        final ImmutableSet.Builder<Response> newUnannotated = ImmutableSet.builder();

        for (final Response response : input.unannotatedResponses()) {
          final Response neutralizedResponse = response.copyWithSwappedRealis(KBPRealis.Actual);
          if (!seenResponses.contains(neutralizedResponse)) {
            newUnannotated.add(neutralizedResponse);
          }
          seenResponses.add(neutralizedResponse);
        }

        return AnswerKey.from(input.docId(), newAssessed.build(), newUnannotated.build(),
            input.corefAnnotation());
      }
    };
  }

  public static AnswerKeyToResponseMappingRule forResponses() {
    return new AnswerKeyToResponseMappingRule() {
      @Override
      public ResponseMapping computeResponseTransformation(final AnswerKey answerKey) {
        final ImmutableMap.Builder<Response, Response> replacements = ImmutableMap.builder();
        for (final Response response : answerKey.allResponses()) {
          final Response replacement = response.copyWithSwappedRealis(KBPRealis.Actual);
          if (!response.equals(replacement)) {
            replacements.put(response, replacement);
          }
        }
        return ResponseMapping.create(replacements.build(), ImmutableSet.<Response>of());
      }
    };
  }

  @Deprecated
  public static Function<SystemOutput, SystemOutput> forSystemOutput() {
    return new Function<SystemOutput, SystemOutput>() {
      @Override
      public SystemOutput apply(SystemOutput input) {
        final ImmutableSet.Builder<Scored<Response>> newResponses = ImmutableSet.builder();

        for (final Scored<Response> assessedResponse : input.scoredResponses()) {
          newResponses.add(
              Scored.from(assessedResponse.item().copyWithSwappedRealis(KBPRealis.Actual),
                  assessedResponse.score()));
        }

        return SystemOutput.from(input.docId(), newResponses.build());
      }
    };
  }
}
