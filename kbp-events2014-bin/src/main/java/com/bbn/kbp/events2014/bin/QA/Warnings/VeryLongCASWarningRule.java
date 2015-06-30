package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

/**
 * Created by jdeyoung on 6/15/15.
 */
public final class VeryLongCASWarningRule extends TRFRWarning {

  protected final int DEFAULT_TOO_BIG = 7;

  protected VeryLongCASWarningRule() {

  }

  @Override
  public SetMultimap<Response, Warning> applyWarning(final AnswerKey answerKey) {
    final Multimap<TypeRoleFillerRealis, Response> trfrToResponse = extractTRFRs(answerKey);
    final ImmutableSetMultimap.Builder<Response, Warning> warnings =
        ImmutableSetMultimap.builder();
    for (TypeRoleFillerRealis trfr : trfrToResponse.keySet()) {
      for (Response r : trfrToResponse.get(trfr)) {
        if (r.canonicalArgument().string().split("\\s+").length
            >= DEFAULT_TOO_BIG) {
          warnings.put(r, Warning.create(getTypeString(), String
              .format("contained more than %d tokens", DEFAULT_TOO_BIG - 1),
              Warning.Severity.MAJOR));
        }
      }
    }
    return warnings.build();
  }

  @Override
  public String getTypeString() {
    return "Very Long CAS";
  }

  @Override
  public String getTypeDescription() {
    return "This response is long enough that we believe it's wrong (and may cause additional other errors)";
  }

  protected static final Splitter WHITESPACE_SPLITTER =
      Splitter.on("\\s+").omitEmptyStrings().trimResults();

  public static VeryLongCASWarningRule create() {
    return new VeryLongCASWarningRule();
  }
}
