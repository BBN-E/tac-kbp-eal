package com.bbn.kbp.events;

import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.nlp.corpora.ere.EREDocument;

import java.io.IOException;

/**
 * A way of fetching {@link EREDocument}s.
 */
@MoveToBUECommon
public interface EREDocumentSource {
  EREDocument ereDocumentForDocId(Symbol docID) throws IOException;
}
