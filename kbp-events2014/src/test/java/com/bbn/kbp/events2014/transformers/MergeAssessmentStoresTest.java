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

        final CorefAnnotation baselineCoref = CorefAnnotation.strictBuilder(docid)
                .corefCAS(b.canonicalArgument(), 200)
                .corefCAS(f.canonicalArgument(), 199)
                .corefCAS(h.canonicalArgument(), 198)
                .corefCAS(i.canonicalArgument(), 197)
                .corefCAS(j.canonicalArgument(), 197)
                .addUnannotatedCAS(d.canonicalArgument())
                .addUnannotatedCAS(l.canonicalArgument())
                .build();

        final AnswerKey baseline = AnswerKey.from(docid,
                // assessed in baseline
                ImmutableSet.of(
                    withCorrectAssessment(b),
                    withCorrectAssessment(f),
                    withCorrectAssessment(h),
                    withCorrectAssessment(i),
                    withCorrectAssessment(j)
                ),
                // unassessed in baseline
                ImmutableSet.of(d, l), baselineCoref);

        final CorefAnnotation additionalCoref = CorefAnnotation.strictBuilder(docid)
                // a is unassessed in baseline, so
                // it should go directly into merged
                // with no coref
                .addUnannotatedCAS(a.canonicalArgument())
                // b is assessed with coref in baseline,
                // so it should take its coref from there
                .corefCAS(b.canonicalArgument(), 1)
                // c is not present in baseline and has no
                // clustermates, so it gets put in a new
                // singleton cluster in merged
                .corefCAS(c.canonicalArgument(), 2)
                // d & e are clustermates neither of which is
                // assessed in the baseline, so they should go
                // together into a new cluster in merged
                .corefCAS(d.canonicalArgument(), 3)
                .corefCAS(e.canonicalArgument(), 3)
                // f is assessed with coref in the baseline and
                // g is not, so g will get put in f's cluster in
                // merged
                .corefCAS(f.canonicalArgument(), 4)
                .corefCAS(g.canonicalArgument(), 4)
                // h, i, and j are assessed in baseline, h in
                // one cluster and i & j in the other. k is not
                // present in baseline. The coref assignments of
                // baseline will be maintained in merged, and k
                // will be placed in i & j's cluster as the
                // dominant one
                .corefCAS(h.canonicalArgument(), 5)
                .corefCAS(i.canonicalArgument(), 5)
                .corefCAS(j.canonicalArgument(), 5)
                .corefCAS(k.canonicalArgument(), 5)
                .addUnannotatedCAS(m.canonicalArgument())
                .build();

        final AnswerKey additional = AnswerKey.from(docid,
                ImmutableSet.of(
                        withWrongAssessment(a),
                        withCorrectAssessment(b),
                        withCorrectAssessment(c),
                        withCorrectAssessment(d),
                        withCorrectAssessment(e),
                        withCorrectAssessment(f),
                        withCorrectAssessment(g),
                        withCorrectAssessment(h),
                        withCorrectAssessment(i),
                        withCorrectAssessment(j),
                        withCorrectAssessment(k)
                ),
                ImmutableSet.of(m), additionalCoref
                );

        final CorefAnnotation referenceCoref = CorefAnnotation.strictBuilder(docid)
                .addUnannotatedCAS(a.canonicalArgument())
                .addUnannotatedCAS(l.canonicalArgument())
                .addUnannotatedCAS(m.canonicalArgument())
                .corefCAS(b.canonicalArgument(), 200)
                .corefCAS(c.canonicalArgument(), 201)
                .corefCAS(d.canonicalArgument(), 202)
                .corefCAS(e.canonicalArgument(), 202)
                .corefCAS(f.canonicalArgument(), 199)
                .corefCAS(g.canonicalArgument(), 199)
                .corefCAS(h.canonicalArgument(), 198)
                .corefCAS(i.canonicalArgument(), 197)
                .corefCAS(j.canonicalArgument(), 197)
                .corefCAS(k.canonicalArgument(), 197)
                .build();

        final AnswerKey merged = AnswerKey.from(docid,
                ImmutableSet.of(
                        withWrongAssessment(a),
                        withCorrectAssessment(b),
                        withCorrectAssessment(c),
                        withCorrectAssessment(d),
                        withCorrectAssessment(e),
                        withCorrectAssessment(f),
                        withCorrectAssessment(g),
                        withCorrectAssessment(h),
                        withCorrectAssessment(i),
                        withCorrectAssessment(j),
                        withCorrectAssessment(k)),
                ImmutableSet.of(l, m), referenceCoref);

        final MergeAssessmentStores merger = MergeAssessmentStores.create();

        assertEquals(merged, merger.mergeAnswerKeys(baseline, additional));
    }

    private final Symbol docid = Symbol.from("docid");
    private final Symbol type = Symbol.from("type");
    private final Symbol role = Symbol.from("role");

    private AssessedResponse withCorrectAssessment(Response r) {
        return AssessedResponse.from(r, correctAssessment());
    }

    private AssessedResponse withWrongAssessment(Response r) {
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
                Optional.<ResponseAssessment.MentionType>absent());
    }

    private ResponseAssessment correctAssessment() {
        return ResponseAssessment.create(Optional.of(FieldAssessment.CORRECT),
                Optional.of(FieldAssessment.CORRECT), Optional.of(FieldAssessment.CORRECT),
                Optional.of(KBPRealis.Actual), Optional.of(FieldAssessment.CORRECT),
                Optional.of(ResponseAssessment.MentionType.NOMINAL));
    }
}
