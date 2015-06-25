package com.bbn.kbp.events2014.transformers;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.ScoringData;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Preconditions.checkArgument;

public final class MakeAllRealisActual implements ScoringDataTransformation {
  private MakeAllRealisActual() {
    throw new UnsupportedOperationException();
  }

  public static MakeAllRealisActual create() {
    return new MakeAllRealisActual();
  }

  private static ResponseAssessment neutralizeResponseAssessment(
      ResponseAssessment responseAssessment) {
    if (responseAssessment.realis().isPresent()) {
      return responseAssessment.copyWithModifiedRealisAssessment(Optional.of(KBPRealis.Actual));
    } else {
      return responseAssessment;
    }
  }

  private static AnswerKey neutralizeAssessments(AnswerKey input) {
    final AnswerKey.Builder ret = input.modifiedCopyBuilder();

    for (final AssessedResponse assessedResponse : input.annotatedResponses()) {
      final ResponseAssessment neutralizedAssessment =
          neutralizeResponseAssessment(assessedResponse.assessment());
      if (!neutralizedAssessment.equals(assessedResponse.assessment())) {
        ret.replaceAssessment(assessedResponse.response(), neutralizedAssessment);
      }
    }
    return ret.build();
  }

  private static ResponseMapping responseMapping(final AnswerKey answerKey) {
    final ImmutableMap.Builder<Response, Response> replacements = ImmutableMap.builder();
    for (final Response response : answerKey.allResponses()) {
      final Response replacement = response.copyWithSwappedRealis(KBPRealis.Actual);
      if (!response.equals(replacement)) {
        replacements.put(response, replacement);
      }
    }
    return ResponseMapping.create(replacements.build(), ImmutableSet.<Response>of());
  }

  @Override
  public ScoringData transform(final ScoringData scoringData) {
    checkArgument(!scoringData.referenceLinking().isPresent()
        && !scoringData.systemLinking().isPresent(), "Realis neutralization is not currently compatible with "
        + "linking scoring");
    checkArgument(scoringData.answerKey().isPresent(), "Answer key must be present to neutralize realis");

    final ScoringData.Builder ret = scoringData.modifiedCopy();

    final ResponseMapping responseMapping = responseMapping(scoringData.answerKey().get());

    AnswerKey newAnswerKey = neutralizeAssessments(responseMapping.apply(scoringData.answerKey().get()));
    ret.withAnswerKey(newAnswerKey);

    if (scoringData.systemOutput().isPresent()) {
      ret.withSystemOutput(responseMapping.apply(scoringData.systemOutput().get()));
    }

    return ret.build();
  }

  @Override
  public void logStats() {

  }
}

/*final class AssessmentMapping {
  private final ImmutableMap<Response, ResponseAssessment> replacements;

  private AssessmentMapping(
      final Map<Response, ResponseAssessment> replacements) {
    this.replacements = ImmutableMap.copyOf(replacements);
  }

  public static AssessmentMapping from(Map<Response, ResponseAssessment> replacements) {
    return new AssessmentMapping(replacements);
  }

  public AnswerKey apply(AnswerKey input) {
    final AnswerKey.Builder ret = input.modifiedCopyBuilder();

    for (final Map.Entry<Response, ResponseAssessment> e : replacements.entrySet()) {
      ret.replaceAssessment(e.getKey(), e.getValue());
    }
    return ret.build();
  }
}
*/
