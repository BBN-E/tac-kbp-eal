package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.diff.AnswerSource;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.Response;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class SystemOutputEquivalenceClasses<Answerable> implements AnswerSource<Answerable, Response> {

  private final ArgumentOutput argumentOutput;
  private final Multimap<Answerable, Response> equivalenceClasses;

  private SystemOutputEquivalenceClasses(final ArgumentOutput argumentOutput,
      final Multimap<Answerable, Response> equivalenceClasses) {
    this.argumentOutput = checkNotNull(argumentOutput);
    this.equivalenceClasses = ImmutableMultimap.copyOf(equivalenceClasses);
  }

  public static <Answerable> SystemOutputEquivalenceClasses<Answerable> forAnswerable(
      final ArgumentOutput argumentOutput, final Function<Response, Answerable> answerableExtractor) {
    return new SystemOutputEquivalenceClasses<Answerable>(argumentOutput,
        Multimaps.index(argumentOutput.responses(), answerableExtractor));
  }

  @Override
  public Set<Answerable> answerables() {
    return equivalenceClasses.keySet();
  }

  @Override
  public Set<Response> answers(final Answerable answerable) {
    return ImmutableSet.copyOf(equivalenceClasses.get(answerable));
  }

  public ArgumentOutput systemOutput() {
    return argumentOutput;
  }
}
