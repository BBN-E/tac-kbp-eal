package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.transformers.KeepBestJustificationOnly;
import com.bbn.kbp.events2014.transformers.ProbableInferenceCases;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

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
    final SystemOutputStore sourceStore =
        AssessmentSpecFormats.openSystemOutputStore(inputStoreLocation, fileFormat);
    final SystemOutputStore destStore =
        AssessmentSpecFormats.createSystemOutputStore(outputStoreLocation, fileFormat);
    final Function<SystemOutput, SystemOutput> bestJustFilter = KeepBestJustificationOnly.create();
    final Function<SystemOutput, SystemOutput> probablyInferenceFilter =
        ProbableInferenceCases.createKeepingMultisentenceJustifications();

    final Optional<File> linkingStoreLocation = params.getOptionalExistingDirectory("linkingStore");
    final Optional<LinkingStore> linkingStore;
    if(linkingStoreLocation.isPresent()) {
      linkingStore = Optional.of(LinkingSpecFormats.openOrCreateLinkingStore(linkingStoreLocation.get()));
    }
    else {
      linkingStore = Optional.absent();
    }
    
    log.info("Source store has {} documents", sourceStore.docIDs().size());
    for (final Symbol docID : sourceStore.docIDs()) {
      final SystemOutput original = sourceStore.read(docID);
      final SystemOutput probableInferenceCases = probablyInferenceFilter.apply(original);
      
      final Optional<ResponseLinking> responseLinking;
      if(linkingStore.isPresent()) {
        responseLinking = linkingStore.get().read(original);
      }
      else {
        responseLinking = Optional.absent();
      }
      
      final SystemOutput filtered;
      if(responseLinking.isPresent()) {
        filtered = ((KeepBestJustificationOnly)bestJustFilter).apply(original, responseLinking.get());
      }
      else {
        filtered = bestJustFilter.apply(original);
      }
      
      int numFiltered = original.size() - filtered.size();

      final SystemOutput toOutput;

      if (keepInferenceCases) {
        toOutput = SystemOutput
            .unionKeepingMaximumScore(ImmutableList.of(filtered, probableInferenceCases));
        if (toOutput.size() > filtered.size()) {
          log.info(
              "For document {}, filtered out {} responses as duplicate justifications, but restored {} as probably inference cases",
              docID, numFiltered, toOutput.size() - filtered.size());
        } else {
          log.info("For document {}, filtered out {} responses as duplicate justifications",
              docID, numFiltered);
        }
      } else {
        toOutput = filtered;
        log.info("For document {}, filtered out {} responses as duplicate justifications",
            docID, numFiltered);
      }

      destStore.write(toOutput);
    }

    sourceStore.close();
    destStore.close();
    
    if(linkingStore.isPresent()) {
      linkingStore.get().close();
    }
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
