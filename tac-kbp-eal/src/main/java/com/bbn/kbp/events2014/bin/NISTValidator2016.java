package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.KBPEA2016OutputLayout;
import com.bbn.kbp.events2014.io.LinkingStoreSource;
import com.bbn.kbp.events2014.validation.LinkingValidator;
import com.bbn.kbp.events2014.validation.LinkingValidators;
import com.bbn.kbp.events2014.validation.TypeAndRoleValidator;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

/**
 * This is a wrapper around the 2016 validator to make it work nicely for NIST's online submission
 * system. Other users should just us {@link com.bbn.kbp.events2014.bin.ValidateSystemOutput2016}
 * directly.
 */
public final class NISTValidator2016  {

  private static final Logger log = LoggerFactory.getLogger(NISTValidator2016.class);

  enum Args {
    ROLES_FILE,
    LINKABLE_TYPES_FILE,
    DOCID_MAP,
    VERBOSITY,
    SUBMISSION_FILE
  }

  private static void usage() {
    log.error(
        "usage: NISTValidator2016 rolesFile linkableTypesFile docIdToOriginalTextMap [VERBOSE|COMPACT] submissionFile");
    System.exit(NISTValidator.ERROR_CODE);
  }
  public static void main(String[] argv) throws IOException {
    if (argv.length != Args.values().length) {
      usage();
    }

    final File rolesFile = new File(argv[Args.ROLES_FILE.ordinal()]);
    log.info("Loading valid roles from {}", rolesFile);
    if (!rolesFile.isFile()) {
      throw new FileNotFoundException("Roles file not found: " + rolesFile);
    }

    final File linkableTypesFile = new File(argv[Args.LINKABLE_TYPES_FILE.ordinal()]);
    log.info("Loading linkable types from {}", linkableTypesFile);
    if (!linkableTypesFile.isFile()) {
      throw new FileNotFoundException("Linkable types file not found: " + rolesFile);
    }

    final File docIdMapFile = new File(argv[Args.DOCID_MAP.ordinal()]);
    if (!docIdMapFile.isFile()) {
      throw new FileNotFoundException(String.format(
          "DocID-->original text mapping not found: %s", docIdMapFile));
    }
    final Map<Symbol, File> docIdMap = FileUtils.loadSymbolToFileMap(docIdMapFile);

    final TypeAndRoleValidator typeAndRoleValidator = TypeAndRoleValidator
        .create(ImmutableSet.<Symbol>of(), FileUtils.loadSymbolMultimap(
            Files.asCharSource(rolesFile, Charsets.UTF_8)));
    final LinkingValidator linkingValidator =
        LinkingValidators.compose(LinkingValidators.validatorFromMap(FileUtils
                .loadSymbolMultimap(Files.asCharSource(linkableTypesFile, Charsets.UTF_8))),
            LinkingValidators.banGeneric());

    final ValidateSystemOutput validator = ValidateSystemOutput.create(
        typeAndRoleValidator, linkingValidator, ValidateSystemOutput2015.convertToStandardIds(
            LinkingStoreSource.createFor2016(), KBPEA2016OutputLayout.get()));

    NISTValidator.Verbosity verbosity = null;

    final String verbosityParam = argv[Args.VERBOSITY.ordinal()];
    try {
      verbosity = NISTValidator.Verbosity.valueOf(verbosityParam);
    } catch (Exception e) {
      log.error("Invalid verbosity {}", verbosityParam);
      usage();
    }

    final File submitFile = new File(argv[Args.SUBMISSION_FILE.ordinal()]);

    final NISTValidator nistValidator = new NISTValidator(validator, verbosity);
    nistValidator.run(docIdMap, submitFile, KBPEA2016OutputLayout.get());
  }


}
