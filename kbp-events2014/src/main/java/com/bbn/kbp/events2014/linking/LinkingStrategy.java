package com.bbn.kbp.events2014.linking;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.io.LinkingStore;

public interface LinkingStrategy {

  ResponseLinking linkResponses(final ArgumentOutput argumentOutput);

  ResponseLinking linkResponses(final AnswerKey answerKey);

  LinkingStore wrap(Iterable<Symbol> docIDs);
}

