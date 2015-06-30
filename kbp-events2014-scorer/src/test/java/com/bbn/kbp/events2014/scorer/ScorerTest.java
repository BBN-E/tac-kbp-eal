package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.ScoringData;
import com.bbn.kbp.linking.EALScorer2015Style;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class ScorerTest {
  final EALScorer2015Style scorer = EALScorer2015Style.createWithoutPreprocessing();

  final Response a = dummyResponseOfType(CONFLICT, VICTIM, "a", KBPRealis.Actual);
  final Response b = dummyResponseOfType(CONFLICT, VICTIM, "b", KBPRealis.Actual);
  final Response c = dummyResponseOfType(CONFLICT, VICTIM, "c", KBPRealis.Actual);
  final Response d = dummyResponseOfType(CONFLICT, VICTIM, "d", KBPRealis.Actual);
  final Response e = dummyResponseOfType(CONFLICT, VICTIM, "e", KBPRealis.Actual);
  final Response f = dummyResponseOfType(CONFLICT, VICTIM, "f", KBPRealis.Actual);
  final Response g = dummyResponseOfType(CONFLICT, VICTIM, "g", KBPRealis.Actual);
  final Response h = dummyResponseOfType(CONFLICT, VICTIM, "h", KBPRealis.Actual);
  final Response i = dummyResponseOfType(CONFLICT, VICTIM, "i", KBPRealis.Actual);
  final Response aGeneric = dummyResponseOfType(CONFLICT, VICTIM, "a", KBPRealis.Generic);
  /*
  final ArgumentOutput output_A = systemOutputFromResponses(ImmutableSet.of(a));
  final ResponseLinking linking_A = ResponseLinking.from(output_A.docId(),
      ImmutableSet.of(ResponseSet.from(a)),
      ImmutableSet.<Response>of());

  final ArgumentOutput output_AB = systemOutputFromResponses(ImmutableSet.of(a, b));
  final ResponseLinking linking_A_B = ResponseLinking.from(output_AB.docId(),
      ImmutableSet.of(ResponseSet.from(a), ResponseSet.from(b)),
      ImmutableSet.<Response>of());

  final ArgumentOutput output_ABC = systemOutputFromResponses(ImmutableSet.of(a, b, c));
  final ResponseLinking linking_A_B_C = ResponseLinking.from(output_ABC.docId(),
      ImmutableSet.of(ResponseSet.from(a), ResponseSet.from(b), ResponseSet.from(c)),
      ImmutableSet.<Response>of());
  */
  final ArgumentOutput output_ABCD = systemOutputFromResponses(ImmutableSet.of(a, b, c, d));
  final ResponseLinking linking_A_B_C_D = ResponseLinking.from(output_ABCD.docId(),
      ImmutableSet.of(ResponseSet.from(a), ResponseSet.from(b), ResponseSet.from(c), ResponseSet.from(d)),
      ImmutableSet.<Response>of());
  /*
  final ResponseLinking linking_AB = ResponseLinking.from(output_AB.docId(),
      ImmutableSet.of(ResponseSet.from(a,b)),
      ImmutableSet.<Response>of());

  final ResponseLinking linking_ABC = ResponseLinking.from(output_ABC.docId(),
      ImmutableSet.of(ResponseSet.from(a,b,c)),
      ImmutableSet.<Response>of());
  */
  final ResponseLinking linking_AB_CD = ResponseLinking.from(output_ABCD.docId(),
      ImmutableSet.of(ResponseSet.from(a,b),ResponseSet.from(c,d)),
      ImmutableSet.<Response>of());

  final ResponseLinking linking_ABCD = ResponseLinking.from(output_ABCD.docId(),
      ImmutableSet.of(ResponseSet.from(a,b,c,d)),
      ImmutableSet.<Response>of());

  final ResponseLinking linking_AC_BD = ResponseLinking.from(output_ABCD.docId(),
      ImmutableSet.of(ResponseSet.from(a,c),ResponseSet.from(b,d)),
      ImmutableSet.<Response>of());
  /*
  final ArgumentOutput output_ABCDEF = systemOutputFromResponses(ImmutableSet.of(a, b, c, d, e, f));
  final ResponseLinking linking_ABCD_E_F = ResponseLinking.from(output_ABCDEF.docId(),
      ImmutableSet.of(ResponseSet.from(a, b, c, d), ResponseSet.from(e), ResponseSet.from(f)),
      ImmutableSet.<Response>of());

  final ResponseLinking linking_ABCD_EF = ResponseLinking.from(output_ABCDEF.docId(),
      ImmutableSet.of(ResponseSet.from(a,b,c,d),ResponseSet.from(e,f)),
      ImmutableSet.<Response>of());

  final ResponseLinking linking_A_B_C_D_EF = ResponseLinking.from(output_ABCDEF.docId(),
      ImmutableSet.of(ResponseSet.from(a),ResponseSet.from(b),ResponseSet.from(c),ResponseSet.from(d),ResponseSet.from(e,f)),
      ImmutableSet.<Response>of());

  final ResponseLinking linking_A_B_C_D_E_F = ResponseLinking.from(output_ABCDEF.docId(),
      ImmutableSet.of(ResponseSet.from(a),ResponseSet.from(b),ResponseSet.from(c),ResponseSet.from(d),ResponseSet.from(e),ResponseSet.from(f)),
      ImmutableSet.<Response>of());

  final ArgumentOutput output_ABG = systemOutputFromResponses(ImmutableSet.of(a, b, g));
  final ResponseLinking linking_AB_G = ResponseLinking.from(output_ABG.docId(),
      ImmutableSet.of(ResponseSet.from(a, b), ResponseSet.from(g)),
      ImmutableSet.<Response>of());
  */
  final ArgumentOutput output_ABDG = systemOutputFromResponses(ImmutableSet.of(a, b, d, g));
  final ResponseLinking linking_ABD_G = ResponseLinking.from(output_ABDG.docId(),
      ImmutableSet.of(ResponseSet.from(a,b,d),ResponseSet.from(g)),
      ImmutableSet.<Response>of());

  final ArgumentOutput output_ABCG = systemOutputFromResponses(ImmutableSet.of(a,b,c,g));
  final ResponseLinking linking_AB_C_G = ResponseLinking.from(output_ABCG.docId(),
      ImmutableSet.of(ResponseSet.from(a,b),ResponseSet.from(c),ResponseSet.from(g)),
      ImmutableSet.<Response>of());

  final ArgumentOutput output_GHI = systemOutputFromResponses(ImmutableSet.of(g,h,i));
  final ResponseLinking linking_G_H_I = ResponseLinking.from(output_GHI.docId(),
      ImmutableSet.of(ResponseSet.from(g),ResponseSet.from(h),ResponseSet.from(i)),
      ImmutableSet.<Response>of());

  final ArgumentOutput output_DEA = systemOutputFromResponses(ImmutableSet.of(d,e,a));
  final ResponseLinking linking_DE_DEA = ResponseLinking.from(output_DEA.docId(),
      ImmutableSet.of(ResponseSet.from(d,e),ResponseSet.from(d,e,a)),
      ImmutableSet.<Response>of());

  ////////////////////

  final ArgumentOutput output_C = systemOutputFromResponses(ImmutableSet.of(c));
  final ResponseLinking linking_C = ResponseLinking.from(output_C.docId(),
      ImmutableSet.of(ResponseSet.from(c)),
      ImmutableSet.<Response>of());
  /*
  final ArgumentOutput output_ABCE = systemOutputFromResponses(ImmutableSet.of(a,b,c,e));
  final ResponseLinking linking_ABCE = ResponseLinking.from(output_ABCE.docId(),
      ImmutableSet.of(ResponseSet.from(a,b,c,e)),
      ImmutableSet.<Response>of());
  */
  ////////////////////

  final ArgumentOutput output_AGeneric_BCE = systemOutputFromResponses(ImmutableSet.of(aGeneric,b,c,e));
  final ResponseLinking linking_AGenericBCE = ResponseLinking.from(output_AGeneric_BCE.docId(),
      ImmutableSet.of(ResponseSet.from(aGeneric,b,c,e)),
      ImmutableSet.<Response>of());


  @Before
  public void setUp() {

  }


  // tests

  @Test
  public void sampleTest() {
    final Response x = dummyResponseOfType(CONFLICT, VICTIM, "x", KBPRealis.Actual);
    final Response y = dummyResponseOfType(CONFLICT, VICTIM, "y", KBPRealis.Actual);
    final Response z = dummyResponseOfType(CONFLICT, VICTIM, "z", KBPRealis.Actual);
    final Response a = dummyResponseOfType(CONFLICT, VICTIM, "a", KBPRealis.Actual);

    final ArgumentOutput argumentOutput = systemOutputFromResponses(ImmutableSet.of(x, y, z));
    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(x, y, z, a));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(
        ImmutableSet.of(x, y, a), ImmutableSet.of(z), coref);

    final ResponseLinking systemResponseLinking = ResponseLinking.from(argumentOutput.docId(),
        ImmutableSet.of(ResponseSet.from(x, y, z)), ImmutableSet.<Response>of());
    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(x, y, a)), ImmutableSet.<Response>of());

    final EALScorer2015Style.Result score =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(argumentOutput)
            .withSystemLinking(systemResponseLinking).build());

    assertEquals(1.75, score.unscaledArgumentScore(), .001);
    assertEquals(4.0/3, score.unscaledLinkingScore(), .001);
    assertEquals(37.0/72.0, score.scaledScore(), .001);
  }


  /*
      key = { {a} {b} {c} {d} }
      //linking_A EA=0.4 ; EAL: TP=1 FP=0 LS=1 final=0.25
      //linking_A_B EA=0.67 ; EAL: TP=2 FP=0 LS=2 final=0.5
      //linking_A_B_C EA=0.86 ; EAL: TP=3 FP=0 LS=3 final=0.75
      linking_A_B_C_D EA=1.0 ; EAL: TP=4 FP=0 LS=4 final=1
      //linking_AB EA=0.67 ; EAL: TP=2 FP=0 LS=0 final=0.25
      //linking_ABC EA=0.86 ; EAL: TP=3 FP=0 LS=0 final=0.375
      //linking_AB_CD EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      linking_ABCD EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      //linking_AC_BD EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      //linking_ABCD_E_F EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
      //linking_ABCD_EF EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
      //linking_A_B_C_D_EF EA=0.8 ; EAL: TP=4 FP=2 LS=4 final=0.9375
      //linking_A_B_C_D_E_F EA=0.8 ; EAL: TP=4 FP=2 LS=4 final=0.9375
  */
  @Test
  public void singletonTest() {
    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(a, b, c, d));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(ImmutableSet.of(a, b, c, d),
        ImmutableSet.<Response>of(), coref);

    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a),ResponseSet.from(b),ResponseSet.from(c),ResponseSet.from(d)),
        ImmutableSet.<Response>of());

    //final ResponseLinking l1 = this.linking_A;
    //final ResponseLinking l2 = this.linking_A_B;
    //final ResponseLinking l3 = this.linking_A_B_C;

    //final ResponseLinking l4 = this.linking_A_B_C_D;
    final EALScorer2015Style.Result score_A_B_C_D =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_ABCD)
            .withSystemLinking(this.linking_A_B_C_D).build()); // singleton key, singleton system
    assertEquals(4.0, score_A_B_C_D.unscaledArgumentScore(), .001);
    assertEquals(4.0, score_A_B_C_D.unscaledLinkingScore(), .001);
    assertEquals(1.0, score_A_B_C_D.scaledScore(), .001);

    //final ResponseLinking l5 = this.linking_AB;
    //final ResponseLinking l6 = this.linking_ABC;
    //final ResponseLinking l7 = this.linking_AB_CD;

    //final ResponseLinking l8 = this.linking_ABCD;
    final EALScorer2015Style.Result score_ABCD =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_ABCD)
            .withSystemLinking(this.linking_ABCD).build()); // singleton key, group system
    assertEquals(4.0, score_ABCD.unscaledArgumentScore(), .001);
    assertEquals(0, score_ABCD.unscaledLinkingScore(), .001);
    assertEquals(0.5, score_ABCD.scaledScore(), .001);

    //final ResponseLinking l9 = this.linking_AC_BD;
    //final ResponseLinking l10 = this.linking_ABCD_E_F;
    //final ResponseLinking l11 = this.linking_ABCD_EF;
    //final ResponseLinking l12 = this.linking_A_B_C_D_EF;
    //final ResponseLinking l13 = this.linking_A_B_C_D_E_F;
  }

  /*
      key = { {a,b,c,d} }
      //linking_A EA=0.4 ; EAL: TP=1 FP=0 LS=0 final=0.125
      //l2 EA=0.67 ; EAL: TP=2 FP=0 LS=0 final=0.25
      //linking_A_B_C EA=0.86 ; EAL: TP=3 FP=0 LS=0 final=0.375
      linking_A_B_C_D EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      //linking_AB EA=0.67 ; EAL: TP=2 FP=0 LS=1 final=0.375
      //linking_ABC EA=0.86 ; EAL: TP=3 FP=0 LS=2.4 final=0.675
      //linking_AB_CD EA=1.0 ; EAL: TP=4 FP=0 LS=2 final=0.75
      linking_ABCD EA=1.0 ; EAL: TP=4 FP=0 LS=4 final=1
      //linking_AC_BD EA=1.0 ; EAL: TP=4 FP=0 LS=2 final=0.75
      //linking_ABCD_E_F EA=0.8 ; EAL: TP=4 FP=2 LS=4 final=0.9375
      //linking_ABCD_EF EA=0.8 ; EAL: TP=4 FP=2 LS=4 final=0.9375
      //linking_A_B_C_D_EF EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
      //linking_A_B_C_D_E_F EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
   */
  @Test
  public void oneGroupTest() {
    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(a,b,c,d));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(ImmutableSet.of(a,b,c,d), ImmutableSet.<Response>of(), coref);

    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a,b,c,d)),
        ImmutableSet.<Response>of());

    //final ResponseLinking l1 = this.linking_A;
    //final ResponseLinking l2 = this.linking_A_B;
    //final ResponseLinking l3 = this.linking_A_B_C;

    //final ResponseLinking l4 = this.linking_A_B_C_D;
    final EALScorer2015Style.Result score_A_B_C_D =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_ABCD).withSystemLinking(this.linking_A_B_C_D).build()); // group key, singleton system
    assertEquals(4.0, score_A_B_C_D.unscaledArgumentScore(), .001);
    assertEquals(0, score_A_B_C_D.unscaledLinkingScore(), .001);
    assertEquals(0.5, score_A_B_C_D.scaledScore(), .001);

    //final ResponseLinking l5 = this.linking_AB;
    //final ResponseLinking l6 = this.linking_ABC;
    //final ResponseLinking l7 = this.linking_AB_CD;

    //final ResponseLinking l8 = this.linking_ABCD;
    final EALScorer2015Style.Result score_ABCD =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_ABCD)
            .withSystemLinking(this.linking_ABCD).build()); // group key, group system
    assertEquals(4.0, score_ABCD.unscaledArgumentScore(), .001);
    assertEquals(4.0, score_ABCD.unscaledLinkingScore(), .001);
    assertEquals(1.0, score_ABCD.scaledScore(), .001);

    //final ResponseLinking l9 = this.linking_AC_BD;
    //final ResponseLinking l10 = this.linking_ABCD_E_F;
    //final ResponseLinking l11 = this.linking_ABCD_EF;
    //final ResponseLinking l12 = this.linking_A_B_C_D_EF;
    //final ResponseLinking l13 = this.linking_A_B_C_D_E_F;
  }

  /*
      key = { {a,b} {c,d} }
      //linking_A EA=0.4 ; EAL: TP=1 FP=0 LS=0 final=0.125
      //l2 EA=0.67 ; EAL: TP=2 FP=0 LS=0 final=0.25
      //linking_A_B_C EA=0.86 ; EAL: TP=3 FP=0 LS=0 final=0.375
      //linking_A_B_C_D EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      //linking_AB EA=0.67 ; EAL: TP=2 FP=0 LS=2 final=0.5
      //linking_ABC EA=0.86 ; EAL: TP=3 FP=0 LS=4/3 final=0.5417
      linking_AB_CD EA=1.0 ; EAL: TP=4 FP=0 LS=4 final=1
      //linking_ABCD EA=1.0 ; EAL: TP=4 FP=0 LS=2 final=0.75
      linking_AC_BD EA=1.0 ; EAL: TP=4 FP=0 LS=0 final=0.5
      //linking_ABCD_E_F EA=0.8 ; EAL: TP=4 FP=2 LS=2 final=0.6875
      //linking_ABCD_EF EA=0.8 ; EAL: TP=4 FP=2 LS=2 final=0.6875
      //linking_A_B_C_D_EF EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
      //linking_A_B_C_D_E_F EA=0.8 ; EAL: TP=4 FP=2 LS=0 final=0.4375
   */
  @Test
  public void test1() {
    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(a, b, c, d));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(ImmutableSet.of(a, b, c, d),
        ImmutableSet.<Response>of(), coref);

    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a, b), ResponseSet.from(c, d)),
        ImmutableSet.<Response>of());

    //final ResponseLinking l1 = this.linking_A;
    //final ResponseLinking l2 = this.linking_A_B;
    //final ResponseLinking l3 = this.linking_A_B_C;
    //final ResponseLinking l4 = this.linking_A_B_C_D;
    //final ResponseLinking l5 = this.linking_AB;
    //final ResponseLinking l6 = this.linking_ABC;

    //final ResponseLinking l7 = this.linking_AB_CD;
    final EALScorer2015Style.Result score_AB_CD =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_ABCD)
            .withSystemLinking(this.linking_AB_CD).build()); // tuples all correct, and clustering all correct
    assertEquals(4.0, score_AB_CD.unscaledArgumentScore(), .001);
    assertEquals(4.0, score_AB_CD.unscaledLinkingScore(), .001);
    assertEquals(1.0, score_AB_CD.scaledScore(), .001);

    //final ResponseLinking l8 = this.linking_ABCD;

    //final ResponseLinking l9 = this.linking_AC_BD;
    final EALScorer2015Style.Result score_AC_BD =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_ABCD)
            .withSystemLinking(this.linking_AC_BD).build()); // tuples all correct, but clustering wrong
    assertEquals(4.0, score_AC_BD.unscaledArgumentScore(), .001);
    assertEquals(0, score_AC_BD.unscaledLinkingScore(), .001);
    assertEquals(0.5, score_AC_BD.scaledScore(), .001);

    //final ResponseLinking l10 = this.linking_ABCD_E_F;
    //final ResponseLinking l11 = this.linking_ABCD_EF;
    //final ResponseLinking l12 = this.linking_A_B_C_D_EF;
    //final ResponseLinking l13 = this.linking_A_B_C_D_E_F;
  }

  private ArgumentOutput systemOutputFrom(final ResponseLinking linking) {
    return ArgumentOutput.createWithConstantScore(linking.docID(), linking.allResponses(), 1.0);
  }

  /*
      key = { {a,b,c} {d} }
      l1 EA=0.4 ; EAL: TP=1 FP=0 LS=0 final=0.125
      l2 EA=0.67 ; EAL: TP=2 FP=0 LS=0 final=0.25
      linking_A_B_C EA=0.86 ; EAL: TP=3 FP=0 LS=0 final=0.375
      linking_A_B_C_D EA=1.0 ; EAL: TP=4 FP=0 LS=1 final=0.625
      linking_AB EA=0.67 ; EAL: TP=2 FP=0 LS=4/3 final=0.4167
      linking_ABC EA=0.86 ; EAL: TP=3 FP=0 LS=3 final=0.75
      linking_AB_CD EA=1.0 ; EAL: TP=4 FP=0 LS=4/3 final=0.6667
      linking_ABCD EA=1.0 ; EAL: TP=4 FP=0 LS=2.4 final=0.8
      linking_AC_BD EA=1.0 ; EAL: TP=4 FP=0 LS=4/3 final=0.6667
      linking_ABCD_E_F EA=0.8 ; EAL: TP=4 FP=2 LS=2.4 final=0.7375
      linking_ABCD_EF EA=0.8 ; EAL: TP=4 FP=2 LS=2.4 final=0.7375
      linking_A_B_C_D_EF EA=0.8 ; EAL: TP=4 FP=2 LS=1 final=0.5625
      linking_A_B_C_D_E_F EA=0.8 ; EAL: TP=4 FP=2 LS=1 final=0.5625
  @Test
  public void test2() {
    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(a,b,c,d));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(ImmutableSet.of(a,b,c,d), ImmutableSet.<Response>of(), coref);

    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a,b,c),ResponseSet.from(d)),
        ImmutableSet.<Response>of());

    final ResponseLinking l1 = this.linking_A;
    final ResponseLinking l2 = this.linking_A_B;
    final ResponseLinking l3 = this.linking_A_B_C;
    final ResponseLinking l4 = this.linking_A_B_C_D;
    final ResponseLinking l5 = this.linking_AB;
    final ResponseLinking l6 = this.linking_ABC;
    final ResponseLinking l7 = this.linking_AB_CD;
    final ResponseLinking l8 = this.linking_ABCD;
    final ResponseLinking l9 = this.linking_AC_BD;
    final ResponseLinking l10 = this.linking_ABCD_E_F;
    final ResponseLinking l11 = this.linking_ABCD_EF;
    final ResponseLinking l12 = this.linking_A_B_C_D_EF;
    final ResponseLinking l13 = this.linking_A_B_C_D_E_F;
  }
  */

  /*
      key = { {a,b,c} {d,e} {f} }
      //linking_AB_G EA=4/9 ; EAL: TP=2 FP=1 LS=4/3 final=0.2569
      linking_ABD_G EA=0.6 ; EAL: TP=3 FP=1 LS=1 final=0.3125
      linking_AB_C_G EA=0.6 ; EAL: TP=3 FP=1 LS=4/3 final=0.3403
      linking_G_H_I EA=0 ; EAL: TP=0 FP=3 LS=0 final=0
      linking_DE_DEA EA=2/3 ; EAL: TP=3 FP=0 LS=4/3 final=0.3333
   */
  @Test
  public void test3() {
    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(a, b, c, d, e, f, g, h, i));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(
        ImmutableSet.of(a, b, c, d, e, f), ImmutableSet.<Response>of(g, h, i), coref);

    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a, b, c), ResponseSet.from(d, e), ResponseSet.from(f)),
        ImmutableSet.<Response>of());

    //final ResponseLinking l14 = this.linking_AB_G;
    //final ResponseLinking l15 = this.linking_ABD_G;    // overlink { {a,b,d} {g} }
    final EALScorer2015Style.Result score_ABD_G =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_ABDG)
                .withSystemLinking(this.linking_ABD_G).build());
    assertEquals(2.75, score_ABD_G.unscaledArgumentScore(), .001);
    // for each key item:
    // for item 'a', keyNeighbors=(b,c) sysNeighbors=(b,d), hence R=1/2 P=1/2, F=1/2
    // for item 'b', keyNeighbors=(a,c) sysNeighbors=(a,d), hence R=1/2 P=1/2, F=1/2
    // for item 'd', keyNeighbors=(e) sysNeighbors=(a,b), hence R=0 P=0 F=0
    // key items c, e, f do not appear in system, hence no contribution to linkingSumF1
    // Therefore, 1/2 + 1/2 + 0 = 1.0 unscaledLinkingScore
    assertEquals(1.0, score_ABD_G.unscaledLinkingScore(), .001);
    assertEquals(0.3125, score_ABD_G.scaledScore(), .001);

    final EALScorer2015Style.Result score_AB_C_G =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_ABCG)
            .withSystemLinking(this.linking_AB_C_G).build());
    assertEquals(2.75, score_AB_C_G.unscaledArgumentScore(), .001);
    assertEquals(4.0/3, score_AB_C_G.unscaledLinkingScore(), .001);
    // for each key item:
    // for item 'a', keyNeighbors=(b,c) sysNeighbors=(b), hence R=1/2 P=1, F=2/3
    // for item 'b', keyNeighbors=(a,c) sysNeighbors=(a), hence R=1/2 P=1, F=2/3
    // for item 'c', keyNeighbors=(a,b) sysNeighbors=(), hence F=0
    // items d,e,f do not appear in system
    // Therefore, 2/3 + 2/3 = 4/3 unscaledLinkingScore
    assertEquals(0.3403, score_AB_C_G.scaledScore(), .001);

    final EALScorer2015Style.Result score_G_H_I =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_GHI)
            .withSystemLinking(this.linking_G_H_I).build());
    assertEquals(-0.75, score_G_H_I.unscaledArgumentScore(), .001);
    assertEquals(0, score_G_H_I.unscaledLinkingScore(), .001);

    assertEquals(-.0625, score_G_H_I.scaledScore(), .001);

    // duplicate system responses { {d,e} {d,e,a} }
    final EALScorer2015Style.Result score_DE_DEA =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_DEA)
            .withSystemLinking(this.linking_DE_DEA).build());
    assertEquals(3.0, score_DE_DEA.unscaledArgumentScore(), .001);
    assertEquals(4.0/3, score_DE_DEA.unscaledLinkingScore(), .001);
    // for each key item:
    // for item 'a', keyNeighbors=(b,c) sysNeighbors=(d,e), F=0
    // for item 'd', keyNeighbors=(e) sysNeighbors=(a,e), R=1 P=1/2, F=2/3
    // for item 'e', keyNeighbors=(d) sysNeighbors=(a,d), R=1 P=1/2, F=2/3
    // items b,c,f do not appear in system
    // Therefore, 0 + 2/3 + 2/3 = 4/3 unscaledLinkingScore
    assertEquals(13.0/36, score_DE_DEA.scaledScore(), .001);
  }

  /*
      key = { {a,b,c} {c,e} {c} }
      linking_C EA=0.4 ; EAL: TP=1 FP=0 LS=0 final=0.125
      //linking_ABCE EA=1 ; EAL: TP=4 FP=0 LS=2.6 final=0.825
   */
  @Test
  public void test4() {
    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(a,b,c,e));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(ImmutableSet.of(a,b,c,e), ImmutableSet.<Response>of(), coref);

    // c is involved in multiple clusters
    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(a,b,c),ResponseSet.from(c,e),ResponseSet.from(c)),
        ImmutableSet.<Response>of());

    final ResponseLinking l19 = this.linking_C;     // although C is duplicated in key, but TP should just be 1
    final EALScorer2015Style.Result score_C =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
            .withSystemOutput(this.output_C)
            .withSystemLinking(this.linking_C).build());
    assertEquals(1.0, score_C.unscaledArgumentScore(), .001);
    assertEquals(0, score_C.unscaledLinkingScore(), .001);
    assertEquals(0.125, score_C.scaledScore(), .001);

    //final ResponseLinking l20 = this.linking_ABCE;  // system gets all EA tuples correct
  }

  /*
      key = { {b,c} {c,d} {d,e} }
      linking_AGenericBCE EA=0.75 ; EAL: TP=3 FP=0 LS=7/6 final=0.5208
   */
  @Test
  public void testGeneric() {
    final CorefAnnotation coref = allSingletonsCoref(ImmutableSet.of(b,c,d,e,aGeneric));
    final AnswerKey answerKey = makeAnswerKeyFromCorrectAndIncorrect(ImmutableSet.of(aGeneric, b,c,d,e),
        ImmutableSet.<Response>of(), coref);

    final ResponseLinking goldResponseLinking = ResponseLinking.from(answerKey.docId(),
        ImmutableSet.of(ResponseSet.from(b,c),ResponseSet.from(c,d),ResponseSet.from(d,e)),
        ImmutableSet.<Response>of());

    final ResponseLinking l21 = this.linking_AGenericBCE;    // { {aGeneric,b,c,e} } , aGeneric should be removed/ignored? by scorer
    final EALScorer2015Style.Result score_AGenericBCE =
        scorer.score(ScoringData.builder().withAnswerKey(answerKey)
            .withReferenceLinking(goldResponseLinking)
    .withSystemOutput(this.output_AGeneric_BCE).withSystemLinking(this.linking_AGenericBCE).build());
    assertEquals(4.0, score_AGenericBCE.unscaledArgumentScore(), .001);
    assertEquals(7.0/6, score_AGenericBCE.unscaledLinkingScore(), .001);
    assertEquals(0.5458333, score_AGenericBCE.scaledScore(), .001);
  }

  // utility methods

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


  private static CorefAnnotation allSingletonsCoref(Iterable<Response> responses) {
    final Random rng = new Random(0);
    final CorefAnnotation.Builder corefBuilder = CorefAnnotation.strictBuilder(DOC);
    for (final Response response : responses) {
      corefBuilder.putInNewRandomCluster(response.canonicalArgument(), rng);
    }
    return corefBuilder.build();
  }

  private static ArgumentOutput systemOutputFromResponses(Iterable<Response> responses) {
    final ImmutableSet.Builder<Scored<Response>> scoredResponses = ImmutableSet.builder();
    for (final Response response : responses) {
      scoredResponses.add(Scored.from(response, 1.0));
    }
    return ArgumentOutput.from(DOC, scoredResponses.build());
  }


}
