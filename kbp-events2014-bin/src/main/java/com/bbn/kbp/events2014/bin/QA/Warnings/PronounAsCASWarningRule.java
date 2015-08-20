package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.kbp.events2014.Response;

import com.google.common.collect.ImmutableSet;

/**
 * Created by jdeyoung on 6/15/15.
 */
public final class PronounAsCASWarningRule extends ContainsStringWarningRule {

  private static final ImmutableSet<String> pronouns = ImmutableSet
      .of("he", "she", "it", "they", "his", "hers", "their", "theirs", "them", "who", "which",
          "that");

  private PronounAsCASWarningRule(final Iterable<String> verboten) {
    super(verboten);
  }

//  @Override
//  public SetMultimap<Response, Warning> applyWarning(final AnswerKey answerKey) {
//    final SetMultimap<Response, Warning> preliminaryResult = super.applyWarning(answerKey);
//    return null;
//  }

  @Override
  public String getTypeString() {
    return "<b>Prounoun as CAS</b>";
  }

  @Override
  public String getTypeDescription() {
    return "The system has detected a CAS that consists entirely of a pronoun. "
        + "If the pronoun can be resolved to a name or description "
        + "(either in the correct set of responses, or earlier in the document), "
        + "the CAS should be marked as WRONG";
  }

  public static PronounAsCASWarningRule create() {
    return new PronounAsCASWarningRule(pronouns);
  }

  @Override
  protected boolean warningApplies(final Response input) {
    if (super.warningApplies(input)) {
      if (WHITESPACE_SPLITTER.splitToList(input.canonicalArgument().string()).size() < 3) {
        return false;
      }
      return true;
    }
    return false;
  }
}
