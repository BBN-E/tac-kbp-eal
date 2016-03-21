package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Created by jdeyoung on 4/27/15.
 */
public final class ChangeDocIDToMatchFile {
  private static final Logger log = LoggerFactory.getLogger(ChangeDocIDToMatchFile.class);

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
      System.err.println("usage: ChangeDocIDToMatchFile input2015AssessentDir output2015AssesmentDir\n");
      System.exit(1);
    }

    final AnnotationStore inStore = AssessmentSpecFormats.openAnnotationStore(new File(argv[0]),
        AssessmentSpecFormats.Format.KBP2015);
    final AnnotationStore outStore = AssessmentSpecFormats.createAnnotationStore(new File(argv[1]),
        AssessmentSpecFormats.Format.KBP2015);

    for (final Symbol docID : inStore.docIDs()) {
      AnswerKey answerKey = convertAnswerKeyDocID(docID, inStore.read(docID));
      outStore.write(answerKey);
    }

    log.info("Converted {} documents", inStore.docIDs().size());
  }

  public static AnswerKey convertAnswerKeyDocID(Symbol docID, AnswerKey input) {
    CorefAnnotation corefAnnotation = input.corefAnnotation();
    CorefAnnotation fixedCorefAnnotation = CorefAnnotation.create(docID,
        corefAnnotation.CASesToIDs(), corefAnnotation.unannotatedCASes());
    Iterable<AssessedResponse> annotatedArgs = Iterables.transform(input.annotatedResponses(),
        fixAssessedResponseDocID(docID));
    Iterable<Response> unannotatedArgs = Iterables.transform(input.unannotatedResponses(), fixResponseDocID(docID));
    return AnswerKey.from(docID, annotatedArgs, unannotatedArgs, fixedCorefAnnotation);
  }

  private static Function<AssessedResponse, AssessedResponse> fixAssessedResponseDocID(final Symbol docID) {
    final Function<Response, Response> fixAssessResponse = fixResponseDocID(docID);
    return new Function<AssessedResponse, AssessedResponse>() {
      @Override
      public AssessedResponse apply(final AssessedResponse input) {
        return AssessedResponse.from(fixAssessResponse.apply(input.response()), input.assessment());
      }
    };
  }

  private static Function<Response, Response> fixResponseDocID(final Symbol docID) {
    return new Function<Response, Response>() {
      @Override
      public Response apply(final Response input) {
        return Response.createFrom(docID, input.type(), input.role(), input.canonicalArgument(), input.baseFiller(), input.additionalArgumentJustifications(),
            input.predicateJustifications(), input.realis());
      }
    };
  }
}
