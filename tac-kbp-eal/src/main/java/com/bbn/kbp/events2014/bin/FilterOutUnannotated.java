package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.DocumentSystemOutput;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.transformers.ResponseMapping;

import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class FilterOutUnannotated {

  private static final Logger log = LoggerFactory.getLogger(FilterOutUnannotated.class);

  private static final String USAGE = "usage: FilterOutUnannotated paramFile\n" +
      "Removes all responses not assessed in the answer key\n" +
      "parameters:\n" +
      "\tinputStore: system output to filter\n" +
      "\toutputStore: directory to write output to\n" +
      "\tlayout: KBP_EA_2014 or KBP_EA_2015\n" +
      "\tanswerKey: answer key to filter against";

  private FilterOutUnannotated() {
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

  private static void trueMain(String[] argv) throws IOException {
    if (argv.length != 1) {
      System.err.println(USAGE);
      System.exit(1);
    }

    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final SystemOutputLayout layout = SystemOutputLayout.ParamParser.fromParamVal(
        params.getString("layout"));
    final SystemOutputStore input = layout.open(params.getExistingDirectory("inputStore"));
    final SystemOutputStore output = layout.open(params.getCreatableDirectory("outputStore"));
    final AnnotationStore annotation = AssessmentSpecFormats.openAnnotationStore(
        params.getExistingDirectory("answerKey"), AssessmentSpecFormats.Format.KBP2015);

    int numDeletedTotal = 0;
    for (final Symbol docID : input.docIDs()) {
      final DocumentSystemOutput original = input.read(docID);
      final AnswerKey answerKey = annotation.readOrEmpty(docID);
      final DocumentSystemOutput filtered =
          original.copyTransformedBy(selectWhichToDelete(original, answerKey));
      int numDeletedForThisDoc = original.arguments().size() - filtered.arguments().size();
      log.info("For {} deleted {} unassessed responses", docID, numDeletedForThisDoc);
      numDeletedTotal += numDeletedForThisDoc;
      output.write(filtered);
    }

    log.info("In total, delete {} unassessed responses", numDeletedTotal);

    input.close();
    output.close();
    annotation.close();
  }

  private static ResponseMapping selectWhichToDelete(final DocumentSystemOutput systemOutput,
      final AnswerKey answerKey) {
    final ImmutableSet.Builder<Response> toDelete = ImmutableSet.builder();
    for (final Response response : systemOutput.arguments().responses()) {
      if (!answerKey.assess(response).isPresent()) {
        toDelete.add(response);
      }
    }
    return ResponseMapping.delete(toDelete.build());
  }
}
