package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.google.common.collect.ImmutableSet;

/**
 * Created by jdeyoung on 6/4/15.
 */
public final class ConjunctionWarningRule extends ConstainsStringWarningRule {

  private final static ImmutableSet<String> conjunctions = ImmutableSet.of("and", "or");

  protected ConjunctionWarningRule() {
    super(conjunctions);
  }

  public static ConjunctionWarningRule create() {
    return new ConjunctionWarningRule();
  }
}
