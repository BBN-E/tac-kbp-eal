package com.bbn.kbp.events;

import com.bbn.kbp.events2014.TACKBPEALException;
import com.bbn.nlp.corpora.ere.EREArgument;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.EREEntity;
import com.bbn.nlp.corpora.ere.EREEntityArgument;
import com.bbn.nlp.corpora.ere.EREFillerArgument;

import com.google.common.base.Optional;

final class ScoringUtils {

  private ScoringUtils() {
    throw new UnsupportedOperationException();
  }

  static ScoringCorefID extractScoringEntity(EREArgument ea, EREDocument ereDoc) {
    if (ea instanceof EREEntityArgument) {
      final Optional<EREEntity> entityContainingMention = ereDoc.getEntityContaining(
          ((EREEntityArgument) ea).entityMention());
      if (entityContainingMention.isPresent()) {
        return ScoringCorefID.of(ScoringEntityType.Entity, entityContainingMention.get().getID());
      } else {
        throw new TACKBPEALException("ERE mention not in any entity");
      }
    } else if (ea instanceof EREFillerArgument) {
      return ScoringCorefID.of(ScoringEntityType.Filler, ea.getID());
    } else {
      throw new TACKBPEALException("Unknown ERE argument type " + ea.getClass());
    }
  }
}
