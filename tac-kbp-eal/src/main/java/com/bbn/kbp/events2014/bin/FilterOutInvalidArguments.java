package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.DocumentSystemOutput;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;
import com.bbn.kbp.events2014.validation.TypeAndRoleValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public final class FilterOutInvalidArguments {

  private static final Logger log = LoggerFactory.getLogger(FilterOutInvalidArguments.class);

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

  private static void trueMain(String[] argv) throws IOException {
    if (argv.length != 1) {
      System.err.println("usage: FilterOutInvalidArguments param_file");
      System.exit(1);
    }

    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    log.info(params.dump());

    final SystemOutputLayout layout =
        SystemOutputLayout.ParamParser.fromParamVal(params.getString("outputLayout"));
    final SystemOutputStore input = layout.open(params.getExistingDirectory("input"));
    final SystemOutputStore output = layout.openOrCreate(params.getCreatableDirectory("output"));
    final TypeAndRoleValidator validator = TypeAndRoleValidator.createFromParameters(params);

    log.info("Filtering {} to {}, removing invalid type/role combinations", input, output);
    for (final Symbol docID : input.docIDs()) {
      final DocumentSystemOutput original = input.read(docID);
      final DocumentSystemOutput filtered = original.copyTransformedBy(
          validator.deleteInvalidResponses(original.arguments()));
      log.info("For document {}, filtered out {} invalid responses",
          docID, original.arguments().size() - filtered.arguments().size());
      output.write(filtered);
    }
    if (output instanceof SystemOutputStore2016) {
      ((SystemOutputStore2016) output)
          .writeCorpusEventFrames(((SystemOutputStore2016) input).readCorpusEventFrames());
    }
    input.close();
    output.close();
  }
}
