package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.DocumentSystemOutput;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.ArgumentStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.transformers.KeepBestJustificationOnly;
import com.bbn.kbp.events2014.transformers.QuoteFilter;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PoolSystemOutput {

  private static final Logger log = LoggerFactory.getLogger(PoolSystemOutput.class);

  private static void trueMain(String[] argv) throws IOException {
    if (argv.length != 1) {
      usage();
    }

    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final List<File> storesToPool = FileUtils.loadFileList(params.getExistingFile("storesToPool"));
    final File outputStorePath = params.getCreatableDirectory("pooledStore");
    final SystemOutputLayout systemOutputLayout =
        SystemOutputLayout.ParamParser.fromParamVal(params.getString("systemOutputLayout"));


    final Optional<QuoteFilter> quoteFilter;
    if (params.isPresent("quoteFilter")) {
      final File quoteFilterFile = params.getExistingFile("quoteFilter");
      log.info("Will apply quote filter {} to all responses", quoteFilterFile);
      quoteFilter = Optional.of(QuoteFilter.loadFrom(
          Files.asByteSource(quoteFilterFile)));
    } else {
      quoteFilter = Optional.absent();
    }

    final ArgumentStore outputStore =
        AssessmentSpecFormats.openOrCreateSystemOutputStore(outputStorePath,
            AssessmentSpecFormats.Format.KBP2015);

    // gather all our input, which includes anything currently in the output store
    final Set<Symbol> allDocIds = Sets.newHashSet();
    final Map<String, SystemOutputStore> storesToCombine = Maps.newHashMap();

    log.info("Output store currently contains responses for {} documents",
        outputStore.docIDs().size());
    allDocIds.addAll(outputStore.docIDs());

    for (final File inputStoreFile : storesToPool) {
      final SystemOutputStore inputStore = systemOutputLayout.open(inputStoreFile);
      log.info("Importing responses for {} documents from {} to {}",
          inputStore.docIDs().size(), inputStoreFile,
          outputStorePath);

      allDocIds.addAll(inputStore.docIDs());
      storesToCombine.put(inputStoreFile.getAbsolutePath(), inputStore);
    }

    for (final Symbol docId : allDocIds) {
      final List<ArgumentOutput> responseSets = Lists.newArrayList();

      final StringBuilder sb = new StringBuilder();

      for (final Map.Entry<String, SystemOutputStore> storeEntry : storesToCombine.entrySet()) {
        final DocumentSystemOutput quoteFiltered =
            quoteFilter.get().transform(storeEntry.getValue().read(docId));
        // if there are multiple responses which we know will end up in the same
        // equivalence class even without knowing coref, drop the lower scoring ones
        final DocumentSystemOutput responses = KeepBestJustificationOnly.asFunctionOnSystemOutput()
            .apply(quoteFiltered);
        responseSets.add(responses.arguments());
        sb.append(String
            .format("\t%5d response from %s\n", responses.arguments().size(), storeEntry.getKey()));
      }

      final ArgumentOutput combinedOutput = ArgumentOutput.unionKeepingMaximumScore(responseSets);
      outputStore.write(combinedOutput);
      log.info("\nFor document {}\n{}\n{} responses total", docId, sb.toString(),
          combinedOutput.size());
    }

    // storesToCombine.values() includes the output store
    for (final SystemOutputStore store : storesToCombine.values()) {
      store.close();
    }
    outputStore.close();
  }

  private static void usage() {
    log.error("usage: poolSystemOutput param_file\n" +
        "where the parameters are:\n" +
        "\tstoresToPool: file listing paths to stores to pool\n" +
        "\tpooledStore: directory of system output store for pooling results\n" +
        "\tsystemOutputLayout: KBP_EA_2014 or KBP_EA_2015\n" +
        "\t(optional) quoteFilter: quote filter to apply to all responses");
    System.exit(1);
  }

  public static void main(String[] argv) {
    try {
      trueMain(argv);
    } catch (Exception e) {
      // proper return value for use in queue sequences
      e.printStackTrace();
      System.exit(1);
    }
  }
}
