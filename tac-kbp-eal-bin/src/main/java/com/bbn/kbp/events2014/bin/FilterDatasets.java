package com.bbn.kbp.events2014.bin;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * A utility for removing files from all datasets used by the annotation tool. We used this when
 * removing long documents during the evaluation.
 */
public final class FilterDatasets {

  private FilterDatasets() {
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
    final File annotationRoot = new File(argv[0]);
    final File filterFile = new File(argv[1]);

    final Set<String> bannedDocs = ImmutableSet.copyOf(
        Files.asCharSource(filterFile, Charsets.UTF_8).readLines());

    final File datasetsDir = new File(annotationRoot, "datasets");

    for (final File datasetDir : datasetsDir.listFiles()) {
      if (datasetDir.isDirectory()) {
        final File docList = new File(datasetDir, "documents.list");
        final Set<String> docsInList = ImmutableSet.copyOf(
            Files.asCharSource(docList, Charsets.UTF_8).readLines());
        Files.asCharSink(docList, Charsets.UTF_8)
            .writeLines(Sets.difference(docsInList, bannedDocs));
      }
    }
  }
}
