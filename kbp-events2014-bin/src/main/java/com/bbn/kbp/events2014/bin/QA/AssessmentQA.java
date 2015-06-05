package com.bbn.kbp.events2014.bin.QA;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.bin.QA.Warnings.ConflictingTypeWarningRule;
import com.bbn.kbp.events2014.bin.QA.Warnings.ConjunctionWarningRule;
import com.bbn.kbp.events2014.bin.QA.Warnings.EmptyResponseWarning;
import com.bbn.kbp.events2014.bin.QA.Warnings.OverlapWarningRule;
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
 * Created by jdeyoung on 6/1/15.
 */
public class AssessmentQA {

  private static final Logger log = LoggerFactory.getLogger(AssessmentQA.class);

  public static void trueMain(String... args) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(args[0]));
    final AnnotationStore store =
        AssessmentSpecFormats.openAnnotationStore(params.getExistingDirectory("annotationStore"),
            AssessmentSpecFormats.Format.KBP2015);
    final File outputDir = params.getCreatableDirectory("outputDir");

    final ImmutableList<WarningRule> warnings = ImmutableList.of(ConjunctionWarningRule.create(),
        OverlapWarningRule.create(),
        ConflictingTypeWarningRule
            .create(params.getString("argFile"), params.getString("roleFile")),
        EmptyResponseWarning.create());

    final QADocumentRenderer htmlRenderer = QADocumentRenderer.createWithDefaultOrdering();

    for (Symbol docID : store.docIDs()) {
      log.info("processing document {}", docID.asString());
      final AnswerKey answerKey = MakeAllRealisActual.forAnswerKey().apply(store.read(docID));
      log.info("serializing {}", docID.asString());
      htmlRenderer.renderTo(
          Files.asCharSink(new File(outputDir, docID.asString() + ".html"), Charset.defaultCharset()),
          answerKey, generateWarnings(answerKey, warnings));
    }
  }

  private static ImmutableMultimap<Response, Warning> generateWarnings(
      AnswerKey answerKey, final Iterable<? extends WarningRule> warningRules) {
    ImmutableMultimap.Builder<Response, Warning> warningResponseBuilder =
        ImmutableMultimap.builder();
    for (WarningRule w : warningRules) {
      warningResponseBuilder.putAll(w.applyWarning(answerKey));
    }
    return warningResponseBuilder.build();
  }

  public static String readableTRFR(TypeRoleFillerRealis trfr) {
    return String.format("%s-%s:%s - %s", trfr.type().asString(), trfr.role().asString(),
        trfr.realis().name(), trfr.argumentCanonicalString().string());
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
