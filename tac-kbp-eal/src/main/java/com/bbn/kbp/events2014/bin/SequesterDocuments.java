package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;

/**
 * Removes all files on a list from all datasets in an annotation directory. This is not generally
 * useful, but we were requested to sequester certain documents in the middle of an evaluation
 * assessment. This transformation happens in-place.
 */
public final class SequesterDocuments {

  private static final Logger log = LoggerFactory.getLogger(SequesterDocuments.class);

  private SequesterDocuments() {
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
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));

    final File annotationDir = params.getExistingDirectory("annotationDir");
    final File sequesterListFile = params.getExistingFile("filesToSequester");

    final File datasetsDir = new File(annotationDir, "datasets");
    if (!datasetsDir.exists()) {
      throw new FileNotFoundException(datasetsDir.toString() + " does not exist");
    }

    final ImmutableSet<String> filesToSequester = ImmutableSet.copyOf(
        Files.readLines(sequesterListFile, Charsets.UTF_8));
    final Predicate<String> notSequesteredPred = not(in(filesToSequester));

    for (final File datasetDir : datasetsDir.listFiles()) {
      if (datasetDir.isDirectory()) {
        final File docListFile = new File(datasetDir, "documents.list");
        if (!docListFile.exists()) {
          throw new FileNotFoundException("Expected " + docListFile
              + " to exist, but it does not.");
        }
        log.info("Filtering {}", docListFile);
        Files.asCharSink(docListFile, Charsets.UTF_8).writeLines(
            filter(Files.asCharSource(docListFile, Charsets.UTF_8).readLines(),
                notSequesteredPred));

      } else {
        log.info("Skipping {} since it is not a directory", datasetDir);
      }
    }
  }
}
