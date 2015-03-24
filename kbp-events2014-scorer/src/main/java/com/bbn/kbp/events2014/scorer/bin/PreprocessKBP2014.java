package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import com.bbn.kbp.events2014.transformers.DeleteInjureForCorrectDie;
import com.bbn.kbp.events2014.transformers.OnlyMostSpecificTemporal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.Set;

/**
 * Created by jdeyoung on 3/23/15.
 */
public final class PreprocessKBP2014 implements Preprocess {

  public PreprocessKBP2014() {
  }

  @Override
  public Result create(final SystemOutputStore systemAnswerStore,
      final AnnotationStore goldAnswerStore, final Set<Symbol> documentsToScore,
      final ImmutableList<Function<AnswerKey, AnswerKey>> answerKeyTransformations,
      final ImmutableList<Function<SystemOutput, SystemOutput>> systemOutputTransformations,
      Symbol docid) throws IOException {
    AnswerKey key = goldAnswerStore.readOrEmpty(docid);

    for (Function<AnswerKey, AnswerKey> answerKeyTransformation : answerKeyTransformations) {
      key = answerKeyTransformation.apply(key);
    }

    // Annotators will mark coreference between the entity fillers for arguments.  We load
    // these annotations in order to group e.g. (BasketballGame, Winner, Louisville, Actual)
    // and (BasketballGame, Winner, The Cards, Actual) if the annotator had coreffed
    // "Louisville" and "The Cards"
    final Function<KBPString, KBPString> entityNormalizer =
        key.corefAnnotation().strictCASNormalizerFunction();

    // hard-coding the following two in is an ugly hack. Eventually they should get refactored
    // into a collection of filters built from each answer key.

    // if a correct temporal role is present in the answer key, then
    // all less specific versions of that temporal role are removed
    // from all system output before scoring
    final OnlyMostSpecificTemporal temporalFilter = OnlyMostSpecificTemporal.forAnswerKey(key);

    // remove injure events which match die events (see "Departures from ACE 2005" in
    // the evaluation plan)
    final DeleteInjureForCorrectDie injureFilter =
        DeleteInjureForCorrectDie.fromEntityNormalizer(entityNormalizer);
    final Function<SystemOutput, SystemOutput> injureSystemOutputFilter =
        injureFilter.systemOutputTransformerForAnswerKey(key);
    key = injureFilter.answerKeyTransformer().apply(key);

    SystemOutput rawSystemOutput = systemAnswerStore.readOrEmpty(docid);
    for (final Function<SystemOutput, SystemOutput> systemOutputTransformation : systemOutputTransformations) {
      rawSystemOutput = systemOutputTransformation.apply(rawSystemOutput);
    }
    rawSystemOutput = temporalFilter.apply(rawSystemOutput);
    rawSystemOutput = injureSystemOutputFilter.apply(rawSystemOutput);

    final SystemOutputAnswerSource<TypeRoleFillerRealis> systemOutput =
        SystemOutputAnswerSource.forAnswerable(rawSystemOutput,
            TypeRoleFillerRealis.extractFromSystemResponse(entityNormalizer));
    final AnswerKeyAnswerSource<TypeRoleFillerRealis> answerKey =
        AnswerKeyAnswerSource.forAnswerable(
            key, TypeRoleFillerRealis.extractFromSystemResponse(entityNormalizer));
    Preprocess.Result result = new Preprocess.Result(answerKey, systemOutput, docid);
    return result;
  }
}
