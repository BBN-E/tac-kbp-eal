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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

public final class MakeAllRealisActual implements ScoringDataTransformation {
  private static final Logger log = LoggerFactory.getLogger(MakeAllRealisActual.class);

  private MakeAllRealisActual() {

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
    if (!responseMapping.isIdentity()) {
      log.info("Realis neutralization resulting in {}", responseMapping.summaryString());
    }

    AnswerKey newAnswerKey = neutralizeAssessments(responseMapping.apply(scoringData.answerKey().get()));
    ret.withAnswerKey(newAnswerKey);

    if (scoringData.argumentOutput().isPresent()) {
      ret.withSystemOutput(responseMapping.apply(scoringData.argumentOutput().get()));
    }

    return ret.build();
  }

  @Override
  public void logStats() {

  }
}
