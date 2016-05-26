package com.bbn.kbp.events2014.bin;


import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
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
import java.util.Map;

import static com.bbn.bue.common.files.FileUtils.loadSymbolToFileMap;

public final class ValidateSystemOutput2016 {

  private static final Logger log = LoggerFactory.getLogger(ValidateSystemOutput2016.class);

  private ValidateSystemOutput2016() {
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

  private static void usage() {
    log.error(
        "Checks a TAC KBP EAL 2016 system output store can be loaded and then dumps it in a human-readable way, resolving \n"
            +
            " offset spans against the original text.\n" +
            "usage: validateSystemOutput2016 parameterFile\n" +
            "Parameter files are lines of key : value pairs\n" +
            "Parameters:\n\tsystemOutputStore: the system output to be validated. This should be the directory with 'argument' and 'linking' subdirectories \n"
            +
            "\tdump: whether to dump a human-readable form of the input to standard output\n" +
            "\tdocIDMap: a list of tab-separated pairs of doc ID and path to original text.\n"
            +
            "\tvalidRoles: is data/2016.types.txt (for KBP 2016)\n"
            + "\tlinkableTypes: is data/2016.linkable.txt - for each event argument type, valid argument types for it to be linked to.\n");
    System.exit(1);
  }

  private static void trueMain(String[] argv) {
    if (argv.length != 1) {
      usage();
    }

    try {
      final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
      log.info(params.dump());

      final TypeAndRoleValidator typeAndRoleValidator = TypeAndRoleValidator
          .create(ImmutableSet.<Symbol>of(), FileUtils.loadSymbolMultimap(
              Files.asCharSource(params.getExistingFile("validRoles"), Charsets.UTF_8)));
      // for performing additional validation
      final LinkingValidator linkingValidator =
          LinkingValidators.compose(LinkingValidators.validatorFromMap(FileUtils
                  .loadSymbolMultimap(
                      Files.asCharSource(params.getExistingFile("linkableTypes"), Charsets.UTF_8))),
              LinkingValidators.banGeneric());
      final ValidateSystemOutput validator =
          ValidateSystemOutput.create(typeAndRoleValidator, linkingValidator,
              ValidateSystemOutput2015.convertToStandardIds(LinkingStoreSource.createFor2016(),
                  KBPEA2016OutputLayout.get()));

      final File systemOutputStoreFile = params.getExistingFileOrDirectory("systemOutputStore");

      final File docIDMappingFile = params.getExistingFile("docIDMap");
      log.info("Using map from document IDs to original text: {}", docIDMappingFile);
      final Map<Symbol, File> docIDMap = loadSymbolToFileMap(docIDMappingFile);

      final ValidateSystemOutput.Result validationResult;
      if (params.getBoolean("dump")) {
        validationResult = validator
            .validateAndDump(systemOutputStoreFile, Integer.MAX_VALUE, docIDMap,
                KBPEA2016OutputLayout.get());
      } else {
        validationResult =
            validator.validateOnly(systemOutputStoreFile, Integer.MAX_VALUE, docIDMap,
                KBPEA2016OutputLayout.get());
      }
      if (!validationResult.wasSuccessful()) {
        throw validationResult.errors().get(0);
      }
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }
}
