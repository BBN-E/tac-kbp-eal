package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.transformers.KeepBestJustificationOnly;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;

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

      final SystemOutputLayout outputLayout = params.getEnum("outputLayout",
          SystemOutputLayout.class);
      final AssessmentSpecFormats.Format annStoreFileFormat =
          params.getEnum("annStore.fileFormat", AssessmentSpecFormats.Format.class);

      final ImmutableSet<SystemOutputStore> systemOutputs =
          loadSystemOutputStores(params, outputLayout);
      final ImmutableSet<AnnotationStore> annotationStores =
          loadAnnotationStores(params, annStoreFileFormat);

      importSystemOutputToAnnotationStore(systemOutputs, annotationStores, filter,
          docIdFilter);
    } else {
      usage();
    }
  }

  private static ImmutableSet<AnnotationStore> loadAnnotationStores(final Parameters params,
      final AssessmentSpecFormats.Format annStoreFileFormat) throws IOException {
    final ImmutableSet<AnnotationStore> annotationStores;

    params.assertAtLeastOneDefined("annotationStore", "annotationStoresList");
    if (params.isPresent("annotationStore")) {
      annotationStores = ImmutableSet.of(AssessmentSpecFormats.openOrCreateAnnotationStore(
          params.getCreatableDirectory("annotationStore"), annStoreFileFormat));
    } else {
      final ImmutableSet.Builder<AnnotationStore> stores = ImmutableSet.builder();
      for (final File f : FileUtils.loadFileList(params.getExistingFile("annotationStoresList"))) {
        stores.add(AssessmentSpecFormats.openOrCreateAnnotationStore(f, annStoreFileFormat));
      }
      annotationStores = stores.build();
    }
    return annotationStores;
  }

  private static ImmutableSet<SystemOutputStore> loadSystemOutputStores(final Parameters params,
      final SystemOutputLayout outputLayout) throws IOException {
    final ImmutableSet<SystemOutputStore> systemOutputs;
    params.assertAtLeastOneDefined("systemOutput", "systemOutputsList");
    if (params.isPresent("systemOutput")) {
      systemOutputs =
          ImmutableSet.of(outputLayout.open(params.getExistingDirectory("systemOutput")));
    } else {
      final ImmutableSet.Builder<SystemOutputStore> stores = ImmutableSet.builder();
      for (File dir : FileUtils.loadFileList(params.getCreatableFile("systemOutputsList"))) {
        stores.add(outputLayout.open(dir));
      }
      systemOutputs = stores.build();
    }
    return systemOutputs;
  }

  private static Function<SystemOutput, SystemOutput> getSystemOutputFilter(Parameters params) {
    final Function<SystemOutput, SystemOutput> filter;
    if (params.getBoolean("importOnlyBestAnswers")) {
      filter = KeepBestJustificationOnly.asFunctionOnSystemOutput();
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

  private static void importSystemOutputToAnnotationStore(Set<SystemOutputStore> argumentStores,
      Set<AnnotationStore> annotationStores,
      Function<SystemOutput, SystemOutput> filter, Predicate<Symbol> docIdFilter)
      throws IOException {
    log.info("Loading system outputs from {}", StringUtils.NewlineJoiner.join(argumentStores));
    log.info("Using assessment stores at {}", StringUtils.NewlineJoiner.join(annotationStores));

    final Multiset<AnnotationStore> totalNumAdded = HashMultiset.create();
    final Multiset<AnnotationStore> totalAlreadyThere = HashMultiset.create();

    for (final SystemOutputStore systemOutput : argumentStores) {
      log.info("Processing system output from {}", systemOutput);

      for (final Symbol docid : filter(systemOutput.docIDs(), docIdFilter)) {
        final SystemOutput docOutput = filter.apply(systemOutput.read(docid));
        log.info("Processing {} responses for document {}", docid, docOutput.arguments().size());

        for (final AnnotationStore annStore : annotationStores) {
          final AnswerKey currentAnnotation = annStore.readOrEmpty(docid);
          final int numAnnotatedResponsesInCurrentAnnotation =
              currentAnnotation.annotatedResponses().size();
          final int numUnannotatedResponsesInCurrentAnnotation =
              currentAnnotation.unannotatedResponses().size();

          final AnswerKey newAnswerKey = currentAnnotation.copyAddingPossiblyUnannotated(
              docOutput.arguments().responses());
          final int numAdded =
              newAnswerKey.unannotatedResponses().size() - numUnannotatedResponsesInCurrentAnnotation;
          final int numAlreadyKnown = docOutput.arguments().responses().size() - numAdded;
          log.info(
              "Annotation store {} has {} annotated and {} unannotated; added {} for assessment",
              annStore, numAnnotatedResponsesInCurrentAnnotation,
              numUnannotatedResponsesInCurrentAnnotation, numAdded);
          annStore.write(newAnswerKey);
          totalNumAdded.add(annStore, numAdded);
          totalAlreadyThere.add(annStore, numAlreadyKnown);
        }
      }
    }

    log.info("Total number of responses added: {}", totalNumAdded);
    log.info("Total number of responses already known: {}", totalAlreadyThere);
  }
}
