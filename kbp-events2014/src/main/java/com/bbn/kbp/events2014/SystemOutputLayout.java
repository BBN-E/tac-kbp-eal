package com.bbn.kbp.events2014;

import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.io.SystemOutputStore2014;
import com.bbn.kbp.events2014.io.SystemOutputStore2015;

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
  }, KBP_EA_2015 {
    @Override
    public SystemOutputStore open(final File path) throws IOException {
      return SystemOutputStore2015.open(path);
    }

    @Override
    public SystemOutputStore openOrCreate(final File path) throws IOException {
      return SystemOutputStore2015.openOrCreate(path);
    }
  };

  public abstract SystemOutputStore open(File path) throws IOException;

  public abstract SystemOutputStore openOrCreate(File path) throws IOException;
}
