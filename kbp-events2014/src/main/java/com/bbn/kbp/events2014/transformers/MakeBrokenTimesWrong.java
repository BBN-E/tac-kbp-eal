package com.bbn.kbp.events2014.transformers;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.KBPTIMEXExpression;

import com.google.common.base.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MakeBrokenTimesWrong {

  private static final Logger log = LoggerFactory.getLogger(MakeBrokenTimesWrong.class);

  private MakeBrokenTimesWrong() {
    throw new UnsupportedOperationException();
  }

  public static Function<AnswerKey, AnswerKey> forAnswerKey() {
    return new Function<AnswerKey, AnswerKey>() {
      @Override
      public AnswerKey apply(final AnswerKey input) {
        final AnswerKey.Builder ret = input.modifiedCopyBuilder();
        for (final AssessedResponse assessedResponse : input.annotatedResponses()) {
          if (assessedResponse.response().isTemporal() && FieldAssessment
              .isAcceptable(assessedResponse.assessment().entityCorrectFiller())) {
            final String cas = assessedResponse.response().canonicalArgument().string();
            try {
              KBPTIMEXExpression.parseTIMEX(cas);
            } catch (IllegalArgumentException iae) {
              log.info(
                  "Response with illegal temporal expression {} had correct CAS assessment. Changing it to incorrect.",
                  cas);
              ret.replaceAssessment(assessedResponse.response(),
                  assessedResponse.assessment().copyWithModifiedCASAssessment(
                      FieldAssessment.INCORRECT));
            }
          }
        }
        return ret.build();
      }
    };
  }
}
