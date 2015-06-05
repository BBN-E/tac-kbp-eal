package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Created by jdeyoung on 6/4/15.
 */
abstract class ContainsStringWarningRule implements WarningRule {

  private static final Logger log = LoggerFactory.getLogger(ContainsStringWarningRule.class);

  private final ImmutableSet<String> verboten;

  protected ContainsStringWarningRule(final Iterable<String> verboten) {
    this.verboten = ImmutableSet.copyOf(verboten);
  }

  public SetMultimap<Response, Warning> applyWarning(
      final AnswerKey answerKey) {
    final Iterable<Response> responses =
        Iterables.transform(answerKey.annotatedResponses(), AssessedResponse.Response);
    final ImmutableSetMultimap.Builder<Response, Warning> warnings =
        ImmutableSetMultimap.builder();
    for (Response r : responses) {
      if (apply(r)) {
        warnings.put(r, Warning
            .create(String.format("contains one of %s", verboten.toString()),
                Warning.Severity.MINOR));
        log.info("adding {} by contains string", r.canonicalArgument().string());
      }
    }
    return warnings.build();
  }

  protected boolean apply(final Response input) {
    Set<String> inputParts =
        Sets.newHashSet(input.canonicalArgument().string().trim().toLowerCase().split(
            "\\s+"));
    inputParts.retainAll(verboten);
    return inputParts.size() > 0;
  }
}
