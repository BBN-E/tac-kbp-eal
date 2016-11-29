package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.io.SystemOutputStore2017;

import java.io.File;
import java.io.IOException;

/**
 * Created by jdeyoung on 11/29/16.
 */
public final class KBPEA2017OutputLayout implements SystemOutputLayout {

  private final KBPEA2015OutputLayout layout2015 = KBPEA2015OutputLayout.get();
  private static final KBPEA2017OutputLayout INSTANCE = new KBPEA2017OutputLayout();


  private KBPEA2017OutputLayout() {
  }

  public static KBPEA2017OutputLayout get() {
    return INSTANCE;
  }

  @Override
  public DocumentSystemOutput emptyOutput(final Symbol docID) {
    return layout2015.emptyOutput(docID);
  }

  @Override
  public SystemOutputStore open(final File path) throws IOException {
    return SystemOutputStore2017.open(path);
  }

  @Override
  public SystemOutputStore openOrCreate(final File path) throws IOException {
    return SystemOutputStore2017.openOrCreate(path);
  }
}
