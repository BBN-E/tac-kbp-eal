package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;

/**
 * Created by rgabbard on 9/17/14.
 */
public class ResponseTest {

  @Test
  public void testDistinguishPJAndAAJHashes() {
    // ensure our hash function doesn't just treat PJ and AAJ lists as if things can
    // just be moved from one to another
    final Symbol doc = Symbol.from("AFP_ENG_20100414.0615");
    final Symbol type = Symbol.from("Life.Die");
    final Symbol role = Symbol.from("Agent");
    final KBPString CAS = KBPString.from("police", 1134, 1139);
    final CharOffsetSpan baseFiller = CharOffsetSpan.fromOffsetsOnly(1134, 1139);
    final KBPRealis realis = KBPRealis.Actual;

    final Response r1 = Response.of(doc, type,
        role, CAS, baseFiller,
        ImmutableSet.<CharOffsetSpan>of(), ImmutableSet
        .of(CharOffsetSpan.fromOffsetsOnly(1039, 1243), CharOffsetSpan.fromOffsetsOnly(642, 838)),
        realis);

    final Response r2 = Response.of(doc, type,
        role, CAS, baseFiller,
        ImmutableSet.<CharOffsetSpan>of(CharOffsetSpan.fromOffsetsOnly(1039, 1243)),
        ImmutableSet.of(CharOffsetSpan.fromOffsetsOnly(642, 838)),
        realis);

    assertNotEquals(r1.uniqueIdentifier(), r2.uniqueIdentifier());
                /*
                java.lang.IllegalArgumentException: Multiple entries with same key: 8c7fa2170da4adb3747e51a7819696c16d6c9890=AFP_ENG_20100414.0615-Life.
Die-Agent([1134:1139]=police[1134-1139]; Actual; [642-838]; [1039-1243]) and 8c7fa2170da4adb3747e51a7819696c16d6c9890=AFP_ENG_20100414.0
615-Life.Die-Agent([1134:1139]=police[1134-1139]; Actual; [1039-1243, 642-838]; [])

                 */
  }
}
