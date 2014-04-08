package com.bbn.kbp.events2014;

import java.util.Set;

import org.junit.Test;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ResponseAssessment.MentionType;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public final class TestPooledAnswerKey {
	final Symbol docid = Symbol.from("foo");
	final Symbol type = Symbol.from("Game.Basketball");
	final Symbol role = Symbol.from("Victor");

	final KBPString cas = KBPString.from("Louisville Cardinals",
		CharOffsetSpan.fromOffsetsOnly(0, 1));
	final KBPString cas2 = KBPString.from("Kentucky Wildcats",
		CharOffsetSpan.fromOffsetsOnly(1, 2));
	final Set<CharOffsetSpan> argJust = ImmutableSet.of();
	final Set<CharOffsetSpan> predJust = ImmutableSet.of(cas.charOffsetSpan());
	final KBPRealis realis = KBPRealis.Actual;

	final Response fooBase = Response.createFrom(docid, type, role, cas, cas.charOffsetSpan(),
		argJust, predJust, realis);
	final Response barBase = Response.createFrom(docid, type, role, cas2, cas2.charOffsetSpan(),
		argJust, predJust, realis);
	final Scored<Response> foo = Scored.from(fooBase, 0.9);
	final Scored<Response> foo_diff_confidence = Scored.from(fooBase, 0.8);

	final Scored<Response> bar = Scored.from(barBase, 0.9);

	final FieldAssessment C = FieldAssessment.CORRECT;

	final ResponseAssessment correct = ResponseAssessment.create(C, Optional.of(C), Optional.of(C),
            Optional.of(KBPRealis.Actual), Optional.of(C), Optional.of(0), Optional.of(MentionType.NAME));
	final ResponseAssessment unreal = ResponseAssessment.create(C, Optional.of(C), Optional.of(C),
            Optional.of(KBPRealis.Other), Optional.of(C), Optional.of(0), Optional.of(MentionType.NAME));

	@SuppressWarnings("unused")
	@Test
	public void testPooledAnswerKey() {
		// test okay for two identical except for confidence with identical answers
		final AnswerKey identical = AnswerKey.from(Symbol.from("foo"), ImmutableList.of(
			AsssessedResponse.from(foo.item(), correct), AsssessedResponse.from(foo_diff_confidence.item(), correct)),
			ImmutableList.<Response>of());

		// test okay for two different with different answer
		final AnswerKey different = AnswerKey.from(Symbol.from("foo"), ImmutableList.of(
			AsssessedResponse.from(foo.item(), correct), AsssessedResponse.from(bar.item(), unreal)),
			ImmutableList.<Response>of());
	}

	@Test(expected=RuntimeException.class)
	public void testPooledAnswerKeyConsistencyException() {
		@SuppressWarnings("unused")
		final AnswerKey bad = AnswerKey.from(Symbol.from("foo"), ImmutableList.of(
			AsssessedResponse.from(foo.item(), correct), AsssessedResponse.from(foo_diff_confidence.item(), unreal)),
			ImmutableList.<Response>of());
	}
}
