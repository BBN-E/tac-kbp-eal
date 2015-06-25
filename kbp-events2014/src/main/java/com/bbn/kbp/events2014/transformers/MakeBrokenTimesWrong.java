package com.bbn.kbp.events2014.transformers;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.KBPTIMEXExpression;
import com.bbn.kbp.events2014.ScoringData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

public final class MakeBrokenTimesWrong implements ScoringDataTransformation {

  private static final Logger log = LoggerFactory.getLogger(MakeBrokenTimesWrong.class);

  private MakeBrokenTimesWrong() {
    throw new UnsupportedOperationException();
  }

  public static MakeBrokenTimesWrong create() {
    return new MakeBrokenTimesWrong();
  }

  private static AnswerKey makeBrokenTimesWrong(AnswerKey input) {
    final AnswerKey.Builder ret = input.modifiedCopyBuilder();
    for (final AssessedResponse assessedResponse : input.annotatedResponses()) {
      if (assessedResponse.response().isTemporal() && FieldAssessment
          .isAcceptable(assessedResponse.assessment().entityCorrectFiller())) {
        final String cas = assessedResponse.response().canonicalArgument().string();
        try {
          KBPTIMEXExpression.parseTIMEX(cas);
        } catch (KBPTIMEXExpression.KBPTIMEXException te) {
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

  @Override
  public ScoringData transform(final ScoringData scoringData) {
    checkArgument(scoringData.answerKey().isPresent(), "It only makes sense to alter assessment if you have an answer key");
    return scoringData.modifiedCopy()
        .withAnswerKey(makeBrokenTimesWrong(scoringData.answerKey().get()))
        .build();
  }

  @Override
  public void logStats() {

  }
}
