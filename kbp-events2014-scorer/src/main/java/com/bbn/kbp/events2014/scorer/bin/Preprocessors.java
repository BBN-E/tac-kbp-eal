package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.kbp.events2014.transformers.DeleteInjureForCorrectDie;
import com.bbn.kbp.events2014.transformers.FixLowercaseXInTemporals;
import com.bbn.kbp.events2014.transformers.MakeAllRealisActual;
import com.bbn.kbp.events2014.transformers.MakeBrokenTimesWrong;
import com.bbn.kbp.events2014.transformers.OnlyMostSpecificTemporal;
import com.bbn.kbp.events2014.transformers.ScoringDataTransformation;
import com.bbn.kbp.events2014.transformers.ScoringDataTransformationSequence;

import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by jdeyoung on 3/23/15.
 */
public final class Preprocessors {
  private static final Logger log = LoggerFactory.getLogger(Preprocessors.class);

  private Preprocessors() {
  }

  public static ScoringDataTransformation for2014FromParameters(Parameters params) {
    final List<ScoringDataTransformation> transformationsInOrder = Lists.newArrayList();

    if (params.getBoolean("neutralizeRealis")) {
      transformationsInOrder.add(MakeAllRealisActual.create());
    }

    // in the original TAC KBP 2014 evaluation the human submission from LDC had temporal values
    // used lowercase "x" instead of "X". These were not caught by the validator and became
    // recall errors for all other systems.  This transformation fixes this.  Note that
    // adding these changes means the scores from the evaluation can no longer be replicated with the current code,
    // but they were slightly wrong anyway.  You can get the older behavior in release 2.2.0.
    transformationsInOrder.add(FixLowercaseXInTemporals.asTransformationForBoth());

    // in the original TAC KBP 2014 evaluation many invalid temporal responses were erroneously
    // assessed as correct. This fixes that. To replicate the evaluation behavior, use release 2.2.0
    // or older
    transformationsInOrder.add(MakeBrokenTimesWrong.create());

    transformationsInOrder.add(DeleteInjureForCorrectDie.asTransformationForBoth());
    transformationsInOrder.add(OnlyMostSpecificTemporal.asTransformationForBoth());

    if (params.getBoolean("attemptToNeutralizeCoref")) {
      log.info("Attempting to neutralize coref");
      transformationsInOrder.add(CorefNeutralizingPreprocessor.create());
    }
    return ScoringDataTransformationSequence.compose(transformationsInOrder);
  }

  public static ScoringDataTransformation for2015FromParameters(Parameters params) {
    final List<ScoringDataTransformation> transformationsInOrder = Lists.newArrayList();

    if (params.getBoolean("neutralizeRealis")) {
      transformationsInOrder.add(MakeAllRealisActual.create());
    }

    // in 2015, we no longe rhave mercy on you if you have a bad time format. This should now
    // be caught in the validator

    // in the original TAC KBP 2014 evaluation many invalid temporal responses were erroneously
    // assessed as correct. This is just here in case the LDC misses marking any wrong.
    transformationsInOrder.add(MakeBrokenTimesWrong.create());

    transformationsInOrder.add(DeleteInjureForCorrectDie.asTransformationForBoth());
    transformationsInOrder.add(OnlyMostSpecificTemporal.asTransformationForBoth());

    if (params.getBoolean("attemptToNeutralizeCoref")) {
      log.info("Attempting to neutralize coref");
      transformationsInOrder.add(CorefNeutralizingPreprocessor.create());
    }
    return ScoringDataTransformationSequence.compose(transformationsInOrder);
  }
}

