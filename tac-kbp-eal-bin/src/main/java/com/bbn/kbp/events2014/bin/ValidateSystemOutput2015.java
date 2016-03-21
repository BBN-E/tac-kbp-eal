package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.ImportForeignIDs;
import com.bbn.kbp.events2014.validation.TypeAndRoleValidator;

import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static com.bbn.bue.common.files.FileUtils.loadSymbolToFileMap;

public final class ValidateSystemOutput2015 {

  private static final Logger log = LoggerFactory.getLogger(ValidateSystemOutput2015.class);

  private ValidateSystemOutput2015() {
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
        "Checks a KBP EAL 2015 system output store can be loaded and then dumps it in a human-readable way, resolving \n"
            +
            " offset spans against the original text.\n" +
            "usage: validateSystemOutput parameterFile\n" +
            "Parameter files are lines of key : value pairs\n" +
            "Parameters:\n\tsystemOutputStore: the system output to be validated. This should be the directory with 'argument' and 'linking' subdirectories \n"
            +
            "\tdump: whether to dump a human-readable form of the input to standard output\n" +
            "\tdocIDMap: (only if dump is true) a list of tab-separated pairs of doc ID and path to original text.\n"
            +
            "\tvalidRoles: is data/2015.types.txt (for KBP 2015)\n" +
            "\talwaysValidRoles: is 'Time, Place' (for KBP 2015)\n");
    System.exit(1);
  }

  private static void trueMain(String[] argv) {
    if (argv.length != 1) {
      usage();
    }

    try {
      final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
      log.info(params.dump());

      final TypeAndRoleValidator typeAndRoleValidator =
          TypeAndRoleValidator.createFromParameters(params);
      final ValidateSystemOutput validator = ValidateSystemOutput.create(typeAndRoleValidator,
          CONVERT_TO_STANDARD_IDS);

      final File systemOutputStoreFile = params.getExistingFileOrDirectory("systemOutputStore");

      final File docIDMappingFile = params.getExistingFile("docIDMap");
      log.info("Using map from document IDs to original text: {}", docIDMappingFile);
      final Map<Symbol, File> docIDMap = loadSymbolToFileMap(docIDMappingFile);

      final ValidateSystemOutput.Result validationResult;
      if (params.getBoolean("dump")) {
        validationResult = validator
            .validateAndDump(systemOutputStoreFile, Integer.MAX_VALUE, docIDMap,
                SystemOutputLayout.KBP_EA_2015);
      } else {
        validationResult =
            validator.validateOnly(systemOutputStoreFile, Integer.MAX_VALUE, docIDMap,
                SystemOutputLayout.KBP_EA_2015);
      }
      if (!validationResult.wasSuccessful()) {
        throw validationResult.errors().get(0);
      }
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }

  static final ValidateSystemOutput.Preprocessor CONVERT_TO_STANDARD_IDS =
      new ValidateSystemOutput.Preprocessor() {
        @Override
        public File preprocess(final File uncompressedSubmissionDir) throws IOException {
          final File outputDir = Files.createTempDir();
          ImportForeignIDs.importForeignIDs(uncompressedSubmissionDir, outputDir);
          return outputDir;
        }
      };
}
