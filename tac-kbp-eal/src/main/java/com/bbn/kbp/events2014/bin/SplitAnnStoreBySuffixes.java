package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Splits up an annotation store which contains annotations on the same documents, identified by
 * suffixes on the doc ID. This is mostly useful for inter-annotator agreement calculations.
 */
public final class SplitAnnStoreBySuffixes {

  private static final Logger log = LoggerFactory.getLogger(SplitAnnStoreBySuffixes.class);

  private SplitAnnStoreBySuffixes() {
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
    final File inputAnnStorePath = params.getExistingDirectory("splitAnnStore.inputStore");
    final File outputStoreBase = params.getCreatableDirectory("splitAnnStore.outputBase");
    final File suffixesFile = params.getExistingFile("splitAnnStore.suffixesList");

    final AnnotationStore inputAnnStore = AssessmentSpecFormats.openAnnotationStore(
        inputAnnStorePath,
        AssessmentSpecFormats.Format.KBP2015);
    final ImmutableSet<String> suffixes = ImmutableSet.copyOf(Files.asCharSource(suffixesFile,
        Charsets.UTF_8).readLines());
    final Map<String, AnnotationStore> outputStoresBySuffix = Maps.newHashMap();
    for (final String suffix : suffixes) {
      outputStoresBySuffix.put(suffix,
          AssessmentSpecFormats.createAnnotationStore(new File(outputStoreBase, suffix),
              AssessmentSpecFormats.Format.KBP2015));
    }

    for (final Symbol docID : inputAnnStore.docIDs()) {
      for (final String suffix : suffixes) {
        if (docID.asString().endsWith(suffix)) {
          final AnswerKey inputKey = inputAnnStore.read(docID);
          log.info("Writing {} to {} store", docID, suffix);
          outputStoresBySuffix.get(suffix).write(stripSuffix(inputKey, suffix));
          continue;
        }
        log.info("No suffix on {}; not copying to output", docID);
      }
    }

    inputAnnStore.close();
    for (final AnnotationStore outputStore : outputStoresBySuffix.values()) {
      outputStore.close();
    }
  }

  private static AnswerKey stripSuffix(final AnswerKey inputKey, final String suffix) {
    final Symbol strippedDocID = Symbol.from(StringUtils.removeSuffixIfPresent(
        inputKey.docId().asString(),
        "-" + suffix));
    return ChangeDocIDToMatchFile.convertAnswerKeyDocID(strippedDocID, inputKey);
  }
}
