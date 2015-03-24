package com.bbn.kbp.events2014.scorer;

import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;

import com.google.common.base.Optional;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;

public final class ByJustificationLocation<Answerable extends Comparable<Answerable>>
    extends Ordering<Answerable> {

  private final AnswerKeyEquivalenceClasses<Answerable> answerKeySource;
  private final SystemOutputEquivalenceClasses<Answerable> systemSource;

  public static <Answerable extends Comparable<Answerable>> ByJustificationLocation<Answerable> create(
      final AnswerKeyEquivalenceClasses<Answerable> answerKeySource,
      final SystemOutputEquivalenceClasses<Answerable> systemSource) {
    return new ByJustificationLocation<Answerable>(answerKeySource, systemSource);
  }

  @Override
  public int compare(final Answerable left, final Answerable right) {
    final Optional<Response> earliestLeftResponse = earliestResponse(left);
    final Optional<Response> earliestRightResponse = earliestResponse(right);

    if (earliestLeftResponse.isPresent()) {
      if (earliestRightResponse.isPresent()) {
        return ComparisonChain.start()
            .compare(earliestLeftResponse.get(), earliestRightResponse.get(),
                Response.ByJustificationLocation)
            .compare(left, right)
            .result();
      } else {
        return -1;
      }
    } else if (earliestRightResponse.isPresent()) {
      return 1;
    } else {
      return left.compareTo(right);
    }
  }

  private Optional<Response> earliestResponse(final Answerable answerable) {
    final List<Response> candidates = ImmutableList.copyOf(concat(
        transform(answerKeySource.answers(answerable), AssessedResponse.Response),
        systemSource.answers(answerable)));
    if (candidates.isEmpty()) {
      return Optional.absent();
    } else {
      return Optional.of(Response.ByJustificationLocation.min(candidates));
    }
  }

  private ByJustificationLocation(final AnswerKeyEquivalenceClasses<Answerable> answerKeySource,
      final SystemOutputEquivalenceClasses<Answerable> systemSource) {
    checkArgument(answerKeySource.answerKey().docId() == systemSource.systemOutput().docId());
    this.answerKeySource = checkNotNull(answerKeySource);
    this.systemSource = checkNotNull(systemSource);
  }
}
