package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.transformers.QuoteFilter;

import com.google.common.base.Charsets;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class BuildQuoteFilter {

  private static final Logger log = LoggerFactory.getLogger(BuildQuoteFilter.class);

  private BuildQuoteFilter() {
    throw new UnsupportedOperationException();
  }

  private static void usage() {
    log.error("usage: BuildQuoteFilter paramFile\n" +
        "parameters are:\n" +
        "\tdocIdToFileMap: tab-separated map from document IDs to original text files\n" +
        "\tquoteFilter: file storing serialized quote filter");
    System.exit(1);
  }

  public static void main(String[] argv) throws IOException {
    if (argv.length != 1) {
      usage();
    }

    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));

    final File docIdToFileMapFile = params.getExistingFile("docIdToFileMap");
    final File filterFile = params.getCreatableFile("quoteFilter");

    final Map<Symbol, CharSource> docIdToFileMap = FileUtils.loadSymbolToFileCharSourceMap(
        Files.asCharSource(docIdToFileMapFile, Charsets.UTF_8));

    log.info("Building quote filter from {} documents in {}", docIdToFileMap.size(),
        docIdToFileMapFile);

    final QuoteFilter quoteFilter = QuoteFilter.createFromOriginalText(docIdToFileMap);
    log.info("Writing quote filter to {}", filterFile);
    quoteFilter.saveTo(Files.asByteSink(filterFile));
  }
}
