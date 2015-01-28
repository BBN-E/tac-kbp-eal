package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.SystemOutput;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by rgabbard on 6/4/14.
 */
public class OnlyMostSpecificTemporalTest {

  @Test
  public void testOnlyMostSpecificTemporal() {
    final Symbol d = Symbol.from("foo");
    final Symbol type = Symbol.from("type");
    final Symbol role = Symbol.from("Time");

    final KBPString d19821231 = KBPString.from("1982-12-31", CharOffsetSpan.fromOffsetsOnly(0, 1));
    final KBPString d198212XX = KBPString.from("1982-12-XX", CharOffsetSpan.fromOffsetsOnly(0, 1));
    // same as previous, different offsets
    final KBPString d198212XXOther =
        KBPString.from("1982-12-XX", CharOffsetSpan.fromOffsetsOnly(2, 3));
    final CharOffsetSpan bf1 = CharOffsetSpan.fromOffsetsOnly(0, 1);
    final CharOffsetSpan pj1 = CharOffsetSpan.fromOffsetsOnly(0, 10);

    final KBPString d19840304 =
        KBPString.from("1984-03-84", CharOffsetSpan.fromOffsetsOnly(20, 21));
    final KBPString d198403XX =
        KBPString.from("1984-03-XX", CharOffsetSpan.fromOffsetsOnly(20, 21));
    final CharOffsetSpan bf2 = CharOffsetSpan.fromOffsetsOnly(20, 21);
    final CharOffsetSpan pj2 = CharOffsetSpan.fromOffsetsOnly(20, 30);

    final CorefAnnotation corefAnnotation = CorefAnnotation.strictBuilder(d)
        .corefCAS(d19821231, 1)
        .corefCAS(d19840304, 2).build();

    // this answer key has one correct temporal role (1982-12-31)
    // and one incorrect (1984-03-04)
    final AnswerKey answerKey = AnswerKey.from(d,
        ImmutableList.of(
            AssessedResponse.from(
                Response.createFrom(d, type, role, d19821231, bf1,
                    ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(pj1), KBPRealis.Actual),
                ResponseAssessment.create(Optional.of(FieldAssessment.CORRECT),
                    Optional.of(FieldAssessment.CORRECT), Optional.of(FieldAssessment.CORRECT),
                    Optional.of(KBPRealis.Actual), Optional.of(FieldAssessment.CORRECT),
                    Optional.of(ResponseAssessment.MentionType.NOMINAL)
                )),
            AssessedResponse.from(
                Response.createFrom(d, type, role, d19840304, bf2,
                    ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(pj2), KBPRealis.Actual),
                ResponseAssessment.create(Optional.of(FieldAssessment.CORRECT),
                    Optional.of(FieldAssessment.CORRECT), Optional.of(FieldAssessment.INCORRECT),
                    Optional.of(KBPRealis.Actual), Optional.of(FieldAssessment.CORRECT),
                    Optional.of(ResponseAssessment.MentionType.NOMINAL)))),
        ImmutableList.<Response>of(), corefAnnotation);

    final SystemOutput systemOutput = SystemOutput.from(d,
        ImmutableList.of(
            Scored.from(Response.createFrom(d, type, role, d198212XX, bf1,
                ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(pj1), KBPRealis.Actual), 1.0),
            Scored.from(Response.createFrom(d, type, role, d198212XXOther, bf2,
                ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(pj2), KBPRealis.Actual), 1.0),
            Scored.from(Response.createFrom(d, type, role, d19821231, bf1,
                ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(pj1), KBPRealis.Actual), 1.0),
            Scored.from(Response.createFrom(d, type, role, d198403XX, bf2,
                ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(pj2), KBPRealis.Actual), 1.0)));

    // 1983-03-XX is not removed because 1984-03-04 in answer key
    // is incorrect
    // the full 1982 case is not removed because it is not less specific
    // both others are removed, illustrating that the CAS offsets don't matter, only the string
    final SystemOutput reference = SystemOutput.from(d,
        ImmutableList.of(
            Scored.from(Response.createFrom(d, type, role, d19821231, bf1,
                ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(pj1), KBPRealis.Actual), 1.0),
            Scored.from(Response.createFrom(d, type, role, d198403XX, bf2,
                ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(pj2), KBPRealis.Actual), 1.0)));

    final OnlyMostSpecificTemporal temporalFilter =
        OnlyMostSpecificTemporal.forAnswerKey(answerKey);
    final SystemOutput filteredOutput = temporalFilter.apply(systemOutput);

    assertEquals(reference, filteredOutput);
  }
}
