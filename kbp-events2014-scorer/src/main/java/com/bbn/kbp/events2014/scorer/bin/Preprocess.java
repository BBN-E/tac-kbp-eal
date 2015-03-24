package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.Set;

/**
 * Created by jdeyoung on 3/23/15.
 * Interface to define preprocessing steps for KBP
 */
public interface Preprocess {

  public abstract Preprocess.Result create(final SystemOutputStore systemAnswerStore,
      final AnnotationStore goldAnswerStore,
      final Set<Symbol> documentsToScore,
      final ImmutableList<Function<AnswerKey, AnswerKey>> answerKeyTransformations,
      final ImmutableList<Function<SystemOutput, SystemOutput>> systemOutputTransformations,
      Symbol docid) throws IOException;

  public class Result {

    private final AnswerKey answerKey;
    private final SystemOutputAnswerSource<TypeRoleFillerRealis> systemOutput;
    private final ImmutableList<Function<AnswerKey, AnswerKey>> answerKeyTransformations;
    private final ImmutableList<Function<SystemOutput, SystemOutput>> systemOutputTransformations;
    private Function<KBPString, KBPString> normalizer;

    protected Result(AnswerKey answerKey, SystemOutputAnswerSource<TypeRoleFillerRealis> systemOutput,
        ImmutableList<Function<AnswerKey, AnswerKey>> answerKeyTransformations,
        ImmutableList<Function<SystemOutput, SystemOutput>> systemOutputTransformations,
        Symbol docid) {
      this.answerKey = answerKey;
      this.systemOutput = systemOutput;
      this.answerKeyTransformations = answerKeyTransformations;
      this.systemOutputTransformations = systemOutputTransformations;
    }

    public AnswerKey getAnswerKey() {
      return answerKey;
    }

    public SystemOutputAnswerSource<TypeRoleFillerRealis> getSystemOutput() {
      return systemOutput;
    }

    public Function<KBPString, KBPString> normalizer() {
      return normalizer;
    }

    public ImmutableList<Function<AnswerKey, AnswerKey>> answerKeyTransformations() {
      return answerKeyTransformations;
    }

    public ImmutableList<Function<SystemOutput, SystemOutput>> systemOutputTransformations() {
      return systemOutputTransformations;
    }

  }

}
