package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Converts an assessment repository to 2015 format.
 */
public final class ConvertTo2015Format {
  private static final Logger log = LoggerFactory.getLogger(ConvertTo2015Format.class);

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
    if (argv.length != 2) {
      System.err.println("usage: convertTo2015Format input2014AssessentDir output2015AssesmentDir\n");
      System.exit(1);
    }

    final AnnotationStore inStore = AssessmentSpecFormats.openAnnotationStore(new File(argv[0]),
        AssessmentSpecFormats.Format.KBP2014);
    final AnnotationStore outStore = AssessmentSpecFormats.createAnnotationStore(new File(argv[1]),
        AssessmentSpecFormats.Format.KBP2015);

    for (final Symbol docID : inStore.docIDs()) {
      outStore.write(inStore.read(docID));
    }

    log.info("Converted {} documents", inStore.docIDs().size());
  }
}
