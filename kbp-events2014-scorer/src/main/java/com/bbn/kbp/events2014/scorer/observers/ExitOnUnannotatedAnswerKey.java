package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;

import com.google.common.collect.Iterables;

import java.io.File;
import java.io.IOException;

/**
 * Does nothing except throw an exception if it encounters an unannotated answer key.
 */
public final class ExitOnUnannotatedAnswerKey extends KBPScoringObserver<TypeRoleFillerRealis> {

  private ExitOnUnannotatedAnswerKey() {
  }

  @Override
  public void observeDocument(final EventArgScoringAlignment<TypeRoleFillerRealis> scoringAlignment,
      final File perDocLogDir) throws IOException {
    if (!scoringAlignment.unassessed().isEmpty()) {
      TypeRoleFillerRealis foo;

      throw new RuntimeException(String.format(
          "All answer keys required to be completely annotated, but these %s are not",
          Iterables.transform(scoringAlignment.unassessed(), TypeRoleFillerRealis.DocID)));
    }
  }

  public static ExitOnUnannotatedAnswerKey create() {
    return new ExitOnUnannotatedAnswerKey();
  }

}
