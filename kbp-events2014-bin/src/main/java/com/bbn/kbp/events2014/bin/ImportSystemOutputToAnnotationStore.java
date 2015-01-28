package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.transformers.KeepBestJustificationOnly;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Predicates.in;
import static com.google.common.collect.Iterables.filter;

public final class ImportSystemOutputToAnnotationStore {

  private static final Logger log =
      LoggerFactory.getLogger(ImportSystemOutputToAnnotationStore.class);

  private ImportSystemOutputToAnnotationStore() {
    throw new UnsupportedOperationException();
  }

  public static void main(final String[] argv) {
    try {
      trueMain(argv);
    } catch (Exception e) {
      // ensure non-zero return on failure
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static final void usage() {
    log.error("\nusage: importSystemOutputToAnnotationStore paramFile\n" +
            "Parameters are:\n" +
            "\tsystemOutput: system output store to import\n" +
            "\tannotationStore: destination annotation store (existing or new)\n" +
            "\timportOnlyBestAnswers: imports only the answer the score would select (recommended: true)"
    );
  }

  private static void trueMain(final String[] argv) throws IOException {
    if (argv.length == 1) {
      final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
      log.info(params.dump());

      final Function<SystemOutput, SystemOutput> filter = getSystemOutputFilter(params);
      final Predicate<Symbol> docIdFilter = getDocIdFilter(params);

      importSystemOutputToAnnotationStore(params.getExistingDirectory("systemOutput"),
          params.getCreatableDirectory("annotationStore"), filter,
          docIdFilter,
          params.getEnum("system.fileFormat", AssessmentSpecFormats.Format.class),
          params.getEnum("annStore.fileFormat", AssessmentSpecFormats.Format.class));
    } else {
      usage();
    }
  }

  private static Function<SystemOutput, SystemOutput> getSystemOutputFilter(Parameters params) {
    final Function<SystemOutput, SystemOutput> filter;
    if (params.getBoolean("importOnlyBestAnswers")) {
      filter = KeepBestJustificationOnly.create();
      log.info("Importing only responses the scorer would select");
    } else {
      filter = Functions.identity();
      log.info("Importing all responses");
    }
    return filter;
  }

  private static final String RESTRICT_TO_THESE_DOCS = "restrictToTheseDocs";

  private static Predicate<Symbol> getDocIdFilter(Parameters params) throws IOException {
    if (params.isPresent(RESTRICT_TO_THESE_DOCS)) {
      final File docIDsFile = params.getExistingFile(RESTRICT_TO_THESE_DOCS);
      final ImmutableList<String> docIDsToImport = Files.asCharSource(docIDsFile,
          Charsets.UTF_8).readLines();
      log.info("Restricting import to {} document IDs in {}",
          docIDsToImport.size(), docIDsFile);
      return in(SymbolUtils.setFrom(docIDsToImport));
    } else {
      return Predicates.alwaysTrue();
    }
  }

  private static void importSystemOutputToAnnotationStore(File systemOutputDir, File annStoreDir,
      Function<SystemOutput, SystemOutput> filter, Predicate<Symbol> docIdFilter,
      AssessmentSpecFormats.Format systemFileFormat,
      AssessmentSpecFormats.Format annStoreFileFormat) throws IOException {
    log.info("Loading system output from {}", systemOutputDir);
    final SystemOutputStore systemOutput = AssessmentSpecFormats.openSystemOutputStore(
        systemOutputDir, systemFileFormat);

    log.info("Using assessment store at {}", annStoreDir);
    final AnnotationStore annStore = AssessmentSpecFormats.openOrCreateAnnotationStore(
        annStoreDir, annStoreFileFormat);

    int totalNumAdded = 0;
    for (final Symbol docid : filter(systemOutput.docIDs(), docIdFilter)) {
      log.info("Pushing responses for {} into assessment store", docid);
      final SystemOutput docOutput = filter.apply(systemOutput.read(docid));
      final int numResponseInSystemOutput = docOutput.size();
      final AnswerKey currentAnnotation = annStore.readOrEmpty(docid);
      final int numAnnotatedResponsesInCurrentAnnotation =
          currentAnnotation.annotatedResponses().size();
      final int numUnannotatedResponsesInCurrentAnnotation =
          currentAnnotation.unannotatedResponses().size();

      final AnswerKey newAnswerKey = currentAnnotation.copyAddingPossiblyUnannotated(
          docOutput.responses());
      final int numAdded =
          newAnswerKey.unannotatedResponses().size() - numUnannotatedResponsesInCurrentAnnotation;
      log.info(
          "For doc {}, annotation store has {} annotated and {} unannotated; system output store has {} "
              +
              "responses; added {} for assessment", docid, numAnnotatedResponsesInCurrentAnnotation,
          numUnannotatedResponsesInCurrentAnnotation, numResponseInSystemOutput, numAdded);
      annStore.write(newAnswerKey);
      totalNumAdded += numAdded;
    }
    log.info("Total number of responses added: {}", totalNumAdded);
  }
}
