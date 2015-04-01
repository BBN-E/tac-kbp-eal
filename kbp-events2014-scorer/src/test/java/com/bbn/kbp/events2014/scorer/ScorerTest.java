package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.SystemOutput;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import junit.framework.TestCase;

import org.junit.Test;

import java.util.Random;

public class ScorerTest extends TestCase {

  private static final Symbol DOC = Symbol.from("DOC");
  private static final Symbol CONFLICT = Symbol.from("Conflict.Attack");
  private static final Symbol VICTIM = Symbol.from("Victim");

  private static KBPString kbpString(String s) {
    return KBPString.from(s, 0, 1);
  }

  private static Response dummyResponseOfType(Symbol type, Symbol role, String cas,
      KBPRealis realis) {
    return Response.createFrom(DOC, type, role, kbpString(cas),
        CharOffsetSpan.fromOffsetsOnly(0, 1),
        ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(
            CharOffsetSpan.fromOffsetsOnly(0, 1)),
        realis);
  }

  private static CorefAnnotation allSingletonsCoref(Iterable<Response> responses) {
    final Random rng = new Random(0);
    final CorefAnnotation.Builder corefBuilder = CorefAnnotation.strictBuilder(DOC);
    for (final Response response : responses) {
      corefBuilder.putInNewRandomCluster(response.canonicalArgument(), rng);
    }
    return corefBuilder.build();
  }

  private static SystemOutput systemOutputFromResponses(Iterable<Response> responses) {
    final ImmutableSet.Builder<Scored<Response>> scoredResponses = ImmutableSet.builder();
    for (final Response response : responses) {
      scoredResponses.add(Scored.from(response, 1.0));
    }
    return SystemOutput.from(DOC, scoredResponses.build());
  }

  @Test
  public void sampleTest() {
    final Response x = dummyResponseOfType(CONFLICT, VICTIM, "x", KBPRealis.Actual);
    final Response y = dummyResponseOfType(CONFLICT, VICTIM, "y", KBPRealis.Actual);
    final Response z = dummyResponseOfType(CONFLICT, VICTIM, "z", KBPRealis.Actual);
    final Response a = dummyResponseOfType(CONFLICT, VICTIM, "a", KBPRealis.Actual);

    final SystemOutput systemOutput = systemOutputFromResponses(ImmutableSet.of(x, y, z));
    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(x, y, z, a));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(
        ImmutableSet.of(x, y, a), ImmutableSet.of(z), coref);

    final ResponseLinking systemResponseLinking = ResponseLinking.from(systemOutput.docId(),
        ImmutableSet.of(ResponseSet.from(x, y, z)), ImmutableSet.<Response>of());
    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a, y, a)), ImmutableSet.<Response>of());

    // expected EA score is FOO
    // expected linking score is BAR
  }

  private AnswerKey makeAnswerKeyFromCorrectAndIncorrect(final ImmutableSet<Response> correct,
      final ImmutableSet<Response> incorrect, final CorefAnnotation coref) {
    final ImmutableSet.Builder<AssessedResponse> correctAssessedResponses = ImmutableSet.builder();
    for (final Response correctResponse : correct) {
      correctAssessedResponses.add(
          AssessedResponse.assessCorrectly(correctResponse, ResponseAssessment.MentionType.NAME));
    }
    final ImmutableSet.Builder<AssessedResponse> incorrectAssessedResponses =
        ImmutableSet.builder();
    for (final Response incorrectResponse : incorrect) {
      incorrectAssessedResponses
          .add(AssessedResponse.assessWithIncorrectEventType(incorrectResponse));
    }
    return AnswerKey.from(DOC, Sets.union(correctAssessedResponses.build(),
        incorrectAssessedResponses.build()), ImmutableSet.<Response>of(), coref);
  }

}
