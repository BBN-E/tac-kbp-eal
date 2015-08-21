package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;

import com.google.common.collect.SetMultimap;

import java.util.Set;

/**
 * Created by jdeyoung on 8/21/15.
 */
public interface CorefWarningRule<T> extends WarningRule<T> {

  SetMultimap<T, Warning> applyWarning(AnswerKey answerKey, Set<Response> restrictWarningTo);
}
