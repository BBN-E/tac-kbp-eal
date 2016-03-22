package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;

public final class KBPEATestUtils {

  public static KBPString kbpString(String s) {
    return KBPString.from(s, 0, 1);
  }


  public static TypeRoleFillerRealis dummyTRFR(Symbol docid, KBPString CAS) {
    return TypeRoleFillerRealis.of(docid, Symbol.from("dummyType"),
        Symbol.from("dummyRole"), KBPRealis.Actual, CAS);
  }



/*    public static AnswerKey minimalAnswerKey(ResponseLinking linking, CorefAnnotation corefAnnotation) {
        final ImmutableSet.Builder<AssessedResponse> responses = ImmutableSet.builder();

        for (final ResponseSet eventFrame : linking.responseSets()) {
            responses.addAll(transform(eventFrame, dummyAnnotationFunction));
        }

        responses.addAll(transform(linking.incompleteResponses(), dummyAnnotationFunction));
        return AnswerKey.from(linking.docID(), responses.build(),
                ImmutableSet.<Response>of(), corefAnnotation);
    }*/

  public static AnswerKey minimalAnswerKeyFor(CorefAnnotation coref) {
    final DummyResponseGenerator responseGenerator = new DummyResponseGenerator();
    final ImmutableSet.Builder<Response> responses = ImmutableSet.builder();
    for (final KBPString s : coref.allCASes()) {
      responses.add(responseGenerator.responseFor(dummyTRFR(coref.docId(), s)));
    }

    final ImmutableSet<AssessedResponse> assessedResponses = FluentIterable
        .from(responses.build())
        .transform(dummyAnnotationFunction)
        .toSet();

    return AnswerKey.from(coref.docId(), assessedResponses,
        ImmutableSet.<Response>of(), coref);
  }

  private static final Function<Response, AssessedResponse> dummyAnnotationFunction =
      new Function<Response, AssessedResponse>() {
        @Override
        public AssessedResponse apply(Response input) {
          return AssessedResponse.from(input, ResponseAssessment.create(
              Optional.of(FieldAssessment.CORRECT),
              Optional.of(FieldAssessment.CORRECT),
              Optional.of(FieldAssessment.CORRECT),
              Optional.of(KBPRealis.Actual),
              Optional.of(FieldAssessment.CORRECT),
              Optional.of(ResponseAssessment.MentionType.NOMINAL)));
        }
      };

  public static ResponseLinking dummyResponseLinkingFor(EventArgumentLinking linking) {
    final DummyResponseGenerator responseGenerator = new DummyResponseGenerator();
    final Map<TypeRoleFillerRealis, Response> canonicalResponses = Maps.newHashMap();

    final Iterable<TypeRoleFillerRealis> allTRFRs = ImmutableSet.copyOf(
        concat(concat(linking.linkedAsSet()), linking.incomplete()));

    for (final TypeRoleFillerRealis trfr : allTRFRs) {
      canonicalResponses.put(trfr, responseGenerator.responseFor(trfr));
    }

    final Function<TypeRoleFillerRealis, Response> canonicalResponseFunction =
        Functions.forMap(canonicalResponses);

    final ImmutableSet.Builder<ResponseSet> responseSets = ImmutableSet.builder();
    for (final TypeRoleFillerRealisSet trfrSet : linking.linkedAsSet()) {
      responseSets.add(ResponseSet.from(transform(trfrSet, canonicalResponseFunction)));
    }
    final Set<Response> incompletes = FluentIterable.from(linking.incomplete())
        .transform(canonicalResponseFunction).toSet();
    return ResponseLinking.from(linking.docID(), responseSets.build(), incompletes);
  }

  public static class DummyResponseGenerator {

    private int nextIdx = 0;

    public Response responseFor(TypeRoleFillerRealis trfr) {
      return Response.of(trfr.docID(), trfr.type(),
          trfr.role(), trfr.argumentCanonicalString(), charOffsetSpan(nextIdx++),
          ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(charOffsetSpan(nextIdx++)),
          KBPRealis.Actual);
    }

    private CharOffsetSpan charOffsetSpan(int idx) {
      return CharOffsetSpan.fromOffsetsOnly(idx, idx + 1);
    }
  }
}
