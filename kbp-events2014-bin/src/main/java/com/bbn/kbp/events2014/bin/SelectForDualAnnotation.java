package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.math.MathUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;

import com.google.common.base.Charsets;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.compose;

public final class SelectForDualAnnotation {

  private static final Logger log = LoggerFactory.getLogger(SelectForDualAnnotation.class);

  private SelectForDualAnnotation() {
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
    // file mapping documents to which annotators worked on them
    final File annotatorsFile = params.getExistingFile("annotatorsFile");
    final File outputDirectory = params.getCreatableDirectory("outputDirectory");
    final File annotationStoreDirectory = params.getExistingDirectory("annotationStore");
    final File excludeListFile = params.getExistingFile("excludeListFile");

    final AnnotationStore outputStore = AssessmentSpecFormats.createAnnotationStore(outputDirectory,
        AssessmentSpecFormats.Format.KBP2014);
    final Set<Symbol> docIDsToIgnore =
        ImmutableSet.copyOf(FileUtils.loadSymbolList(excludeListFile));
    final AnnotationStore annotationStore =
        AssessmentSpecFormats.openAnnotationStore(annotationStoreDirectory,
            AssessmentSpecFormats.Format.KBP2014);
    final Table<Symbol, Symbol, Symbol> annotatorsTable = FileUtils.loadSymbolTable(
        Files.asCharSource(annotatorsFile, Charsets.UTF_8));
    final ImmutableMap<Symbol, Integer> docIDsToNumResponses =
        buildDocIDsToNumResponses(annotationStore);

    final Set<Symbol> docIDs =
        Sets.difference(annotatorsTable.rowKeySet(), docIDsToIgnore).immutableCopy();
    final Set<Symbol> nwDocIDs = ImmutableSet.copyOf(Sets.filter(docIDs,
        compose(StringUtils.startsWith("NYT"), Functions.toStringFunction())));
    final Set<Symbol> dfDocIDs = Sets.difference(docIDs, nwDocIDs).immutableCopy();

    for (final Map.Entry<String, Set<Symbol>> config :
        ImmutableMap.of("Newswire", nwDocIDs, "Discussion forum", dfDocIDs).entrySet()) {
      final Set<Symbol> targetDocIDs = config.getValue();
      final ImmutableMultimap<Symbol, Symbol> eventTypesToDocuments =
          makeEventTypesToDocumentsMap(annotationStore, targetDocIDs);

      final ImmutableMultimap<Symbol, Symbol> annotatorsToDocuments =
          makeAnnotatorsToDocumentsMap(annotatorsTable, targetDocIDs);
      final double medianSize = determineMedianSize(annotationStore, targetDocIDs);
      log.info("Median size is {}", medianSize);

      final List<Symbol> selectedDocIDs = Lists.newArrayList();
      final Set<Symbol> selectedEventTypes = Sets.newHashSet();
      final Set<Symbol> selectedAnnotators = Sets.newHashSet();

      final ImmutableMultimap<Symbol, Symbol> documentsToEventTypes =
          eventTypesToDocuments.inverse();
      final ImmutableMultimap<Symbol, Symbol> documentsToAnnotators =
          annotatorsToDocuments.inverse();

      for (final Symbol eventType : eventTypesToDocuments.keySet()) {
        if (!selectedEventTypes.contains(eventType)) {
          final Symbol selectedDocID =
              selectClosestTo(eventTypesToDocuments.get(eventType), docIDsToNumResponses,
                  medianSize);
          selectedDocIDs.add(selectedDocID);
          selectedEventTypes.addAll(documentsToEventTypes.get(selectedDocID));
          selectedAnnotators.addAll(documentsToAnnotators.get(selectedDocID));
          log.info("For event type {}, selected {} which covers event types {} and annotators {}",
              eventType, selectedDocID,
              documentsToEventTypes.get(selectedDocID), documentsToAnnotators.get(selectedDocID));
        } else {
          log.info("Event type {} already covered", eventType);
        }
      }

      for (final Symbol annotator : annotatorsToDocuments.keySet()) {
        if (!selectedAnnotators.contains(annotator)) {
          final Symbol selectedDocID =
              selectClosestTo(annotatorsToDocuments.get(annotator), docIDsToNumResponses,
                  medianSize);
          selectedDocIDs.add(selectedDocID);
          selectedEventTypes.addAll(documentsToEventTypes.get(selectedDocID));
          selectedAnnotators.addAll(documentsToAnnotators.get(selectedDocID));
          log.info("For annotator {}, selected {} which covers event types {} and annotators {}",
              annotator, selectedDocID,
              documentsToEventTypes.get(selectedDocID), documentsToAnnotators.get(selectedDocID));
        } else {
          log.info("Annotator {} already covered", annotator);
        }
      }

      Set<Symbol> unselectedDocIDs = Sets.newHashSet(targetDocIDs);
      unselectedDocIDs.removeAll(selectedDocIDs);
      while (selectedDocIDs.size() < 25) {
        final Symbol fillUpDocID =
            selectClosestTo(unselectedDocIDs, docIDsToNumResponses, medianSize);
        unselectedDocIDs.remove(fillUpDocID);
        selectedDocIDs.add(fillUpDocID);
      }
      log.info("Selected a total of {} documents for {}", selectedDocIDs.size(), config.getKey());

      for (final Symbol selectedDocId : selectedDocIDs) {
        outputStore.write(annotationStore.read(selectedDocId).unannotatedCopy());
      }
    }
    annotationStore.close();
    outputStore.close();
    ;
  }

  private static ImmutableMultimap<Symbol, Symbol> makeAnnotatorsToDocumentsMap(
      Table<Symbol, Symbol, Symbol> annotatorsTable, Set<Symbol> targetDocIDs) {
    final ImmutableMultimap.Builder<Symbol, Symbol> ret = ImmutableMultimap.builder();

    for (final Symbol docID : targetDocIDs) {
      for (final Symbol annotator : annotatorsTable.row(docID).values()) {
        ret.put(annotator, docID);
      }
    }

    return ret.build();
  }

  private static ImmutableMultimap<Symbol, Symbol> makeEventTypesToDocumentsMap(
      AnnotationStore annotationStore, Set<Symbol> targetDocIDs) throws IOException {
    final ImmutableMultimap.Builder<Symbol, Symbol> ret = ImmutableMultimap.builder();

    for (final Symbol docID : targetDocIDs) {
      for (final Response response : annotationStore.readOrEmpty(docID).allResponses()) {
        ret.put(response.type(), docID);
      }
    }

    return ret.build();
  }


  private static ImmutableMap<Symbol, Integer> buildDocIDsToNumResponses(
      AnnotationStore annotationStore) throws IOException {
    final ImmutableMap.Builder<Symbol, Integer> ret = ImmutableMap.builder();

    for (final Symbol docID : annotationStore.docIDs()) {
      ret.put(docID, annotationStore.readOrEmpty(docID).allResponses().size());
    }

    return ret.build();
  }

  private static double determineMedianSize(AnnotationStore annotationStore,
      Set<Symbol> targetDocIDs) throws IOException {
    final List<Integer> sizes = Lists.newArrayList();
    for (final Symbol docID : targetDocIDs) {
      sizes.add(annotationStore.readOrEmpty(docID).allResponses().size());
    }
    return MathUtils.median(sizes).get();
  }

  @MoveToBUECommon
  private static double median(List<Integer> sizes) {
    final ImmutableList<Integer> sorted = Ordering.natural().immutableSortedCopy(sizes);
    if (sorted.size() % 2 == 0) {
      return 0.5 * (sorted.get(sorted.size() / 2) + sorted.get(sorted.size() / 2 - 1));
    } else {
      return sorted.get(sorted.size() / 2);
    }
  }

  private static Symbol selectClosestTo(Collection<Symbol> docIDs,
      ImmutableMap<Symbol, Integer> docIDsToNumResponses, double targetSize) {
    checkArgument(!docIDs.isEmpty());
    double smalletDivergence = Integer.MAX_VALUE;
    Symbol closestDocID = null;

    for (final Symbol docID : docIDs) {
      final int size = docIDsToNumResponses.get(docID);
      final double divergence = Math.abs(targetSize - size);
      if (divergence < smalletDivergence) {
        smalletDivergence = divergence;
        closestDocID = docID;
      }
    }

    return closestDocID;
  }
}
