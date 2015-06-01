package com.bbn.kbp.events2014.transformers;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;

import com.google.common.base.Function;
import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Created by rgabbard on 5/29/15.
 */
public final class FixLowercaseXInTemporals {

  private static final Logger log = LoggerFactory.getLogger(FixLowercaseXInTemporals.class);

  private FixLowercaseXInTemporals() {
    throw new UnsupportedOperationException();
  }

  public static Function<SystemOutput, SystemOutput> forSystemOutput() {
    return new Function<SystemOutput, SystemOutput>() {
      @Override
      public SystemOutput apply(final SystemOutput input) {
        final SystemOutput.Builder ret = input.modifiedCopyBuilder();
        for (final Response response : input.responses()) {
          final Optional<Response> fixedResponse = fixLowercaseXInTime(response);
          if (fixedResponse.isPresent()) {
            log.info("Fixing bad time in system output from {} to {}",
                response.canonicalArgument().string(),
                fixedResponse.get().canonicalArgument().string());
            ret.replaceResponseKeepingScoreAndMetadata(response, fixedResponse.get());
          }
        }
        return ret.build();
      }
    };
  }

  public static Function<AnswerKey, AnswerKey> forAnswerKey() {
    return new Function<AnswerKey, AnswerKey>() {
      final Random rng = new Random();

      @Override
      public AnswerKey apply(final AnswerKey input) {
        final AnswerKey.Builder ret = input.modifiedCopyBuilder();

        for (final Response r : input.allResponses()) {
          final Optional<Response> fixedResponse = fixLowercaseXInTime(r);
          if (fixedResponse.isPresent()) {
            log.info("Fixing bad time in answer key from {} to {}", r.canonicalArgument().string(),
                fixedResponse.get().canonicalArgument().string());
            ret.replaceAsssessedResponseMaintainingAssessment(r, fixedResponse.get(), rng);
          }
        }

        return ret.build();
      }
    };
  }

  private static Optional<Response> fixLowercaseXInTime(Response response) {
    if (response.isTemporal()) {
      final String originalString = response.canonicalArgument().string();
      if (originalString.contains("x")) {
        final String fixedString = originalString.replaceAll("x", "X");
        return Optional.of(response.copyWithSwappedCanonicalArgument(
            response.canonicalArgument().copyWithString(fixedString)));
      }
    }
    return Optional.absent();
  }
}
