package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.kbp.events2014.AnswerKey;

import com.google.common.collect.Multimap;

/**
 * Created by jdeyoung on 6/4/15.
 */
public interface WarningRule<T> {

  Multimap<T, Warning> applyWarning(AnswerKey answerKey);

  String getTypeString();

  String getTypeDescription();
}
