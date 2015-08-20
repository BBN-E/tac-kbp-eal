package com.bbn.kbp.events2014.bin.QA;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.bin.QA.Warnings.Warning;
import com.bbn.kbp.events2014.bin.QA.Warnings.WarningRule;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.transformers.MakeAllRealisActual;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

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

    final ImmutableList<WarningRule<Integer>> warnings =
        ImmutableList.of();
    final CorefDocumentRenderer
        htmlRenderer = CorefDocumentRenderer.createWithDefaultOrdering(warnings);

    for (Symbol docID : store.docIDs()) {
      log.info("processing document {}", docID.asString());
      final AnswerKey answerKey = MakeAllRealisActual.neutralizeAssessedResponsesAndAssessment(
          store.read(docID));
      generateWarnings(answerKey, warnings);
      log.info("serializing {}", docID.asString());
      htmlRenderer.renderTo(
          Files
              .asCharSink(new File(outputDir, docID.asString() + ".coref.html"),
                  Charset.defaultCharset()),
          answerKey, generateWarnings(answerKey, warnings));
    }
  }

  private static ImmutableMultimap<Integer, Warning> generateWarnings(final AnswerKey answerKey,
      final ImmutableList<WarningRule<Integer>> warnings) {
    final ImmutableMultimap.Builder<Integer, Warning> result = ImmutableMultimap.builder();
    for(final WarningRule<Integer> w: warnings) {
      result.putAll(w.applyWarning(answerKey));
    }
    return result.build();
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
