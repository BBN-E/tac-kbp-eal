package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ResponseLinking;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Created by jdeyoung on 7/27/15.
 */
final class ImportForeignIDs {

  private static final Logger log = LoggerFactory.getLogger(ImportForeignIDs.class);

  public static void main(String... args) {
    try {
      trueMain(args);
    } catch (final Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void trueMain(final String[] args) throws IOException {
    final File originalAnnotationStoreLocation = new File(args[0]);
    final File originalLinkingStoreLocation = new File(args[1]);
    final File outputDirectory = new File(args[2]);
    final AnnotationStore originalAnnotationStore =
        AssessmentSpecFormats.openAnnotationStore(originalAnnotationStoreLocation,
            AssessmentSpecFormats.Format.KBP2015);
    final LinkingStore originalLinkingStore =
        LinkingSpecFormats.openLinkingStore(originalLinkingStoreLocation);

    final AnnotationStore newAnnotationStore =
        AssessmentSpecFormats.openOrCreateAnnotationStore(new File(outputDirectory, "arguments"),
            AssessmentSpecFormats.Format.KBP2015);
    final LinkingStore newLinkingStore =
        LinkingSpecFormats.openOrCreateLinkingStore(new File(outputDirectory, "linking"));

    for (final Symbol docid : originalAnnotationStore.docIDs()) {
      log.info("Converting {}", docid);
      final ImmutableBiMap.Builder<String, String> originalToSystem = ImmutableBiMap.builder();
      final AnswerKey key = AssessmentSpecFormats
          .uncachedReadFromDirectoryAnnotationStoreKeepingOldIDS(originalAnnotationStore, docid,
              originalToSystem);
      newAnnotationStore.write(key);
      final Optional<ResponseLinking> transformedLinking = LinkingSpecFormats
          .readFromLinkingStoreTransformingIDs(originalLinkingStore, docid, key.allResponses(),
              originalToSystem.build());
      newLinkingStore.write(transformedLinking.get());
    }
    originalAnnotationStore.close();
    originalLinkingStore.close();
    newAnnotationStore.close();
    newLinkingStore.close();
  }
}
