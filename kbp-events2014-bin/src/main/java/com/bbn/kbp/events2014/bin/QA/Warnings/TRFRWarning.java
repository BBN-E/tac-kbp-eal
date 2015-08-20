package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;

import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

/**
 * Created by jdeyoung on 6/4/15.
 */
abstract class TRFRWarning implements WarningRule<Response> {

  protected Multimap<TypeRoleFillerRealis, Response> extractTRFRs(final AnswerKey answerKey) {
    return Multimaps.index(
        Iterables.transform(answerKey.annotatedResponses(), AssessedResponse.Response),
        TypeRoleFillerRealis
            .extractFromSystemResponse(answerKey.corefAnnotation().strictCASNormalizerFunction()));
  }
}
