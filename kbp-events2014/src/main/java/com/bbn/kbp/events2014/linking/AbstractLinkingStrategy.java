package com.bbn.kbp.events2014.linking;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.ArgumentStore;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;

public abstract class AbstractLinkingStrategy implements LinkingStrategy {
  @Override
  public LinkingStore wrap(final ArgumentStore argumentStore) {
    return new LinkingStore() {

      @Override
      public ImmutableSet<Symbol> docIDs() throws IOException {
        return argumentStore.docIDs();
      }

      @Override
      public Optional<ResponseLinking> read(final ArgumentOutput argumentOutput) throws IOException {
        return Optional.of(linkResponses(argumentOutput));
      }

      @Override
      public Optional<ResponseLinking> read(final AnswerKey answerKey) throws IOException {
        throw new UnsupportedOperationException();
      }

      @Override
      public void write(final ResponseLinking toWrite) throws IOException {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close() throws IOException {
        // do nothing, assume underlying argumentStore will be closed separately
      }
    };
  }
}
