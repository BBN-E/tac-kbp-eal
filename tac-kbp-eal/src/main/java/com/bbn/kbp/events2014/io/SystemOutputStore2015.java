package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.SystemOutput2015;

import com.google.common.base.Optional;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SystemOutputStore2015 implements SystemOutputStore {
  private final ArgumentStore argumentStore;
  private final LinkingStore linkingStore;

  private SystemOutputStore2015(final ArgumentStore argumentStore, final LinkingStore linkingStore) {
    this.argumentStore = checkNotNull(argumentStore);
    this.linkingStore = checkNotNull(linkingStore);
  }

  public static SystemOutputStore2015 open(File dir) throws IOException {
    final File argumentsDir = new File(dir, "arguments");
    final File linkingDir = new File(dir, "linking");
    final ArgumentStore argStore = AssessmentSpecFormats.openSystemOutputStore(argumentsDir,
        AssessmentSpecFormats.Format.KBP2015);
    final LinkingStore linkingStore = LinkingStoreSource.createFor2015().openLinkingStore(linkingDir);
    if (argStore.docIDs().equals(linkingStore.docIDs())) {
      return new SystemOutputStore2015(argStore, linkingStore);
    } else {
      throw new RuntimeException("Argument and linking store docIDs do not match");
    }
  }

  public static SystemOutputStore2015 openOrCreate(File dir) throws IOException {
    final File argumentsDir = new File(dir, "arguments");
    final File linkingDir = new File(dir, "linking");
    final ArgumentStore argStore = AssessmentSpecFormats.openOrCreateSystemOutputStore(argumentsDir,
        AssessmentSpecFormats.Format.KBP2015);
    final LinkingStore linkingStore = LinkingStoreSource.createFor2015().openOrCreateLinkingStore(linkingDir);
    if (argStore.docIDs().equals(linkingStore.docIDs())) {
      return new SystemOutputStore2015(argStore, linkingStore);
    } else {
      throw new RuntimeException("Argument and linking store docIDs do not match");
    }
  }

  @Override
  public Set<Symbol> docIDs() throws IOException {
    return argumentStore.docIDs();
  }

  @Override
  public SystemOutput2015 read(final Symbol docID) throws IOException {
    final ArgumentOutput arguments = argumentStore.read(docID);
    final Optional<ResponseLinking> linking = linkingStore.read(arguments);
    if (!linking.isPresent()) {
      throw new RuntimeException("Missing linking for " + docID);
    }
    return SystemOutput2015.from(arguments, linking.get());
  }

  @Override
  public void write(final SystemOutput output) throws IOException {
    if (output instanceof SystemOutput2015) {
      argumentStore.write(output.arguments());
      linkingStore.write(((SystemOutput2015) output).linking());
    } else {
      throw new RuntimeException("Can only write 2015-format system outputs");
    }
  }

  @Override
  public void close() throws IOException {
    argumentStore.close();
    linkingStore.close();
  }

  public String toString() {
    return "SystemOutputStore2015(" + argumentStore + ", " + linkingStore + ")";
  }
}
