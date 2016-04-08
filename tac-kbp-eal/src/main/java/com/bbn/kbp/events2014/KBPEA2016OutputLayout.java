package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;

import java.io.File;
import java.io.IOException;

public final class KBPEA2016OutputLayout implements SystemOutputLayout {

  private final KBPEA2015OutputLayout layout2015 = KBPEA2015OutputLayout.get();

  private static final KBPEA2016OutputLayout INSTANCE = new KBPEA2016OutputLayout();

  public static KBPEA2016OutputLayout get() {
    return INSTANCE;
  }

  @Override
  public DocumentSystemOutput emptyOutput(final Symbol docID) {
    return layout2015.emptyOutput(docID);
  }

  @Override
  public SystemOutputStore2016 open(final File path) throws IOException {
    return SystemOutputStore2016.open(path);
  }

  @Override
  public SystemOutputStore2016 openOrCreate(final File path) throws IOException {
    return SystemOutputStore2016.openOrCreate(path);
  }
}
