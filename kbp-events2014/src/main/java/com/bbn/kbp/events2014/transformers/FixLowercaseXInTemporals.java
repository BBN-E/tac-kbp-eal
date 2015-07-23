package com.bbn.kbp.events2014.transformers;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by rgabbard on 5/29/15.
 */
public final class FixLowercaseXInTemporals implements AnswerKeyToResponseMappingRule {

  private static final Logger log = LoggerFactory.getLogger(FixLowercaseXInTemporals.class);

  private FixLowercaseXInTemporals() {
  }

  public static ScoringDataTransformation asTransformationForBoth() {
    return ApplyAnswerKeyToResponseMappingRuleToAll.forRuleApplyingToBoth(
        new FixLowercaseXInTemporals());
  }

  private static Optional<Response> fixLowercaseXInTime(Response response) {
    if (response.isTemporal()) {
      final String originalString = response.canonicalArgument().string();
      if (originalString.contains("x")) {
        final String fixedString = originalString.replaceAll("xx", "XX");
        return Optional.of(response.copyWithSwappedCanonicalArgument(
            response.canonicalArgument().copyWithString(fixedString)));
      }
    }
    return Optional.absent();
  }

  @Override
  public ResponseMapping computeResponseTransformation(final AnswerKey answerKey) {
    final ImmutableMap.Builder<Response, Response> replacements = ImmutableMap.builder();
    for (final Response response : answerKey.allResponses()) {
      final Optional<Response> fixedResponse = fixLowercaseXInTime(response);
      if (fixedResponse.isPresent()) {
        log.info("Fixing bad time in answer key from {} to {}",
            response.canonicalArgument().string(),
            fixedResponse.get().canonicalArgument().string());
        replacements.put(response, fixedResponse.get());
      }
    }
    return ResponseMapping.create(replacements.build(), ImmutableSet.<Response>of());
  }
}
