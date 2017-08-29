package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.SystemOutputStore;

import java.io.File;
import java.io.IOException;


public interface SystemOutputLayout {

  DocumentSystemOutput emptyOutput(Symbol docID);

  SystemOutputStore open(File path) throws IOException;

  SystemOutputStore openOrCreate(File path) throws IOException;

  // this is necessary because this was orignally an enum and we'd like
  // to provide backwards compatability with the params.getEnum() calls
  // as much as possible
  final class ParamParser {

    public static SystemOutputLayout fromParamVal(String s) {
      if (s.equals("KBP_EAL_2016")) {
        return KBPEA2016OutputLayout.get();
      } else if (s.equals("KBP_EAL_2015") || s.equals("KBP_EA_2015")) {
        return KBPEA2015OutputLayout.get();
      } else if (s.equals("KBP_EAL_2014")) {
        return KBPEA2014OutputLayout.get();
      } else {
        throw new TACKBPEALException("Unknown system output layout " + s);
      }
    }
  }
}


