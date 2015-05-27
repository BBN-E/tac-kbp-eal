package com.bbn.kbp.events2014.scorer.bin;


import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.filter;

/**
 * This preprocessor alters the system output and the answer key together to try to provide an
 * approximation of what scores would be if a system's CAS resolution were perfect.
 */
public final class CorefNeutralizingPreprocessor implements Preprocessor {

  private static final Logger log = LoggerFactory.getLogger(CorefNeutralizingPreprocessor.class);

  private final Preprocessor wrappedPreprocessor;

  private int passedThrough = 0;
  private int swappedCASForSameBF = 0;
  private int swappedCASDifferentBF = 0;
  private int deleted = 0;

  private CorefNeutralizingPreprocessor(
      final Preprocessor wrappedPreprocessor) {
    this.wrappedPreprocessor = checkNotNull(wrappedPreprocessor);
  }

  public static CorefNeutralizingPreprocessor createWrappingPreprocessor(
      Preprocessor preprocessor) {
    return new CorefNeutralizingPreprocessor(preprocessor);
  }

  @Override
  public Result preprocess(final SystemOutput systemOutput,
      final AnswerKey answerKey) {
    final Preprocessor.Result wrappedResult = wrappedPreprocessor.preprocess(systemOutput,
        answerKey);

    assertRealisIsNeutralized(wrappedResult);

    final ImmutableMultimap<String, AssessedResponse> answerKeyByTypeRoleBaseFiller =
        Multimaps.index(filter(answerKey.annotatedResponses(),
                AssessedResponse.IsCorrectUpToInexactJustifications),
            Functions.compose(TypeRoleBaseFiller, AssessedResponse.Response));
    final ImmutableMultimap<String, AssessedResponse> answerKeyByTypeRole =
        Multimaps.index(filter(answerKey.annotatedResponses(),
                AssessedResponse.IsCorrectUpToInexactJustifications),
            Functions.compose(TypeRole, AssessedResponse.Response));

    final List<Response> newResponses = Lists.newArrayList();
    //final List<AssessedResponse> additionalAssessedResponses = Lists.newArrayList();
    for (final Response response : wrappedResult.systemOutput().responses()) {
      final AssessedResponse assessedResponse = wrappedResult.answerKey().assess(response).get();
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
          newResponses
              .add(Iterables.getFirst(correctPoolResponsesSharingSameTypeRoleBF, null).response());
          ++swappedCASForSameBF;
        } else {
          final Collection<AssessedResponse> correctPoolResponsesSharingSameTypeRole =
              answerKeyByTypeRole.get(TypeRole.apply(response));
          if (!correctPoolResponsesSharingSameTypeRole.isEmpty()) {
            newResponses
                .add(Iterables.getFirst(correctPoolResponsesSharingSameTypeRole, null).response());
            ++swappedCASDifferentBF;
          } else {
            // do nothing, drop the response if we can't find anything to map it to
            ++deleted;
          }
        }
      } else {
        // this is not a coref error - either it is right or it is wrong for a another
        // reason, so we just copy it over
        newResponses.add(response);
        ++passedThrough;
      }
    }

    final SystemOutput corefNeutralizedSystemOutput =
        SystemOutput.createWithConstantScore(systemOutput.docId(),
            newResponses, 1.0);
    return new Preprocessor.Result(corefNeutralizedSystemOutput, wrappedResult.answerKey(),
        wrappedResult.normalizer());
  }

  public void logStats() {
    wrappedPreprocessor.logStats();
    log.info("Coref neutralizer pass {} through, swapped CAS while keeping BF for {},"
            + "swapped CAS and BF for {}, and deleted {}", passedThrough, swappedCASForSameBF,
        swappedCASDifferentBF, deleted);
  }


  private void assertRealisIsNeutralized(final Result wrappedResult) {
    for (final Response response : wrappedResult.systemOutput().responses()) {
      if (response.realis() != KBPRealis.Actual) {
        throw new RuntimeException(
            "CorefNeutralizingProcessor is only intended to be used in conjunction with neutralizing realis");
      }
    }

    for (final AssessedResponse annResponse : wrappedResult.answerKey().annotatedResponses()) {
      if (annResponse.response().realis() != KBPRealis.Actual ||
          annResponse.assessment().realis().get() != KBPRealis.Actual) {
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
