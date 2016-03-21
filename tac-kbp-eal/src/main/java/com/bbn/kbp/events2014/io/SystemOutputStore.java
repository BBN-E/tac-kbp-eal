package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.SystemOutput2015;
import com.bbn.kbp.events2014.transformers.ResponseMapping;

import com.google.common.base.Optional;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public interface SystemOutputStore {
  Set<Symbol> docIDs() throws IOException;
  SystemOutput read(Symbol docID) throws IOException;
  void write(SystemOutput output) throws IOException;
  void close() throws IOException;
}

