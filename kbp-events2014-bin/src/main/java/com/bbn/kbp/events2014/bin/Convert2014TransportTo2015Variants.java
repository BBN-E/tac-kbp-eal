package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Created by jdeyoung on 8/4/15.
 */
public class Convert2014TransportTo2015Variants {

  private static final Logger log = LoggerFactory
      .getLogger(Convert2014TransportTo2015Variants.class);

  private static final Symbol TRANSPORT_SYMBOL = Symbol.from("Movement.Transport");
  private static final Symbol PERSON_SYMBOL = Symbol.from("Movement.Transport-Person");
  private static final Symbol ARTIFACT_SYMBOL = Symbol.from("Movement.Transport-Artifact");

  public static void main(String... args) {
    try {
      trueMain(args);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void trueMain(final String[] args) throws IOException {
    final AnnotationStore input = AssessmentSpecFormats.openAnnotationStore(new File(args[0]),
        AssessmentSpecFormats.Format.KBP2014);
    final AnnotationStore output = AssessmentSpecFormats.createAnnotationStore(new File(args[1]),
        AssessmentSpecFormats.Format.KBP2015);
    int transformed = 0;
    for (final Symbol docid : input.docIDs()) {
      final AnswerKey old = input.readOrEmpty(docid);
      final AnswerKey restrictedToRelevantTypes = old.filter(CorrectTransportFilter);

      final ImmutableList.Builder<Response> nuResponses = ImmutableList.builder();
      for (final AssessedResponse assessedResponse : restrictedToRelevantTypes
          .annotatedResponses()) {
        transformed += 1;
        nuResponses.add(assessedResponse.response().copyWithSwappedType(PERSON_SYMBOL));
        nuResponses.add(assessedResponse.response().copyWithSwappedType(ARTIFACT_SYMBOL));
      }
      final AnswerKey nu = AnswerKey
          .from(docid, ImmutableList.<AssessedResponse>of(), nuResponses.build(),
              old.corefAnnotation());
      output.write(nu);
    }
    input.close();
    output.close();
    log.info("Wrote {} new events.", transformed);
  }


  private static final AnswerKey.Filter CorrectTransportFilter = new AnswerKey.Filter() {
    @Override
    public Predicate<AssessedResponse> assessedFilter() {
      return new Predicate<AssessedResponse>() {
        @Override
        public boolean apply(final AssessedResponse input) {
          return input.response().type() == TRANSPORT_SYMBOL && input
              .isCorrectUpToInexactJustifications();
        }
      };
    }

    @Override
    public Predicate<Response> unassessedFilter() {
      return Predicates.alwaysFalse();
    }
  };
}
