package com.bbn.kbp.events2014.bin;

import java.io.IOException;

public final class ExportCorpusLinkingsForAssessment {

  private ExportCorpusLinkingsForAssessment() {
    throw new UnsupportedOperationException();
  }

  public static void main(String[] argv) {
    // we wrap the main method in this way to
    // ensure a non-zero return value on failure
    try {
      trueMain(argv);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void trueMain(String[] argv) throws IOException {
    /*final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final List<File> systemCorpusLinkingFiles = getSystemCorpusLinkingFiles(params);
    final File targetFile = params.getCreatableFile("com.bbn.kbp.events2014.outputAssessmentFile");
    final boolean mergeExisting = params.getBoolean("com.bbn.kbp.events2014.mergeWithExisting");

    final CorpusQueryAssessments initialAssessments;
    if (mergeExisting && targetFile.isFile()) {
      initialAssessments = SingleFileQueryStoreLoader.create()
          .open2016(Files.asCharSource(targetFile, Charsets.UTF_8));
    } else {
      initialAssessments = CorpusQueryAssessments.createEmpty();
    }

    final CorpusEventFrameLoader loader = CorpusEventFrameIO.loaderFor2016();
    for (final File systemCorpusLinkingFile : systemCorpusLinkingFiles) {
      final ImmutableSet<CorpusEventFrame> corpusEventFrames =
          loader.loadCorpusEventFrames(Files.asCharSource(systemCorpusLinkingFile, Charsets.UTF_8));
      initialAssessments.
    }*/
  }
}
