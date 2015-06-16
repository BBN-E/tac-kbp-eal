package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      if (warningApplies(r)) {
        warnings.put(r, Warning
            .create(getTypeString(), String.format("contains one of %s", verboten.toString()),
                Warning.Severity.MINOR));
        log.info("adding {} by contains string", r.canonicalArgument().string());
      }
    }
    return warnings.build();
  }

  protected static final Splitter WHITESPACE_SPLITTER =
      Splitter.on("\\s+").omitEmptyStrings().trimResults();

  protected boolean warningApplies(final Response input) {
    // replace non ascii characters before applying warning
    final ImmutableSet<String> words = ImmutableSet
        .copyOf(WHITESPACE_SPLITTER
            .split(input.canonicalArgument().string().toLowerCase().replaceAll("[^A-Za-z0-9]", "")));
    final Sets.SetView<String> forbiddenWords = Sets.intersection(words, verboten);
    return !forbiddenWords.isEmpty();
  }
}
