package com.bbn.kbp.events2014.scorer.bin;


import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ScoringData;
import com.bbn.kbp.events2014.transformers.ResponseMapping;
import com.bbn.kbp.events2014.transformers.ScoringDataTransformation;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.filter;

/**
 * This preprocessor alters the system output and the answer key together to try to provide an
 * approximation of what scores would be if a system's CAS resolution were perfect.
 */
public final class CorefNeutralizingPreprocessor implements ScoringDataTransformation {

  private static final Logger log = LoggerFactory.getLogger(CorefNeutralizingPreprocessor.class);

  private int passedThrough = 0;
  private int swappedCASForSameBF = 0;
  private int swappedCASDifferentBF = 0;
  private int deleted = 0;

  private CorefNeutralizingPreprocessor() {
  }

  public static CorefNeutralizingPreprocessor create() {
    return new CorefNeutralizingPreprocessor();
  }


  public ScoringData transform(final ScoringData input) {
    checkArgument(input.answerKey().isPresent()
        && input.argumentOutput().isPresent(), "Both systme output and an answer key must be "
        + "present to neutralize coref");
    assertRealisIsNeutralized(input);

    final AnswerKey answerKey = input.answerKey().get();
    final ArgumentOutput argumentOutput = input.argumentOutput().get();

    final ImmutableMultimap<String, AssessedResponse> answerKeyByTypeRoleBaseFiller =
        Multimaps.index(filter(answerKey.annotatedResponses(),
                AssessedResponse.IsCorrectUpToInexactJustifications),
            Functions.compose(TypeRoleBaseFiller, AssessedResponse.Response));
    final ImmutableMultimap<String, AssessedResponse> answerKeyByTypeRole =
        Multimaps.index(filter(answerKey.annotatedResponses(),
                AssessedResponse.IsCorrectUpToInexactJustifications),
            Functions.compose(TypeRole, AssessedResponse.Response));

    final ImmutableMap.Builder<Response, Response> responseReplacements = ImmutableMap.builder();
    final ImmutableSet.Builder<Response> toDelete = ImmutableSet.builder();

    for (final Response response : argumentOutput.responses()) {
      final AssessedResponse assessedResponse = answerKey.assess(response).get();
      if (FieldAssessment.isAcceptable(assessedResponse.assessment().baseFillerCorrect()) &&
          !FieldAssessment.isAcceptable(assessedResponse.assessment().entityCorrectFiller())) {
        // this is a coref error, one of the cases we want to fix
        final Collection<AssessedResponse> correctPoolResponsesSharingSameTypeRoleBF =
            answerKeyByTypeRoleBaseFiller.get(TypeRoleBaseFiller.apply(response));
        if (!correctPoolResponsesSharingSameTypeRoleBF.isEmpty()) {
          // if there is a correct answer key responses which matches this in type, role,
          // and base filler but has a different CAS, replace this response with that
          // one. If there is more than one, we take the first in the arbitrary order
          // of the answer key
          responseReplacements.put(response,
              Iterables.getFirst(correctPoolResponsesSharingSameTypeRoleBF, null).response());
          ++swappedCASForSameBF;
        } else {
          final Collection<AssessedResponse> correctPoolResponsesSharingSameTypeRole =
              answerKeyByTypeRole.get(TypeRole.apply(response));
          if (!correctPoolResponsesSharingSameTypeRole.isEmpty()) {
            responseReplacements.put(response,
                Iterables.getFirst(correctPoolResponsesSharingSameTypeRole, null).response());
            ++swappedCASDifferentBF;
          } else {
            // do nothing, drop the response if we can't find anything to map it to
            toDelete.add(response);
            ++deleted;
          }
        }
      } else {
        // this is not a coref error - either it is right or it is wrong for a another
        // reason, so we just copy it over
        ++passedThrough;
      }
    }

    final ResponseMapping responseMapping = ResponseMapping.create(responseReplacements.build(),
        toDelete.build());

    if (!responseMapping.isIdentity()) {
      log.info("Coref neutralization resulted in {}", responseMapping.summaryString());
    }

    final ScoringData.Builder ret = ScoringData.builder().from(input);
    ret.argumentOutput(responseMapping.apply(argumentOutput));
    if (input.systemLinking().isPresent()) {
      ret.systemLinking(responseMapping.apply(input.systemLinking().get()));
    }
    return ret.build();
  }

  public void logStats() {
    log.info("Coref neutralizer pass {} through, swapped CAS while keeping BF for {},"
            + "swapped CAS and BF for {}, and deleted {}", passedThrough, swappedCASForSameBF,
        swappedCASDifferentBF, deleted);
  }


  private void assertRealisIsNeutralized(final ScoringData input) {
    for (final Response response : input.argumentOutput().get().responses()) {
      if (response.realis() != KBPRealis.Actual) {
        throw new RuntimeException(
            "CorefNeutralizingProcessor is only intended to be used in conjunction with neutralizing realis");
      }
    }

    for (final AssessedResponse annResponse : input.answerKey().get().annotatedResponses()) {
      if (annResponse.response().realis() != KBPRealis.Actual ||
          (annResponse.assessment().realis().isPresent()
               && annResponse.assessment().realis().get() != KBPRealis.Actual)) {
        throw new RuntimeException(
            "CorefNeutralizingProcessor is only intended to be used in conjunction with neutralizing realis");
      }
    }
  }

  private static final Function<Response, String> TypeRoleBaseFiller =
      new Function<Response, String>() {
        @Override
        public String apply(final Response response) {
          return response.type().asString() + "-" + response.role().asString()
              + "-" + response.baseFiller().toString();
        }
      };

  private static final Function<Response, String> TypeRole = new Function<Response, String>() {
    @Override
    public String apply(final Response response) {
      return response.type().asString() + "-" + response.role().asString();
    }
  };
}
