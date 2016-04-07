package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events2014.KBPEA2015OutputLayout;
import com.bbn.kbp.events2014.validation.TypeAndRoleValidator;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

/**
 * This is a wrapper around the validator to make it work nicely for NIST's online submission
 * system. Other users should just us {@link com.bbn.kbp.events2014.bin.ValidateSystemOutput2015}
 * directly.
 */
public final class NISTValidator2015  {

  private static final Logger log = LoggerFactory.getLogger(NISTValidator2015.class);

  private static void usage() {
    log.error(
        "usage: NISTValidator2015 rolesFile docIdToOriginalTextMap [VERBOSE|COMPACT] submissionFile");
    System.exit(NISTValidator.ERROR_CODE);
  }
  public static void main(String[] argv) throws IOException {
    if (argv.length != 4) {
      usage();
    }

    final File rolesFile = new File(argv[0]);
    log.info("Loading valid roles from {}", rolesFile);
    if (!rolesFile.exists()) {
      throw new FileNotFoundException(String.format("Roles file not found: %s", rolesFile));
    }

    final File docIdMapFile = new File(argv[1]);
    if (!docIdMapFile.exists() && docIdMapFile.isFile()) {
      throw new FileNotFoundException(String.format(
          "DocID-->original text mapping not found: %s", docIdMapFile));
    }
    final Map<Symbol, File> docIdMap = FileUtils.loadSymbolToFileMap(docIdMapFile);

    final ValidateSystemOutput validator = ValidateSystemOutput.create(
        TypeAndRoleValidator.create(SymbolUtils.setFrom("Time", "Place"),
            FileUtils.loadSymbolMultimap(Files.asCharSource(rolesFile, Charsets.UTF_8))),
        ValidateSystemOutput2015.CONVERT_TO_STANDARD_IDS);

    NISTValidator.Verbosity verbosity = null;

    final String verbosityParam = argv[2];
    try {
      verbosity = NISTValidator.Verbosity.valueOf(verbosityParam);
    } catch (Exception e) {
      log.error("Invalid verbosity {}", verbosityParam);
      usage();
    }

    final File submitFile = new File(argv[3]);

    final NISTValidator nistValidator = new NISTValidator(validator, verbosity);
    nistValidator.run(docIdMap, submitFile, KBPEA2015OutputLayout.get());
  }


}
