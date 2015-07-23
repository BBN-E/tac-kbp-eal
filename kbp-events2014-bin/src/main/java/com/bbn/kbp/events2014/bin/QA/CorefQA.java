package com.bbn.kbp.events2014.bin.QA;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.bin.QA.Warnings.OverlapWarningRule;
import com.bbn.kbp.events2014.bin.QA.Warnings.WarningRule;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.transformers.MakeAllRealisActual;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static com.bbn.kbp.events2014.bin.QA.AssessmentQA.generateWarnings;

/**
 * Created by jdeyoung on 6/30/15.
 */
public class CorefQA {

  private static final Logger log = LoggerFactory.getLogger(CorefQA.class);

  public static void trueMain(String... args) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(args[0]));
    final AnnotationStore store =
        AssessmentSpecFormats.openAnnotationStore(params.getExistingDirectory("annotationStore"),
            AssessmentSpecFormats.Format.KBP2015);
    final File outputDir = params.getCreatableDirectory("outputDir");

    final ImmutableList<WarningRule> warnings =
        ImmutableList.<WarningRule>of(OverlapWarningRule.create());
    final CorefDocumentRenderer
        htmlRenderer = CorefDocumentRenderer.createWithDefaultOrdering(warnings);

    for (Symbol docID : store.docIDs()) {
      log.info("processing document {}", docID.asString());
      final AnswerKey answerKey = MakeAllRealisActual.neutralizeAssessedResponsesAndAssessment(
          store.read(docID));
      log.info("serializing {}", docID.asString());
      htmlRenderer.renderTo(
          Files
              .asCharSink(new File(outputDir, docID.asString() + ".coref.html"),
                  Charset.defaultCharset()),
          answerKey, generateWarnings(answerKey, warnings));
    }
  }

  public static void main(String... args) {
    try {
      trueMain(args);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
