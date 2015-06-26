package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.io.ArgumentStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.transformers.KeepBestJustificationOnly;
import com.bbn.kbp.events2014.transformers.ResponseMapping;

import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public final class KeepOnlyBestResponses {

  private static final Logger log = LoggerFactory.getLogger(KeepOnlyBestResponses.class);

  private static void usage() {
    log.error("usage: KeepOnlyBestResponses paramFile\n" +
        "parameters are:\n" +
        "\tinputStore: input system output store\n" +
        "\toutputStore: location to write filtered output store. Must be non-existent or an empty directory.\n"
        +
        "\tkeepInferenceCases: whether to keep cases that look like inference, even if not highest scoring");
    System.exit(1);
  }

  private static void trueMain(String[] argv) throws IOException {
    if (argv.length != 1) {
      usage();
    }

    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    log.info(params.dump());

    final File inputStoreLocation = params.getExistingDirectory("inputStore");
    final File outputStoreLocation = params.getCreatableDirectory("outputStore");


    final boolean keepInferenceCases = params.getBoolean("keepInferenceCases");

    final AssessmentSpecFormats.Format fileFormat =
        params.getEnum("fileFormat", AssessmentSpecFormats.Format.class);
    final ArgumentStore sourceStore =
        AssessmentSpecFormats.openSystemOutputStore(inputStoreLocation, fileFormat);
    final ArgumentStore destStore =
        AssessmentSpecFormats.createSystemOutputStore(outputStoreLocation, fileFormat);

    final Optional<LinkingStore> sourceLinkingStore;
    final Optional<LinkingStore> destLinkingStore;
    final Optional<File> inputLinkingStoreLocation = params.getOptionalExistingDirectory(
        "linkingStore");
    if (inputLinkingStoreLocation.isPresent()) {
      log.info("Also applying keep best to linking");
      final File outputLinkingStoreLocation = params.getExistingDirectory("outputLinkingStore");
      sourceLinkingStore = Optional.of(LinkingSpecFormats.openLinkingStore(inputLinkingStoreLocation.get()));
      destLinkingStore = Optional.of(LinkingSpecFormats.openOrCreateLinkingStore(outputLinkingStoreLocation));
    } else {
      sourceLinkingStore = Optional.absent();
      destLinkingStore = Optional.absent();
    }

    log.info("Source store has {} documents", sourceStore.docIDs().size());
    for (final Symbol docID : sourceStore.docIDs()) {
      final ArgumentOutput original = sourceStore.read(docID);
      final ResponseMapping responseMapping = KeepBestJustificationOnly.computeResponseMapping(
          original);
      final ArgumentOutput filtered = responseMapping.apply(original);
      int numFiltered = original.size() - filtered.size();

      log.info("For document {}, filtered out {} responses as duplicate justifications",
          docID, numFiltered);

      destStore.write(filtered);

      if (sourceLinkingStore.isPresent()) {
        final Optional<ResponseLinking> originalLinking = sourceLinkingStore.get().read(original);
        if (originalLinking.isPresent()) {
          // .get() save because source and dest are set together
          destLinkingStore.get().write(responseMapping.apply(originalLinking.get()));
        } else {
          throw new RuntimeException("No linking present for document " + docID);
        }
      }
    }

    sourceStore.close();
    destStore.close();
  }


  public static void main(String[] argv) {
    // to get proper exit status on crashes...
    try {
      trueMain(argv);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private KeepOnlyBestResponses() {
    throw new UnsupportedOperationException();
  }
}
