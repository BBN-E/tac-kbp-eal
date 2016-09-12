package com.bbn.kbp.events2014.linking;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.io.LinkingStore;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.Set;

public abstract class AbstractLinkingStrategy implements LinkingStrategy {
  @Override
  public LinkingStore wrap(final Iterable<Symbol> docIDs) {
    final ImmutableSet<Symbol> docIdSet = ImmutableSet.copyOf(docIDs);

    return new LinkingStore() {
      @Override
      public ImmutableSet<Symbol> docIDs() throws IOException {
        return docIdSet;
      }

      @Override
      public Optional<ResponseLinking> read(final ArgumentOutput argumentOutput) throws IOException {
        if (docIdSet.contains(argumentOutput.docId())) {
          return Optional.of(linkResponses(argumentOutput));
        } else {
          return Optional.absent();
        }
      }

      @Override
      public Optional<ResponseLinking> read(final AnswerKey answerKey) throws IOException {

        if (docIdSet.contains(answerKey.docId())) {
          return Optional.of(linkResponses(answerKey));
        } else {
          return Optional.absent();
        }
      }

      @Override
      public void write(final ResponseLinking toWrite) throws IOException {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close() throws IOException {
        // do nothing, assume underlying argumentStore will be closed separately
      }

      @Override
      public Optional<ResponseLinking> readTransformingIDs(final Symbol docID,
          final Set<Response> responses,
          final Optional<ImmutableMap<String, String>> foreignResponseIDToLocal,
          final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIDToLocal)
          throws IOException {
        throw new UnsupportedOperationException();
      }
    };
  }
}
