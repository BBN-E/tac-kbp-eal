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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class ScorerTest {

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
        ImmutableSet.of(ResponseSet.from(x, y, a)), ImmutableSet.<Response>of());

    // expected EA score is FOO
    // expected linking score is BAR

    assertEquals(0.1, 0.1, 0.0001); // dummy

    //final SystemExamples systemExamples = SystemExamples.create();
    //singletonTest(systemExamples);
    //oneGroupTest(systemExamples);
    //test1(systemExamples);
    //test2(systemExamples);
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

  /*
      key = { {a} {b} {c} {d} }
      l1 EA=0.4 ; EAL: TP=1 FP=0 LS=1 final=0.25
      l2 EA=0.67 ; EAL: TP=2 FP=0 LS=2 final=0.5
      l3 EA=0.86 ; EAL: TP=3 FP=0 LS=3 final=0.75
      l4 EA=1.0 ; EAL: TP=4 FP=0 LS=4 final=1
      l5 EA=0.67 ; EAL: TP=2 FP=0 LS=0 final=0.25
      l6 EA=0.86 ; EAL: TP=3 FP=0 LS=0 final=0.375
      l7 EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      l8 EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      l9 EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      l10 EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
      l11 EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
      l12 EA=0.8 ; EAL: TP=4 FP=2 LS=4 final=0.9375
      l13 EA=0.8 ; EAL: TP=4 FP=2 LS=4 final=0.9375
  */
  private void singletonTest(final SystemExamples systemExamples) {
    final ImmutableMap<String, Response> responses = systemExamples.getResponses();
    final ImmutableMap<String, ResponseLinking> linkings = systemExamples.getLinkings();

    final Response a = responses.get("a");
    final Response b = responses.get("b");
    final Response c = responses.get("c");
    final Response d = responses.get("d");

    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(a,b,c,d));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(ImmutableSet.of(a,b,c,d), ImmutableSet.<Response>of(), coref);

    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a),ResponseSet.from(b),ResponseSet.from(c),ResponseSet.from(d)),
        ImmutableSet.<Response>of());


    final ResponseLinking l1 = linkings.get("l1");
    final ResponseLinking l2 = linkings.get("l2");
    final ResponseLinking l3 = linkings.get("l3");
    final ResponseLinking l4 = linkings.get("l4");
    final ResponseLinking l5 = linkings.get("l5");
    final ResponseLinking l6 = linkings.get("l6");
    final ResponseLinking l7 = linkings.get("l7");
    final ResponseLinking l8 = linkings.get("l8");
    final ResponseLinking l9 = linkings.get("l9");
    final ResponseLinking l10 = linkings.get("l10");
    final ResponseLinking l11 = linkings.get("l11");
    final ResponseLinking l12 = linkings.get("l12");
    final ResponseLinking l13 = linkings.get("l13");
  }

  /*
      key = { {a,b,c,d} }
      l1 EA=0.4 ; EAL: TP=1 FP=0 LS=0 final=0.125
      l2 EA=0.67 ; EAL: TP=2 FP=0 LS=0 final=0.25
      l3 EA=0.86 ; EAL: TP=3 FP=0 LS=0 final=0.375
      l4 EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      l5 EA=0.67 ; EAL: TP=2 FP=0 LS=1 final=0.375
      l6 EA=0.86 ; EAL: TP=3 FP=0 LS=2.4 final=0.675
      l7 EA=1.0 ; EAL: TP=4 FP=0 LS=2 final=0.75
      l8 EA=1.0 ; EAL: TP=4 FP=0 LS=4 final=1
      l9 EA=1.0 ; EAL: TP=4 FP=0 LS=2 final=0.75
      l10 EA=0.8 ; EAL: TP=4 FP=2 LS=4 final=0.9375
      l11 EA=0.8 ; EAL: TP=4 FP=2 LS=4 final=0.9375
      l12 EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
      l13 EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
   */
  private void oneGroupTest(final SystemExamples systemExamples) {
    final ImmutableMap<String, Response> responses = systemExamples.getResponses();
    final ImmutableMap<String, ResponseLinking> linkings = systemExamples.getLinkings();

    final Response a = responses.get("a");
    final Response b = responses.get("b");
    final Response c = responses.get("c");
    final Response d = responses.get("d");

    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(a,b,c,d));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(ImmutableSet.of(a,b,c,d), ImmutableSet.<Response>of(), coref);

    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a,b,c,d)),
        ImmutableSet.<Response>of());

    final ResponseLinking l1 = linkings.get("l1");
    final ResponseLinking l2 = linkings.get("l2");
    final ResponseLinking l3 = linkings.get("l3");
    final ResponseLinking l4 = linkings.get("l4");
    final ResponseLinking l5 = linkings.get("l5");
    final ResponseLinking l6 = linkings.get("l6");
    final ResponseLinking l7 = linkings.get("l7");
    final ResponseLinking l8 = linkings.get("l8");
    final ResponseLinking l9 = linkings.get("l9");
    final ResponseLinking l10 = linkings.get("l10");
    final ResponseLinking l11 = linkings.get("l11");
    final ResponseLinking l12 = linkings.get("l12");
    final ResponseLinking l13 = linkings.get("l13");
  }

  /*
      key = { {a,b} {c,d} }
      l1 EA=0.4 ; EAL: TP=1 FP=0 LS=0 final=0.125
      l2 EA=0.67 ; EAL: TP=2 FP=0 LS=0 final=0.25
      l3 EA=0.86 ; EAL: TP=3 FP=0 LS=0 final=0.375
      l4 EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      l5 EA=0.67 ; EAL: TP=2 FP=0 LS=2 final=0.5
      l6 EA=0.86 ; EAL: TP=3 FP=0 LS=4/3 final=0.5417
      l7 EA=1.0 ; EAL: TP=4 FP=0 LS=4 final=1
      l8 EA=1.0 ; EAL: TP=4 FP=0 LS=2 final=0.75
      l9 EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      l10 EA=0.8 ; EAL: TP=4 FP=2 LS=2 final=0.6875
      l11 EA=0.8 ; EAL: TP=4 FP=2 LS=2 final=0.6875
      l12 EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
      l13 EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
   */
  private void test1(final SystemExamples systemExamples) {
    final ImmutableMap<String, Response> responses = systemExamples.getResponses();
    final ImmutableMap<String, ResponseLinking> linkings = systemExamples.getLinkings();

    final Response a = responses.get("a");
    final Response b = responses.get("b");
    final Response c = responses.get("c");
    final Response d = responses.get("d");

    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(a,b,c,d));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(ImmutableSet.of(a,b,c,d), ImmutableSet.<Response>of(), coref);

    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a,b),ResponseSet.from(c,d)),
        ImmutableSet.<Response>of());

    final ResponseLinking l1 = linkings.get("l1");
    final ResponseLinking l2 = linkings.get("l2");
    final ResponseLinking l3 = linkings.get("l3");
    final ResponseLinking l4 = linkings.get("l4");
    final ResponseLinking l5 = linkings.get("l5");
    final ResponseLinking l6 = linkings.get("l6");
    final ResponseLinking l7 = linkings.get("l7");
    final ResponseLinking l8 = linkings.get("l8");
    final ResponseLinking l9 = linkings.get("l9");
    final ResponseLinking l10 = linkings.get("l10");
    final ResponseLinking l11 = linkings.get("l11");
    final ResponseLinking l12 = linkings.get("l12");
    final ResponseLinking l13 = linkings.get("l13");
  }

  /*
      key = { {a,b,c} {d} }
      l1 EA=0.4 ; EAL: TP=1 FP=0 LS=0 final=0.125
      l2 EA=0.67 ; EAL: TP=2 FP=0 LS=0 final=0.25
      l3 EA=0.86 ; EAL: TP=3 FP=0 LS=0 final=0.375
      l4 EA=1.0 ; EAL: TP=4 FP=0 LS=1 final=0.625
      l5 EA=0.67 ; EAL: TP=2 FP=0 LS=4/3 final=0.4167
      l6 EA=0.86 ; EAL: TP=3 FP=0 LS=3 final=0.75
      l7 EA=1.0 ; EAL: TP=4 FP=0 LS=4/3 final=0.6667
      l8 EA=1.0 ; EAL: TP=4 FP=0 LS=2.4 final=0.8
      l9 EA=1.0 ; EAL: TP=4 FP=0 LS=4/3 final=0.6667
      l10 EA=0.8 ; EAL: TP=4 FP=2 LS=2.4 final=0.7375
      l11 EA=0.8 ; EAL: TP=4 FP=2 LS=2.4 final=0.7375
      l12 EA=0.8 ; EAL: TP=4 FP=2 LS=1 final=0.5625
      l13 EA=0.8 ; EAL: TP=4 FP=2 LS=1 final=0.5625
   */
  private void test2(final SystemExamples systemExamples) {
    final ImmutableMap<String, Response> responses = systemExamples.getResponses();
    final ImmutableMap<String, ResponseLinking> linkings = systemExamples.getLinkings();

    final Response a = responses.get("a");
    final Response b = responses.get("b");
    final Response c = responses.get("c");
    final Response d = responses.get("d");

    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(a,b,c,d));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(ImmutableSet.of(a,b,c,d), ImmutableSet.<Response>of(), coref);

    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a,b,c),ResponseSet.from(d)),
        ImmutableSet.<Response>of());

    final ResponseLinking l1 = linkings.get("l1");
    final ResponseLinking l2 = linkings.get("l2");
    final ResponseLinking l3 = linkings.get("l3");
    final ResponseLinking l4 = linkings.get("l4");
    final ResponseLinking l5 = linkings.get("l5");
    final ResponseLinking l6 = linkings.get("l6");
    final ResponseLinking l7 = linkings.get("l7");
    final ResponseLinking l8 = linkings.get("l8");
    final ResponseLinking l9 = linkings.get("l9");
    final ResponseLinking l10 = linkings.get("l10");
    final ResponseLinking l11 = linkings.get("l11");
    final ResponseLinking l12 = linkings.get("l12");
    final ResponseLinking l13 = linkings.get("l13");
  }

  private static class SystemExamples {
    final ImmutableMap<String, Response> responses;
    final ImmutableMap<String, ResponseLinking> linkings;

    public static SystemExamples create() {
      final ImmutableMap<String, Response> responses = createResponses();
      final ImmutableMap<String, ResponseLinking> linkings = createLinkings(responses);

      return new SystemExamples(responses, linkings);
    }

    private SystemExamples(final ImmutableMap<String, Response> responses,
        final ImmutableMap<String, ResponseLinking> linkings) {
      this.responses = responses;
      this.linkings = linkings;
    }

    public ImmutableMap<String, Response> getResponses() {
      return responses;
    }

    public ImmutableMap<String, ResponseLinking> getLinkings() {
      return linkings;
    }

    private static ImmutableMap<String, Response> createResponses() {
      final ImmutableMap.Builder<String, Response> ret = ImmutableMap.builder();

      final Response a = dummyResponseOfType(CONFLICT, VICTIM, "a", KBPRealis.Actual);
      final Response b = dummyResponseOfType(CONFLICT, VICTIM, "b", KBPRealis.Actual);
      final Response c = dummyResponseOfType(CONFLICT, VICTIM, "c", KBPRealis.Actual);
      final Response d = dummyResponseOfType(CONFLICT, VICTIM, "d", KBPRealis.Actual);
      final Response e = dummyResponseOfType(CONFLICT, VICTIM, "e", KBPRealis.Actual);
      final Response f = dummyResponseOfType(CONFLICT, VICTIM, "f", KBPRealis.Actual);

      ret.put("a", a);
      ret.put("b", b);
      ret.put("c", c);
      ret.put("d", d);
      ret.put("e", e);
      ret.put("f", f);

      return ret.build();
    }

    /*
      System linkings:
      l1 = { {a} }
      l2 = { {a} {b} }
      l3 = { {a} {b} {c} }
      l4 = { {a} {b} {c} {d} }

      l5 = { {a,b} }
      l6 = { {a,b,c} }
      l7 = { {a,b} {c,d} }
      l8 = { {a,b,c,d} }
      l9 = { {a,c} {b,d} }

      l10 = { {a,b,c,d} {e} {f} }
      l11 = { {a,b,c,d} {e,f} }

      l12 = { {a} {b} {c} {d} {e,f} }
      l13 = { {a} {b} {c} {d} {e} {f} }
     */
    private static ImmutableMap<String, ResponseLinking> createLinkings(final ImmutableMap<String, Response> responses) {
      final ImmutableMap.Builder<String, ResponseLinking> ret = ImmutableMap.builder();

      final Response a = responses.get("a");
      final Response b = responses.get("b");
      final Response c = responses.get("c");
      final Response d = responses.get("d");
      final Response e = responses.get("e");
      final Response f = responses.get("f");

      final SystemOutput o1 = systemOutputFromResponses(ImmutableSet.of(a));
      final ResponseLinking l1 = ResponseLinking.from(o1.docId(),
          ImmutableSet.of(ResponseSet.from(a)),
          ImmutableSet.<Response>of());
      ret.put("l1", l1);

      final SystemOutput o2 = systemOutputFromResponses(ImmutableSet.of(a,b));
      final ResponseLinking l2 = ResponseLinking.from(o2.docId(),
          ImmutableSet.of(ResponseSet.from(a),ResponseSet.from(b)),
          ImmutableSet.<Response>of());
      ret.put("l2", l2);

      final SystemOutput o3 = systemOutputFromResponses(ImmutableSet.of(a,b,c));
      final ResponseLinking l3 = ResponseLinking.from(o3.docId(),
          ImmutableSet.of(ResponseSet.from(a),ResponseSet.from(b),ResponseSet.from(c)),
          ImmutableSet.<Response>of());
      ret.put("l3", l3);

      final SystemOutput o4 = systemOutputFromResponses(ImmutableSet.of(a,b,c,d));
      final ResponseLinking l4 = ResponseLinking.from(o4.docId(),
          ImmutableSet.of(ResponseSet.from(a),ResponseSet.from(b),ResponseSet.from(c),ResponseSet.from(d)),
          ImmutableSet.<Response>of());
      ret.put("l4", l4);

      final SystemOutput o5 = systemOutputFromResponses(ImmutableSet.of(a,b));
      final ResponseLinking l5 = ResponseLinking.from(o5.docId(),
          ImmutableSet.of(ResponseSet.from(a,b)),
          ImmutableSet.<Response>of());
      ret.put("l5", l5);

      final SystemOutput o6 = systemOutputFromResponses(ImmutableSet.of(a,b,c));
      final ResponseLinking l6 = ResponseLinking.from(o6.docId(),
          ImmutableSet.of(ResponseSet.from(a,b,c)),
          ImmutableSet.<Response>of());
      ret.put("l6", l6);

      final SystemOutput o7 = systemOutputFromResponses(ImmutableSet.of(a,b,c,d));
      final ResponseLinking l7 = ResponseLinking.from(o7.docId(),
          ImmutableSet.of(ResponseSet.from(a,b),ResponseSet.from(c,d)),
          ImmutableSet.<Response>of());
      ret.put("l7", l7);

      final SystemOutput o8 = systemOutputFromResponses(ImmutableSet.of(a,b,c,d));
      final ResponseLinking l8 = ResponseLinking.from(o8.docId(),
          ImmutableSet.of(ResponseSet.from(a,b,c,d)),
          ImmutableSet.<Response>of());
      ret.put("l8", l8);

      final SystemOutput o9 = systemOutputFromResponses(ImmutableSet.of(a,b,c,d));
      final ResponseLinking l9 = ResponseLinking.from(o9.docId(),
          ImmutableSet.of(ResponseSet.from(a,c),ResponseSet.from(b,d)),
          ImmutableSet.<Response>of());
      ret.put("l9", l9);

      final SystemOutput o10 = systemOutputFromResponses(ImmutableSet.of(a,b,c,d,e,f));
      final ResponseLinking l10 = ResponseLinking.from(o10.docId(),
          ImmutableSet.of(ResponseSet.from(a,b,c,d),ResponseSet.from(e),ResponseSet.from(f)),
          ImmutableSet.<Response>of());
      ret.put("l10", l10);

      final SystemOutput o11 = systemOutputFromResponses(ImmutableSet.of(a,b,c,d,e,f));
      final ResponseLinking l11 = ResponseLinking.from(o11.docId(),
          ImmutableSet.of(ResponseSet.from(a,b,c,d),ResponseSet.from(e,f)),
          ImmutableSet.<Response>of());
      ret.put("l11", l11);

      final SystemOutput o12 = systemOutputFromResponses(ImmutableSet.of(a,b,c,d,e,f));
      final ResponseLinking l12 = ResponseLinking.from(o12.docId(),
          ImmutableSet.of(ResponseSet.from(a),ResponseSet.from(b),ResponseSet.from(c),ResponseSet.from(d),ResponseSet.from(e,f)),
          ImmutableSet.<Response>of());
      ret.put("l12", l12);

      final SystemOutput o13 = systemOutputFromResponses(ImmutableSet.of(a,b,c,d,e,f));
      final ResponseLinking l13 = ResponseLinking.from(o13.docId(),
          ImmutableSet.of(ResponseSet.from(a),ResponseSet.from(b),ResponseSet.from(c),ResponseSet.from(d),ResponseSet.from(e),ResponseSet.from(f)),
          ImmutableSet.<Response>of());
      ret.put("l13", l13);


      return ret.build();
    }


  }


}
