package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.transformers.DeleteInjureForCorrectDie;
import com.bbn.kbp.events2014.transformers.FixLowercaseXInTemporals;
import com.bbn.kbp.events2014.transformers.MakeAllRealisActual;
import com.bbn.kbp.events2014.transformers.MakeBrokenTimesWrong;
import com.bbn.kbp.events2014.transformers.OnlyMostSpecificTemporal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by jdeyoung on 3/23/15.
 */
public final class PreprocessorKBP2014 implements Preprocessor {

  private static final Logger log = LoggerFactory.getLogger(PreprocessorKBP2014.class);

  private final ImmutableList<Function<AnswerKey, AnswerKey>> answerKeyTransformations;
  private final ImmutableList<Function<SystemOutput, SystemOutput>> systemOutputTransformations;

  private PreprocessorKBP2014(
      final Iterable<Function<AnswerKey, AnswerKey>> answerKeyTransformations,
      final Iterable<Function<SystemOutput, SystemOutput>> systemOutputTransformations) {
    this.answerKeyTransformations = ImmutableList.copyOf(answerKeyTransformations);
    this.systemOutputTransformations = ImmutableList.copyOf(systemOutputTransformations);
  }

  public static Preprocessor fromParameters(Parameters params) {
    final List<Function<AnswerKey, AnswerKey>> answerKeyTransformations = Lists.newArrayList();
    final List<Function<SystemOutput, SystemOutput>> systemOutputTransformations = Lists.newArrayList();

    if (params.getBoolean("neutralizeRealis")) {
      answerKeyTransformations.add(MakeAllRealisActual.forAssessments());
      systemOutputTransformations.add(MakeAllRealisActual.forSystemOutput());
    }

    // in the original TAC KBP 2014 evaluation the human submission from LDC had temporal values
    // used lowercase "x" instead of "X". These were not caught by the validator and became
    // recall errors for all other systems.  This transformation fixes this.  Note that
    // adding these changes means the scores from the evaluation can no longer be replicated with the current code,
    // but they were slightly wrong anyway.  You can get the older behavior in release 2.2.0.

    answerKeyTransformations.add(FixLowercaseXInTemporals.forAnswerKey());
    systemOutputTransformations.add(FixLowercaseXInTemporals.forSystemOutput());

    // in the original TAC KBP 2014 evaluation many invalid temporal responses were erroneously
    // assessed as correct. This fixes that. To replicate the evaluation behavior, use release 2.2.0
    // or older
    answerKeyTransformations.add(MakeBrokenTimesWrong.forAnswerKey());

    final Preprocessor basePreprocessor =
        new PreprocessorKBP2014(answerKeyTransformations, systemOutputTransformations);
    if (params.getBoolean("attemptToNeutralizeCoref")) {
      log.info("Attempting to neutralize coref");
      return CorefNeutralizingPreprocessor.createWrappingPreprocessor(basePreprocessor);
    } else {
      return basePreprocessor;
    }
  }

  public static Preprocessor createKeepingRealis() {
    return new PreprocessorKBP2014(ImmutableList.<Function<AnswerKey, AnswerKey>>of(),
        ImmutableList.<Function<SystemOutput, SystemOutput>>of());
  }

  @Override
  public Result preprocess(SystemOutput systemOutput, AnswerKey answerKey)  {
    for (Function<AnswerKey, AnswerKey> answerKeyTransformation : answerKeyTransformations) {
      answerKey = answerKeyTransformation.apply(answerKey);
    }

    // Annotators will mark coreference between the entity fillers for arguments.  We load
    // these annotations in order to group e.g. (BasketballGame, Winner, Louisville, Actual)
    // and (BasketballGame, Winner, The Cards, Actual) if the annotator had coreffed
    // "Louisville" and "The Cards"
    final Function<KBPString, KBPString> entityNormalizer =
        answerKey.corefAnnotation().strictCASNormalizerFunction();

    // hard-coding the following two in is an ugly hack. Eventually they should get refactored
    // into a collection of filters built from each answer answerKey.

    // if a correct temporal role is present in the answer answerKey, then
    // all less specific versions of that temporal role are removed
    // from all system output before scoring
    final OnlyMostSpecificTemporal temporalFilter = OnlyMostSpecificTemporal.forAnswerKey(answerKey);

    // remove injure events which match die events (see "Departures from ACE 2005" in
    // the evaluation plan)
    final DeleteInjureForCorrectDie injureFilter =
        DeleteInjureForCorrectDie.fromEntityNormalizer(entityNormalizer);
    final Function<SystemOutput, SystemOutput> injureSystemOutputFilter =
        injureFilter.systemOutputTransformerForAnswerKey(answerKey);
    answerKey = injureFilter.answerKeyTransformer().apply(answerKey);

    for (final Function<SystemOutput, SystemOutput> systemOutputTransformation : systemOutputTransformations) {
      systemOutput = systemOutputTransformation.apply(systemOutput);
    }
    systemOutput = temporalFilter.apply(systemOutput);
    systemOutput = injureSystemOutputFilter.apply(systemOutput);

    Preprocessor.Result result = new Preprocessor.Result(systemOutput, answerKey);
    return result;
  }

  public void logStats() {

  }

}

class AbstractPreprocessor implements Preprocessor {


  @Override
  public Result preprocess(final SystemOutput systemOutput,
      final AnswerKey answerKey) {
    return null;
  }

  @Override
  public void logStats() {

  }
}
