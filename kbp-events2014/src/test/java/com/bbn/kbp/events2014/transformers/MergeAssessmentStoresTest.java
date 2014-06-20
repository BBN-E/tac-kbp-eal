package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.*;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MergeAssessmentStoresTest {
    int offsetCounter = 0;

    @Test
    public void TestMerge() {
        final Response a = responseWithCAS("a");
        final Response b = responseWithCAS("b");
        final Response c= responseWithCAS("c");
        final Response d = responseWithCAS("d");
        final Response e = responseWithCAS("e");
        final Response f = responseWithCAS("f");
        final Response g = responseWithCAS("g");
        final Response h = responseWithCAS("h");
        final Response i = responseWithCAS("i");
        final Response j = responseWithCAS("j");
        final Response k = responseWithCAS("k");
        final Response l = responseWithCAS("l");
        final Response m = responseWithCAS("m");

        final AnswerKey baseline = AnswerKey.from(docid,
                // assessed in baseline
                ImmutableSet.of(
                    withCoref(b, 200),
                    withCoref(f, 199),
                    withCoref(h, 198),
                    withCoref(i, 197),
                    withCoref(j, 197)
                ),
                // unassessed in baseline
                ImmutableSet.of(d, l)
                );

        final AnswerKey additional = AnswerKey.from(docid,
                ImmutableSet.of(
                        // a is unassessed in baseline, so
                        // it should go directly into merged
                        // with no coref
                        withNoCoref(a),
                        // b is assessed with coref in baseline,
                        // so it should take its coref from there
                        withCoref(b, 1),
                        // c is not present in baseline and has no
                        // clustermates, so it gets put in a new
                        // singleton cluster in merged
                        withCoref(c, 2),
                        // d & e are clustermates neither of which is
                        // assessed in the baseline, so they should go
                        // together into a new cluster in merged
                        withCoref(d, 3),
                        withCoref(e, 3),

                        // f is assessed with coref in the baseline and
                        // g is not, so g will get put in f's cluster in
                        // merged
                        withCoref(f, 4),
                        withCoref(g, 4),

                        // h, i, and j are assessed in baseline, h in
                        // one cluster and i & j in the other. k is not
                        // present in baseline. The coref assignments of
                        // baseline will be maintained in merged, and k
                        // will be placed in i & j's cluster as the
                        // dominant one
                        withCoref(h, 5),
                        withCoref(i, 5),
                        withCoref(j, 5),
                        withCoref(k, 5)
                ),
                ImmutableSet.of(m)
                );

        final AnswerKey merged = AnswerKey.from(docid,
                ImmutableSet.of(
                        withNoCoref(a),
                        withCoref(b, 200),
                        withCoref(c, 201),
                        withCoref(d, 202),
                        withCoref(e, 202),
                        withCoref(f, 199),
                        withCoref(g, 199),
                        withCoref(h, 198),
                        withCoref(i, 197),
                        withCoref(j, 197),
                        withCoref(k, 197)),
                ImmutableSet.of(l, m));

        final MergeAssessmentStores merger = MergeAssessmentStores.create();

        assertEquals(merged, merger.mergeAnswerKeys(baseline, additional));
    }

    private final Symbol docid = Symbol.from("docid");
    private final Symbol type = Symbol.from("type");
    private final Symbol role = Symbol.from("role");

    private AssessedResponse withCoref(Response r, int coref) {
        return AssessedResponse.from(r, assessmentWithCoref(coref));
    }

    private AssessedResponse withNoCoref(Response r) {
        return AssessedResponse.from(r, wrongAssessment());
    }

    private Response responseWithCAS(String CAS) {
        return Response.createFrom(docid, type, role,
                KBPString.from(CAS, CharOffsetSpan.fromOffsetsOnly(offsetCounter++, offsetCounter++)),
                CharOffsetSpan.fromOffsetsOnly(offsetCounter++, offsetCounter++),
                ImmutableSet.<CharOffsetSpan>of(), ImmutableSet.of(CharOffsetSpan.fromOffsetsOnly(offsetCounter++, offsetCounter++)),
                KBPRealis.Actual);
    }

    private ResponseAssessment wrongAssessment() {
        return ResponseAssessment.create(Optional.of(FieldAssessment.INCORRECT),
                Optional.<FieldAssessment>absent(), Optional.<FieldAssessment>absent(),
                Optional.<KBPRealis>absent(), Optional.<FieldAssessment>absent(),
                Optional.<Integer>absent(), Optional.<ResponseAssessment.MentionType>absent());
    }

    private ResponseAssessment assessmentWithCoref(Integer corefIdx) {
        return ResponseAssessment.create(Optional.of(FieldAssessment.CORRECT),
                Optional.of(FieldAssessment.CORRECT), Optional.of(FieldAssessment.CORRECT),
                Optional.of(KBPRealis.Actual), Optional.of(FieldAssessment.CORRECT),
                Optional.of(corefIdx), Optional.of(ResponseAssessment.MentionType.NOMINAL));
    }
}
