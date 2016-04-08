package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.DocumentSystemOutput;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.transformers.QuoteFilter;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class ApplyQuoteFilter {

  private static final Logger log = LoggerFactory.getLogger(ApplyQuoteFilter.class);

  private ApplyQuoteFilter() {
    throw new UnsupportedOperationException();
  }

  private static void usage() {
    log.error("usage: ApplyQuoteFilter paramFile\n" +
        "parameters are:\n" +
        "\tinputStore: input system output store\n" +
        "\toutputStore: location to write filtered output store. Must be non-existent or an empty directory.\n"
        +
        "\tquoteFilter: file storing serialized quote filter");
    System.exit(1);
  }

  public static void main(String[] argv) throws IOException {
    try {
      trueMain(argv);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static void trueMain(String[] argv) throws IOException {
    if (argv.length != 1) {
      usage();
    }

    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    log.info(params.dump());

    final QuoteFilter quoteFilter = loadQuoteFilter(params);
    final Map<SystemOutputStore, SystemOutputStore> inputStoreToOutputStore = getInputOutput(params);

    for (final Map.Entry<SystemOutputStore, SystemOutputStore> inputOutputPair : inputStoreToOutputStore.entrySet()) {
      filterStore(inputOutputPair.getKey(), inputOutputPair.getValue(), quoteFilter);
    }
  }

  private static void filterStore(SystemOutputStore source,
      SystemOutputStore dest, QuoteFilter quoteFilter) throws IOException {
    log.info("Filtering {} to {}", source, dest);

    log.info("Source store has {} documents", source.docIDs().size());
    for (final Symbol docID : source.docIDs()) {
      final DocumentSystemOutput original = source.read(docID);
      final DocumentSystemOutput filtered = quoteFilter.transform(original);
      log.info("For document {}, filtered out {} responses from quotes",
          docID, original.arguments().size() - filtered.arguments().size());

      dest.write(filtered);
    }

    source.close();
    dest.close();
  }

  private static QuoteFilter loadQuoteFilter(Parameters params) throws IOException {
    final File filterFile = params.getExistingFile("quoteFilter");

    log.info("Loading quote filter from {}", filterFile);
    return QuoteFilter.loadFrom(Files.asByteSource(filterFile));
  }

  private static ImmutableMap<SystemOutputStore, SystemOutputStore> getInputOutput(Parameters params)
      throws IOException {
    final ImmutableMap.Builder<SystemOutputStore, SystemOutputStore> ret = ImmutableMap.builder();

    final SystemOutputLayout layout = SystemOutputLayout.ParamParser.fromParamVal(
        params.getString("outputLayout"));

    final boolean inputStorePresent = params.isPresent("inputStore");
    final boolean inputStoresPresent = params.isPresent("inputStoresDir");

    if (inputStorePresent != inputStoresPresent) {
      if (inputStorePresent) {
        final File inputPath = params.getExistingDirectory("inputStore");
        final File outputPath = params.getCreatableDirectory("outputStore");
        ret.put(storeFor(inputPath, outputPath, layout));
      } else {
        final File inputStoresDir = params.getExistingDirectory("inputStoresDir");
        final File outputStoresDir = params.getCreatableDirectory("outputStoresDir");

        for (File subDir : inputStoresDir.listFiles()) {
          if (subDir.isDirectory()) {
            final File outputDir = new File(outputStoresDir, subDir.getName());
            outputDir.mkdirs();
            ret.put(storeFor(subDir, outputDir, layout));
          }
        }
      }
    } else {
      throw new RuntimeException("Exactly one of inputStore and inputStoresDir must be present");
    }

    return ret.build();
  }

  private static Map.Entry<SystemOutputStore, SystemOutputStore> storeFor(File inputPath,
      File outputPath, SystemOutputLayout layout) throws IOException {
    return Maps.immutableEntry(layout.open(inputPath), layout.openOrCreate(outputPath));
  }
}
