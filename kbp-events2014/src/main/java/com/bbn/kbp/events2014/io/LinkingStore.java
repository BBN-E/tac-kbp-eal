package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;

public interface LinkingStore {
    public ImmutableSet<Symbol> docIDs() throws IOException;
    public Optional<ResponseLinking> read(SystemOutput systemOutput) throws IOException;
    public Optional<ResponseLinking> read(AnswerKey answerKey) throws IOException;
    public void write(ResponseLinking toWrite) throws IOException;
    public void close() throws IOException;
}
