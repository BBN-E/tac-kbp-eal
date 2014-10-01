package com.bbn.kbp.events2014.linking;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.*;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import static com.bbn.kbp.events2014.KBPEATestUtils.*;
import static org.junit.Assert.assertEquals;


public class NaiveProjectorTest {
    @Test
    public void naiveProjectorTest() {
        final Symbol docID = Symbol.from("docID");
        final KBPString A = kbpString("a");
        final KBPString B = kbpString("b");
        final KBPString C = kbpString("c");
        final KBPString D = kbpString("d");
        final KBPString E = kbpString("e");
        final KBPString F = kbpString("f");
        final KBPString G = kbpString("g");
        final KBPString H = kbpString("h");
        final KBPString I = kbpString("i");
        final KBPString J = kbpString("j");
        final KBPString K = kbpString("k");

        // Source coref clusters: 1={A,B,C}, 2={D,E}, 3={F,G}, 4={H,I}, 5={J}
        final CorefAnnotation sourceCoref = CorefAnnotation.strictBuilder(docID)
                .corefCAS(A, 1)
                .corefCAS(B, 1)
                .corefCAS(C, 1)
                .corefCAS(D, 2)
                .corefCAS(E, 2)
                .corefCAS(F, 3)
                .corefCAS(G, 3)
                .corefCAS(H, 4)
                .corefCAS(I, 4)
                .corefCAS(J, 5).build();

        // source event frames. We only have one response for each CAS, all types, roles, etc.
        // the same, for simplicity.  {1,4}, {2,4}, {3,4}, {4}, {5}
        final EventArgumentLinking sourceArgumentLinking = EventArgumentLinking.create(
                docID, ImmutableSet.of(
                        TypeRoleFillerRealisSet.from(ImmutableSet.of(
                                dummyTRFR(docID, sourceCoref.normalizeStrictly(J)))),
                        TypeRoleFillerRealisSet.from(ImmutableSet.of(
                                dummyTRFR(docID, sourceCoref.normalizeStrictly(A)),
                                dummyTRFR(docID, sourceCoref.normalizeStrictly(H)))),
                        TypeRoleFillerRealisSet.from(ImmutableSet.of(
                                dummyTRFR(docID, sourceCoref.normalizeStrictly(D)),
                                dummyTRFR(docID, sourceCoref.normalizeStrictly(H)))),
                        TypeRoleFillerRealisSet.from(ImmutableSet.of(
                                dummyTRFR(docID, sourceCoref.normalizeStrictly(F)),
                                dummyTRFR(docID, sourceCoref.normalizeStrictly(H)))),
                        TypeRoleFillerRealisSet.from(ImmutableSet.of(
                                dummyTRFR(docID, sourceCoref.normalizeStrictly(H))))),
                ImmutableSet.<TypeRoleFillerRealis>of());

        final ResponseLinking sourceResponseLinking = ExactMatchEventArgumentLinkingAligner.create()
                .alignToResponseLinking(sourceArgumentLinking, minimalAnswerKeyFor(sourceCoref));

        // target coref clusters. 1={A,B}, 2={C}, 3={d,e,f,g}, 4={h,i}, 5={K}
        // compared to source,
        // * source's 1 is split into target's 1 and 2
        // * source's 2 and 3 are merged into target's 3
        // * 4 is the same in each
        // * source's 5 is dropped
        // * K is newly introduced
        final CorefAnnotation targetCoref = CorefAnnotation.strictBuilder(docID)
                .corefCAS(A, 1)
                .corefCAS(B, 1)
                .corefCAS(C, 2)
                .corefCAS(D, 3)
                .corefCAS(E, 3)
                .corefCAS(F, 3)
                .corefCAS(G, 3)
                .corefCAS(H, 4)
                .corefCAS(I, 4)
                .corefCAS(K, 5).build();

        final EventArgumentLinking referenceArgumentLinking = EventArgumentLinking.create(
                docID, ImmutableSet.of(
                        TypeRoleFillerRealisSet.from(ImmutableSet.of(
                                dummyTRFR(docID, targetCoref.normalizeStrictly(A)),
                                dummyTRFR(docID, targetCoref.normalizeStrictly(C)),
                                dummyTRFR(docID, targetCoref.normalizeStrictly(H)))),
                        TypeRoleFillerRealisSet.from(ImmutableSet.of(
                                dummyTRFR(docID, targetCoref.normalizeStrictly(D)),
                                dummyTRFR(docID, targetCoref.normalizeStrictly(H)))),
                        TypeRoleFillerRealisSet.from(ImmutableSet.of(
                                dummyTRFR(docID, targetCoref.normalizeStrictly(H))))),
                ImmutableSet.of(dummyTRFR(docID, targetCoref.normalizeStrictly(K))));

        final AnswerKey targetAnswerKey = minimalAnswerKeyFor(targetCoref);
        final ResponseLinking referenceResponseLinking = ExactMatchEventArgumentLinkingAligner.create()
                .alignToResponseLinking(referenceArgumentLinking, targetAnswerKey);

        final NaiveResponseLinkingProjector projector = NaiveResponseLinkingProjector.create();
        final ResponseLinking result = projector.from(sourceResponseLinking)
                .projectTo(minimalAnswerKeyFor(targetCoref));
        assertEquals(referenceResponseLinking, result);
    }

}
