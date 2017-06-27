package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.CorpusEventFrame;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocumentSystemOutput;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;
import com.bbn.kbp.events2014.ResponseLinking;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;


public final class SystemOutputStore2017 implements SystemOutputStore, CrossDocSystemOutputStore {

  private final Symbol systemID;
  private final ArgumentStore argumentStore;
  private final LinkingStore linkingStore;
  private final File corpusLinkingFile;

  private final CorpusEventFrameWriter eventFrameWriter = CorpusEventFrameIO.writerFor2016();
  private final CorpusEventFrameLoader eventFrameReader = CorpusEventFrameIO.loaderFor2016();

  private SystemOutputStore2017(final Symbol systemID, final ArgumentStore argumentStore,
      final LinkingStore linkingStore, final File corpusLinkingFile) {
    this.systemID = checkNotNull(systemID);
    this.argumentStore = checkNotNull(argumentStore);
    this.linkingStore = checkNotNull(linkingStore);
    this.corpusLinkingFile = checkNotNull(corpusLinkingFile);
  }


  public static SystemOutputStore2017 open(File dir) throws IOException {
    final Symbol systemID = Symbol.from(dir.getName());
    final File argumentsDir = new File(dir, "arguments");
    final File linkingDir = new File(dir, "linking");
    final File corpusLinkingFile = new File(new File(dir, "corpusLinking"), "corpusLinking");
    corpusLinkingFile.getParentFile().mkdirs();

    final ArgumentStore argStore = AssessmentSpecFormats.openSystemOutputStore(argumentsDir,
        AssessmentSpecFormats.Format.KBP2017);
    final LinkingStore linkingStore =
        LinkingStoreSource.createFor2016().openLinkingStore(linkingDir);
    if (argStore.docIDs().equals(linkingStore.docIDs())) {
      return new SystemOutputStore2017(systemID, argStore, linkingStore, corpusLinkingFile);
    } else {
      throw new RuntimeException("Argument and linking store docIDs do not match, missing " + Sets
          .symmetricDifference(argStore.docIDs(), linkingStore.docIDs()));
    }
  }

  public static SystemOutputStore2017 openOrCreate(final File dir) throws IOException {
    return openOrCreate(dir, AssessmentSpecFormats.Format.KBP2017);
  }

  static SystemOutputStore2017 openOrCreate(File dir,
      final AssessmentSpecFormats.Format format) throws IOException {
    final Symbol systemID = Symbol.from(dir.getName());
    final File argumentsDir = new File(dir, "arguments");
    final File linkingDir = new File(dir, "linking");
    final File corpusLinkingFile = new File(new File(dir, "corpusLinking"), "corpusLinking");
    corpusLinkingFile.getParentFile().mkdirs();

    final ArgumentStore argStore = AssessmentSpecFormats.openOrCreateSystemOutputStore(argumentsDir,
        format);
    final LinkingStore linkingStore =
        LinkingStoreSource.createFor2016().openOrCreateLinkingStore(linkingDir);
    if (argStore.docIDs().equals(linkingStore.docIDs())) {
      return new SystemOutputStore2017(systemID, argStore, linkingStore, corpusLinkingFile);
    } else {
      throw new RuntimeException("Argument and linking store docIDs do not match, missing " + Sets
          .symmetricDifference(argStore.docIDs(), linkingStore.docIDs()));
    }
  }

  @Override
  public CorpusEventLinking readCorpusEventFrames() throws IOException {
    if (corpusLinkingFile.isFile()) {
      return eventFrameReader.loadCorpusEventFrames(Files.asCharSource(corpusLinkingFile,
          Charsets.UTF_8));
    } else {
      return CorpusEventLinking.of(ImmutableSet.<CorpusEventFrame>of());
    }
  }

  @Override
  public void writeCorpusEventFrames(CorpusEventLinking corpusEventFrames) throws IOException {
    corpusLinkingFile.getParentFile().mkdirs();
    eventFrameWriter.writeCorpusEventFrames(corpusEventFrames, Files.asCharSink(corpusLinkingFile,
        Charsets.UTF_8));
  }

  @Override
  public final Symbol systemID() {
    return systemID;
  }

  @Override
  public final Set<Symbol> docIDs() throws IOException {
    return argumentStore.docIDs();
  }

  @Override
  public DocumentSystemOutput2015 read(final Symbol docID) throws IOException {
    final ArgumentOutput argOutput = argumentStore.read(docID);
    final Optional<ResponseLinking> linking = linkingStore.read(argOutput);
    checkState(linking.isPresent(), "No linking found for " + docID);
    return DocumentSystemOutput2015.from(argOutput, linking.get());
  }

  @Override
  public void write(final DocumentSystemOutput output) throws IOException {
    checkArgument(output instanceof DocumentSystemOutput2015);
    argumentStore.write(output.arguments());
    linkingStore.write(((DocumentSystemOutput2015) output).linking());
  }

  @Override
  public void close() throws IOException {
    argumentStore.close();
    linkingStore.close();
  }
}
