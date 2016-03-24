package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CorpusEventFrame;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocumentSystemOutput;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class SystemOutputStore2016 implements SystemOutputStore {

  private final SystemOutputStore2015 docLevelOutput;

  private final CorpusEventFrameWriter eventFrameWriter = CorpusEventFrameIO.writerFor2016();
  private final CorpusEventFrameLoader eventFrameReader = CorpusEventFrameIO.loaderFor2016();

  private SystemOutputStore2016(final SystemOutputStore2015 docLevelOutput) {
    this.docLevelOutput = checkNotNull(docLevelOutput);
  }

  public static SystemOutputStore2016 for2015Store(
      final SystemOutputStore2015 systemOutputStore2015) {
    return new SystemOutputStore2016(systemOutputStore2015);
  }

  public CorpusEventLinking readCorpusEventFrames() throws IOException {
    return eventFrameReader.loadCorpusEventFrames(Files.asCharSource(corpusLinkingFile(),
        Charsets.UTF_8));
  }

  public void writeCorpusEventFrames(CorpusEventLinking corpusEventFrames) throws IOException {
    final File corpusLinkingFile = corpusLinkingFile();
    corpusLinkingFile.mkdirs();
    eventFrameWriter.writeCorpusEventFrames(corpusEventFrames, Files.asCharSink(corpusLinkingFile,
        Charsets.UTF_8));
  }

  @Override
  public final Set<Symbol> docIDs() throws IOException {
    return docLevelOutput.docIDs();
  }

  @Override
  public DocumentSystemOutput2015 read(final Symbol docID) throws IOException {
    return docLevelOutput.read(docID);
  }

  @Override
  public void write(final DocumentSystemOutput output) throws IOException {
    docLevelOutput.write(output);
  }

  @Override
  public void close() throws IOException {
    docLevelOutput.close();
  }

  private File corpusLinkingFile() {
    return new File(new File(docLevelOutput.directory(), "corpusLinking"), "corpusLinking");
  }
}
