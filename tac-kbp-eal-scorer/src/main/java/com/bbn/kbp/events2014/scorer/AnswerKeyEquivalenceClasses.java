package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.diff.AnswerSource;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import java.util.Set;

import static com.bbn.kbp.events2014.AssessedResponseFunctions.response;
import static com.google.common.base.Functions.compose;
import static com.google.common.base.Preconditions.checkNotNull;

public class AnswerKeyEquivalenceClasses<Answerable>
    implements AnswerSource<Answerable, AssessedResponse> {

  private final AnswerKey answerKey;
  private final ImmutableMultimap<Answerable, AssessedResponse> equivalenceClasses;

  private AnswerKeyEquivalenceClasses(final AnswerKey answerKey,
      final Multimap<Answerable, AssessedResponse> equivalenceClasses) {
    this.answerKey = checkNotNull(answerKey);
    this.equivalenceClasses = ImmutableMultimap.copyOf(equivalenceClasses);
  }

  public static <Answerable> AnswerKeyEquivalenceClasses<Answerable> forAnswerable(
      final AnswerKey answerKey, final Function<Response, Answerable> answerableExtractor) {
    return new AnswerKeyEquivalenceClasses<Answerable>(answerKey,
        Multimaps.index(answerKey.annotatedResponses(),
            compose(answerableExtractor, response())));
  }

  @Override
  public Set<Answerable> answerables() {
    return equivalenceClasses.keySet();
  }

  @Override
  public Set<AssessedResponse> answers(final Answerable answerable) {
    return ImmutableSet.copyOf(equivalenceClasses.get(answerable));
  }

  public AnswerKey answerKey() {
    return answerKey;
  }

  public ImmutableMultimap<Answerable, AssessedResponse> asMultimap() {
    return equivalenceClasses;
  }
}
