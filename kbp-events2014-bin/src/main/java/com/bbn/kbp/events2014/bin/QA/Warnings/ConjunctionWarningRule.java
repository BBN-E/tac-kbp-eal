package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.google.common.collect.ImmutableSet;

/**
 * Created by jdeyoung on 6/4/15.
 */
public final class ConjunctionWarningRule extends ContainsStringWarningRule {

  private final static ImmutableSet<String> conjunctions = ImmutableSet.of("and", "or", "nor");

  protected ConjunctionWarningRule() {
    super(conjunctions);
  }

  public static ConjunctionWarningRule create() {
    return new ConjunctionWarningRule();
  }

  @Override
  public String getTypeString() {
    return "<b>Conjunction</b>";
  }

  @Override
  public String getTypeDescription() {
    return "The system has detected a conjunction in the CAS. "
        + "This CAS should probably be marked as WRONG.  "
        + "Its components would be marked as CORRECT.";
  }
}
