package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.transformers.KeepBestJustificationOnly;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Does the second-phase of two-phase assessment. In two-phase assessment (as was used for the 2014
 * evaluation), the assessors first do coreference and then this coreference information is used to
 * do more aggressive downselection during pruning (this is possible because in the first phase each
 * system was allowed only to keep the best scoring justification for each type-role-filler-realis.
 * In the second phase, we keep only the best justification for each type-role-*coreffed
 * filler*-realis.
 *
 * This is intended to be run multiple times as the first phase annotation store is filled out. Once
 * a document is written to the second-phase annotation store, it will never be written again, even
 * if the first phase annotation has changed.  Only documents with complete first-phase coreference
 * will be ported over to the second-phase annotation store.
 */
public final class SecondPhaseAssessmentPooler {

  private static final Logger log = LoggerFactory.getLogger(SecondPhaseAssessmentPooler.class);

  private SecondPhaseAssessmentPooler() {
    throw new UnsupportedOperationException();
  }

  private static void trueMain(String[] argv) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final List<File> storesToPool = FileUtils.loadFileList(params.getExistingFile("sourceStores"));
    final File firstPhaseAnnotationStorePath =
        params.getCreatableDirectory("firstPhaseAnnotationStore");
    final File outputAnnotationStorePath =
        params.getCreatableDirectory("secondPhaseAnnotationStore");
    final AssessmentSpecFormats.Format fileFormat =
        params.getEnum("fileFormat", AssessmentSpecFormats.Format.class);

    log.info("Output will be written to {} in {} format", outputAnnotationStorePath, fileFormat);
    final AnnotationStore outputAnnotationStore = AssessmentSpecFormats.openOrCreateAnnotationStore(
        outputAnnotationStorePath, fileFormat);

    // gather all our input, which includes anything currently in the output store
    final Map<String, SystemOutputStore> storesToCombine = Maps.newHashMap();

    log.info(
        "Output annotation store currently contains responses for {} documents. These will not be altered.",
        outputAnnotationStore.docIDs().size());

    final AnnotationStore firstPhaseAnnotationStore =
        AssessmentSpecFormats.openAnnotationStore(firstPhaseAnnotationStorePath, fileFormat);
    final Set<Symbol> docIDsWithCompleteCoref = docIDsWithCompleteCoref(firstPhaseAnnotationStore);
    log.info("First-phase annotation store has {} documents with complete coref which will "
            + "be used to populate the second phase annotation store: {}",
        docIDsWithCompleteCoref.size(), docIDsWithCompleteCoref);

    for (final File inputStoreFile : storesToPool) {
      final SystemOutputStore inputStore =
          AssessmentSpecFormats.openSystemOutputStore(inputStoreFile, fileFormat);
      log.info("Importing responses from {} which contains {} documents",
          inputStoreFile, inputStore.docIDs().size());

      storesToCombine.put(inputStoreFile.getAbsolutePath(), inputStore);
    }

    // Recall any documents already in the output store are left unchanged
    final Set<Symbol> docsToImport =
        Sets.difference(docIDsWithCompleteCoref, outputAnnotationStore.docIDs());
    for (final Symbol docId : docsToImport) {
      final List<SystemOutput> responseSets = Lists.newArrayList();

      final StringBuilder sb = new StringBuilder();

      for (final Map.Entry<String, SystemOutputStore> storeEntry : storesToCombine.entrySet()) {
        final SystemOutput responses = storeEntry.getValue().readOrEmpty(docId);
        responseSets.add(responses);
        sb.append(String.format("\t%5d response from %s\n", responses.size(), storeEntry.getKey()));
      }

      final AnswerKey answerKey = firstPhaseAnnotationStore.read(docId);
      final AnswerKey combinedOutput = responsesNeededToScore(responseSets, answerKey);
      outputAnnotationStore.write(combinedOutput);
      log.info("\nFor document {}\n{}\n{} responses total", docId, sb.toString(),
          combinedOutput.allResponses().size());
    }

    // storesToCombine.values() includes the output store
    for (final SystemOutputStore store : storesToCombine.values()) {
      store.close();
    }
    firstPhaseAnnotationStore.close();
    outputAnnotationStore.close();
  }

  // for each system output, keep only the responses needed to score it, taking into account the
  // coref information in answerKey. Then take the union of the result of this for all system outputs.
  private static AnswerKey responsesNeededToScore(Iterable<SystemOutput> systemOutputs,
      AnswerKey answerKey) {
    final ImmutableSet<SystemOutput> locallyBestUsingCoref = FluentIterable.from(systemOutputs)
        .transform(KeepBestJustificationOnly.asFunctionUsingCoref(answerKey.corefAnnotation())).toSet();
    final SystemOutput pooledResponsesUsingCoref =
        SystemOutput.unionKeepingMaximumScore(locallyBestUsingCoref);

    final Set<Response> responsesAdded = Sets.difference(pooledResponsesUsingCoref.responses(),
        answerKey.allResponses());
    if (responsesAdded.isEmpty()) {
      return toAnswerKeyWithCoref(pooledResponsesUsingCoref, answerKey.corefAnnotation());
    } else {
      // uh-oh, our sanity check failed. We should never have added answers beyond what was
      // originally present in the answer key
      final StringBuilder sb = new StringBuilder();
      sb.append("It should have been impossible for using coref to add new responses. ")
          .append("However, the following responses were added:\n").append(responsesAdded);
      sb.append("\n");

      throw new IllegalStateException(sb.toString());
    }
  }

  // turns a system output into an answer key with all responses in the systme output present as
  // unannotated responses.  The provided coref annotation is used but mus tbe trimmed because it may
  // refer to strings found in no response
  private static AnswerKey toAnswerKeyWithCoref(SystemOutput systemOutput,
      CorefAnnotation corefAnnotation) {
    final Set<KBPString> allCASesInResponses = FluentIterable.from(systemOutput.responses())
        .transform(Response.CASFunction())
        .toSet();

    final CorefAnnotation corefOnOnlyRelevantCASes =
        corefAnnotation.copyRemovingStringsNotIn(allCASesInResponses);
    return AnswerKey
        .from(systemOutput.docId(), ImmutableSet.<AssessedResponse>of(), systemOutput.responses(),
            corefOnOnlyRelevantCASes);
  }

  private static Set<Symbol> docIDsWithCompleteCoref(AnnotationStore annotationStore)
      throws IOException {
    final ImmutableSet.Builder<Symbol> ret = ImmutableSet.builder();
    for (final Symbol docID : annotationStore.docIDs()) {
      if (annotationStore.readOrEmpty(docID).corefAnnotation().isComplete()) {
        ret.add(docID);
      }
    }
    return ret.build();
  }

  public static void main(String[] argv) {
    try {
      trueMain(argv);
    } catch (Throwable t) {
      log.error("Exception thrown: {}", t);
      System.exit(1);
    }
  }
}
