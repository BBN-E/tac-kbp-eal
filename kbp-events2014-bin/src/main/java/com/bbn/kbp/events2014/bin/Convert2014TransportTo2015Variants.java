package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

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
  private static final Symbol ARTIFACT = Symbol.from("Artifact");
  private static final Symbol VEHICLE = Symbol.from("Vehicle");
  private static final Symbol INSTRUMENT = Symbol.from("Instrument");
  private static final Symbol PRICE = Symbol.from("Price");
  private static final Symbol PERSON = Symbol.from("Person");

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
    int transformedAssessments = 0;
    int transformedUnassessed = 0;
    for (final Symbol docid : input.docIDs()) {
      final AnswerKey old = input.readOrEmpty(docid);
      final AnswerKey restrictedToRelevantTypes = old.filter(CorrectTransportFilter);

      // get old responses
      final AnswerKey restrictedToNotRelevantTypes = old.filter(notCorrectTransportFilter);
      final ImmutableSet.Builder<Response> nuResponses = ImmutableSet.builder();
      final ImmutableSet<Response> assessed = ImmutableSet.copyOf(Iterables
          .transform(restrictedToNotRelevantTypes.annotatedResponses(), AssessedResponse.Response));
      nuResponses.addAll(restrictedToNotRelevantTypes.unannotatedResponses());

      // transform responses

      for (final AssessedResponse assessedResponse : restrictedToRelevantTypes
          .annotatedResponses()) {
        transformedAssessments += 1;
        final Response person = transformResponsePerson(assessedResponse.response());
        if (!assessed.contains(person)) {
          nuResponses.add();
        }
        final Response artifact = transformResponseArtifact(assessedResponse.response());
        if (!assessed.contains(artifact)) {
          nuResponses.add(artifact);
        }
      }
      for (final Response response : restrictedToRelevantTypes.unannotatedResponses()) {
        transformedUnassessed += 1;
        final Response person = transformResponsePerson(response);
        if (!assessed.contains(person)) {
          nuResponses.add();
        }
        final Response artifact = transformResponseArtifact(response);
        if (!assessed.contains(artifact)) {
          nuResponses.add(artifact);
        }
      }

      // save the old assessed as well
      final AnswerKey nu = AnswerKey
          .from(docid, restrictedToNotRelevantTypes.annotatedResponses(), nuResponses.build(),
              old.corefAnnotation());
      output.write(nu);
    }
    input.close();
    output.close();
    log.info("Wrote {} transformed from assessed, {} from unassessed.", transformedAssessments,
        transformedUnassessed);
  }


  private static Response transformResponseArtifact(Response r) {
    if (r.role().equalTo(VEHICLE)) {
      r = r.copyWithSwappedRole(INSTRUMENT);
    }

    if (r.type().equalTo(TRANSPORT_SYMBOL)) {
      return r.copyWithSwappedType(ARTIFACT_SYMBOL);
    } else {
      return r;
    }
  }

  private static Response transformResponsePerson(Response r) {
    if (r.role().equalTo(ARTIFACT)) {
      r = r.copyWithSwappedRole(INSTRUMENT);
    }

    if (r.type().equalTo(TRANSPORT_SYMBOL)) {
      return r.copyWithSwappedRole(PERSON_SYMBOL);
    } else {
      return r;
    }
  }

  private static final AnswerKey.Filter CorrectTransportFilter = new AnswerKey.Filter() {
    @Override
    public Predicate<AssessedResponse> assessedFilter() {
      return new Predicate<AssessedResponse>() {
        @Override
        public boolean apply(final AssessedResponse input) {
          return input.response().type() == TRANSPORT_SYMBOL && input
              .isCorrectUpToInexactJustifications() && !input.response().role().equalTo(PRICE);
        }
      };
    }

    @Override
    public Predicate<Response> unassessedFilter() {
      return new Predicate<Response>() {
        @Override
        public boolean apply(final Response input) {
          return input.type().equalTo(TRANSPORT_SYMBOL) && !input.role().equalTo(PRICE);
        }
      };
    }
  };

  // slight misnomer
  private static final AnswerKey.Filter notCorrectTransportFilter = new AnswerKey.Filter() {
    @Override
    public Predicate<AssessedResponse> assessedFilter() {
      return Predicates.alwaysTrue();
    }

    @Override
    public Predicate<Response> unassessedFilter() {
      return Predicates.not(CorrectTransportFilter.unassessedFilter());
    }
  };
}
