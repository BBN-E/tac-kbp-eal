package com.bbn.kbp.events2014;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.io.SystemOutputStore2014;
import com.bbn.kbp.events2014.io.SystemOutputStore2015;

import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;

public final class KBPEA2014OutputLayout implements SystemOutputLayout {

  private static final KBPEA2014OutputLayout INSTANCE = new KBPEA2014OutputLayout();

  public static KBPEA2014OutputLayout get() {
    return INSTANCE;
  }

  @Override
  public SystemOutputStore open(final File path) throws IOException {
    return SystemOutputStore2014.open(path);
  }

  @Override
  public SystemOutputStore openOrCreate(final File path) throws IOException {
    return SystemOutputStore2015.openOrCreate(path);
  }

  @Override
  public DocumentSystemOutput emptyOutput(final Symbol docID) {
    return DocumentSystemOutput2014
        .from(ArgumentOutput.from(docID, ImmutableSet.<Scored<Response>>of()));
  }
}
