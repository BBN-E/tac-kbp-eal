package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ArgumentOutput;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.Set;

public interface LinkingStore {

  ImmutableSet<Symbol> docIDs() throws IOException;

  Optional<ResponseLinking> read(ArgumentOutput argumentOutput) throws IOException;

  Optional<ResponseLinking> read(AnswerKey answerKey) throws IOException;

  void write(ResponseLinking toWrite) throws IOException;

  void close() throws IOException;

  Optional<ResponseLinking> readTransformingIDs(
      Symbol docID, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignIDToLocal)
      throws IOException;
}
