package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocumentSystemOutput;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.CrossDocSystemOutputStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.transformers.KeepBestJustificationOnly;
import com.bbn.kbp.events2014.transformers.ResponseMapping;

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
    final SystemOutputLayout layout = SystemOutputLayout.ParamParser.fromParamVal(
        params.getString("outputLayout"));

    final SystemOutputStore inputStore = layout.open(inputStoreLocation);
    final SystemOutputStore outputStore = layout.openOrCreate(outputStoreLocation);

    log.info("Source store has {} documents", inputStore.docIDs().size());
    for (final Symbol docID : inputStore.docIDs()) {
      final DocumentSystemOutput original = inputStore.read(docID);
      final ResponseMapping responseMapping = KeepBestJustificationOnly.computeResponseMapping(
          original);
      final DocumentSystemOutput filtered = original.copyTransformedBy(responseMapping);

      int numFiltered = original.arguments().size() - filtered.arguments().size();

      log.info("For document {}, filtered out {} responses as duplicate justifications",
          docID, numFiltered);

      outputStore.write(filtered);
    }
    if (inputStore instanceof CrossDocSystemOutputStore) {
      final CorpusEventLinking filteredLinking =
          ResponseMapping.apply(((CrossDocSystemOutputStore) inputStore).readCorpusEventFrames(),
              (CrossDocSystemOutputStore) outputStore);
      ((CrossDocSystemOutputStore) outputStore).writeCorpusEventFrames(filteredLinking);
    }

    inputStore.close();
    outputStore.close();
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
