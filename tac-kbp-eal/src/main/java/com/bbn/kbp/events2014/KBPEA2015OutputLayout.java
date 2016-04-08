package com.bbn.kbp.events2014;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.io.SystemOutputStore2015;

import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;

public final class KBPEA2015OutputLayout implements SystemOutputLayout {

  private static final KBPEA2015OutputLayout INSTANCE = new KBPEA2015OutputLayout();

  public static KBPEA2015OutputLayout get() {
    return INSTANCE;
  }

  @Override
  public SystemOutputStore open(final File path) throws IOException {
    return SystemOutputStore2015.open(path);
  }

  @Override
  public SystemOutputStore openOrCreate(final File path) throws IOException {
    return SystemOutputStore2015.openOrCreate(path);
  }

  @Override
  public DocumentSystemOutput emptyOutput(final Symbol docID) {
    return DocumentSystemOutput2015
        .from(ArgumentOutput.from(docID, ImmutableSet.<Scored<Response>>of()),
            ResponseLinking.builder().docID(docID).build());
  }
}
