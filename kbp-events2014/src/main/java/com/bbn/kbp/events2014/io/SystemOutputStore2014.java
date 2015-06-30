package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.SystemOutput2014;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SystemOutputStore2014 implements SystemOutputStore {
  private final ArgumentStore argumentStore;

  SystemOutputStore2014(final ArgumentStore argumentStore) {
    this.argumentStore = checkNotNull(argumentStore);
  }

  @Override
  public Set<Symbol> docIDs() throws IOException {
    return argumentStore.docIDs();
  }

  @Override
  public SystemOutput2014 read(final Symbol docID) throws IOException {
    return SystemOutput2014.from(argumentStore.read(docID));
  }

  @Override
  public void write(final SystemOutput output) throws IOException {
    if (output instanceof SystemOutput2014) {
      argumentStore.write(output.arguments());
    } else {
      throw new RuntimeException("Can only write 2014-format system outputs");
    }
  }

  public static SystemOutputStore2014 open(File dir) throws IOException {
    final File argumentsDir = new File(dir, "arguments");
    final ArgumentStore argStore = AssessmentSpecFormats.openSystemOutputStore(argumentsDir,
        AssessmentSpecFormats.Format.KBP2014);
    return new SystemOutputStore2014(argStore);
  }

  public static SystemOutputStore2014 openOrCreate(File dir) throws IOException {
    final File argumentsDir = new File(dir, "arguments");
    final ArgumentStore argStore = AssessmentSpecFormats.openOrCreateSystemOutputStore(argumentsDir,
        AssessmentSpecFormats.Format.KBP2014);
    return new SystemOutputStore2014(argStore);
  }

  @Override
  public void close() throws IOException {
    argumentStore.close();
  }

  public String toString() {
    return "SystemOutputStore2014(" + argumentStore + ")";
  }
}
