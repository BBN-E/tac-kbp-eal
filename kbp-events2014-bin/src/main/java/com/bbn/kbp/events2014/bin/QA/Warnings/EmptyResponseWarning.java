package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;

/**
 * Created by jdeyoung on 6/4/15.
 */
public final class EmptyResponseWarning implements WarningRule {

  private EmptyResponseWarning() {

  }

  @Override
  public SetMultimap<Response, Warning> applyWarning(final AnswerKey answerKey) {
    final ImmutableSetMultimap.Builder<Response, Warning> result = ImmutableSetMultimap.builder();
    for(final Response r: Iterables.transform(answerKey.annotatedResponses(), AssessedResponse.Response)) {
      final String trimmed = r.canonicalArgument().string().trim();
      final String noPunct = trimmed.replaceAll("[^A-Za-z0-9]", "");
      if(noPunct.trim().isEmpty()) {
        result.put(r, new Warning(getTypeString(), String.format("%s appears to be only spaces and punctuation", trimmed),
            Warning.Severity.MINOR));
      }
    }
    return result.build();
  }

  @Override
  public String getTypeString() {
    return "<b>Empty Response</b>";
  }

  @Override
  public String getTypeDescription() {
    return "The response has no content (no characters or all punctuation),"
        + " it's probably an error";
  }

  public static EmptyResponseWarning create() {
    return new EmptyResponseWarning();
  }
}
