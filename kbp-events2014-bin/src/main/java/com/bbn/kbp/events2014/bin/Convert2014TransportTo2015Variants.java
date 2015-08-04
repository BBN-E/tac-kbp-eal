package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

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

  private static final Symbol MOVEMENT_TRANSPORT = Symbol.from("Movement.Transport");
  private static final Symbol MOVEMENT_TRANSPORT_PERSON = Symbol.from("Movement.Transport-Person");
  private static final Symbol MOVEMENT_TRANSPORT_ARTIFACT =
      Symbol.from("Movement.Transport-Artifact");
  private static final Symbol ARTIFACT_ROLE = Symbol.from("Artifact");
  private static final Symbol VEHICLE_ROLE = Symbol.from("Vehicle");
  private static final Symbol INSTRUMENT_ROLE = Symbol.from("Instrument");
  private static final Symbol PRICE_ROLE = Symbol.from("Price");
  private static final Symbol PERSON_ROLE = Symbol.from("Person");

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

      final AnswerKey.Builder augmentedKey = old.modifiedCopyBuilder();

      final ImmutableSet<Response> movementTransportResponses = FluentIterable
          .from(restrictedToRelevantTypes.annotatedResponses())
          .transform(AssessedResponse.Response)
          .append(restrictedToRelevantTypes.unannotatedResponses())
          .toSet();

      for (final Response movementTransportResponse : movementTransportResponses) {
        ++transformedAssessments;
        augmentedKey.addUnannotated(transformResponsePerson(movementTransportResponse));
        augmentedKey.addUnannotated(transformResponseArtifact(movementTransportResponse));
      }

      output.write(augmentedKey.build());
    }
    input.close();
    output.close();
    log.info("Wrote {} transformed from assessed, {} from unassessed.", transformedAssessments,
        transformedUnassessed);
  }


  private static Response transformResponseArtifact(Response r) {
    if (r.role().equalTo(VEHICLE_ROLE)) {
      r = r.copyWithSwappedRole(INSTRUMENT_ROLE);
    }

    if (r.type().equalTo(MOVEMENT_TRANSPORT)) {
      return r.copyWithSwappedType(MOVEMENT_TRANSPORT_ARTIFACT);
    } else {
      return r;
    }
  }

  private static Response transformResponsePerson(Response r) {
    if (r.role().equalTo(VEHICLE_ROLE)) {
      r = r.copyWithSwappedRole(INSTRUMENT_ROLE);
    }

    if (r.role().equalTo(ARTIFACT_ROLE)) {
      r = r.copyWithSwappedRole(PERSON_ROLE);
    }

    if (r.type().equalTo(MOVEMENT_TRANSPORT)) {
      return r.copyWithSwappedRole(MOVEMENT_TRANSPORT_PERSON);
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
          return input.response().type() == MOVEMENT_TRANSPORT && input
              .isCorrectUpToInexactJustifications() && !input.response().role().equalTo(PRICE_ROLE);
        }
      };
    }

    @Override
    public Predicate<Response> unassessedFilter() {
      return new Predicate<Response>() {
        @Override
        public boolean apply(final Response input) {
          return input.type().equalTo(MOVEMENT_TRANSPORT) && !input.role().equalTo(PRICE_ROLE);
        }
      };
    }
  };

}
