package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.KBPEATestUtils;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import static com.bbn.kbp.events2014.KBPEATestUtils.dummyTRFR;
import static org.junit.Assert.assertEquals;

/**
 * Created by rgabbard on 6/25/15.
 */
public class TestResponseMapping {
  @Test
  public void testLinkingMapping() {
    final Symbol docID = Symbol.from("foo");

    final KBPEATestUtils.DummyResponseGenerator rGen = new KBPEATestUtils.DummyResponseGenerator();
    final Response a = rGen.responseFor(dummyTRFR(docID, KBPEATestUtils.kbpString("a")));
    final Response b = rGen.responseFor(dummyTRFR(docID, KBPEATestUtils.kbpString("b")));
    final Response c = rGen.responseFor(dummyTRFR(docID, KBPEATestUtils.kbpString("c")));
    final Response d = rGen.responseFor(dummyTRFR(docID, KBPEATestUtils.kbpString("d")));
    final Response e = rGen.responseFor(dummyTRFR(docID, KBPEATestUtils.kbpString("e")));
    final Response f = rGen.responseFor(dummyTRFR(docID, KBPEATestUtils.kbpString("f")));
    final Response g = rGen.responseFor(dummyTRFR(docID, KBPEATestUtils.kbpString("g")));

    final ResponseLinking sourceLinking = ResponseLinking.from(docID,
        ImmutableSet.of(ResponseSet.from(a, b), ResponseSet.from(a, c),
            ResponseSet.from(a, c, d), ResponseSet.from(d)), ImmutableList.of(e, f));
    final ResponseLinking expectedResult = ResponseLinking.from(docID,
        ImmutableSet.of(ResponseSet.from(a,c)), ImmutableList.of(g));
    final ResponseMapping mapping = ResponseMapping.create(
        ImmutableMap.of(b,c, f, g), ImmutableSet.of(d,e));

    assertEquals(expectedResult, mapping.apply(sourceLinking));
  }
}
