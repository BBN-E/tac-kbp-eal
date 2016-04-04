package com.bbn.kbp.events2014;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.io.SystemOutputStore2014;
import com.bbn.kbp.events2014.io.SystemOutputStore2015;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;

import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;

public enum SystemOutputLayout {
  KBP_EA_2014 {
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
  }, KBP_EA_2015 {
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
  },
  KBP_EA_2016 {
    @Override
    public DocumentSystemOutput emptyOutput(final Symbol docID) {
      return KBP_EA_2015.emptyOutput(docID);
    }

    @Override
    public SystemOutputStore2016 open(final File path) throws IOException {
      return SystemOutputStore2016.for2015Store(SystemOutputStore2015.open(path));
    }

    @Override
    public SystemOutputStore2016 openOrCreate(final File path) throws IOException {
      return SystemOutputStore2016.for2015Store(SystemOutputStore2015.openOrCreate(path));
    }
  };

  public abstract DocumentSystemOutput emptyOutput(Symbol docID);
  public abstract SystemOutputStore open(File path) throws IOException;
  public abstract SystemOutputStore openOrCreate(File path) throws IOException;
}
