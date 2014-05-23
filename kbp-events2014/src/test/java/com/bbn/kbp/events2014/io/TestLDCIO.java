package com.bbn.kbp.events2014.io;

import java.io.File;
import java.io.IOException;

import com.bbn.kbp.events2014.*;
import junit.framework.TestCase;

import org.junit.Test;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ResponseAssessment.MentionType;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class TestLDCIO extends TestCase {
	private final Symbol docid = Symbol.from("AFP_ENG_20030304.0250");
	private Scored<Response> arg;
	private ResponseAssessment ann;
	private AssessedResponse annArg;

	@Override
	public void setUp() {
		final KBPString cas = KBPString.from("Phillipines",
			CharOffsetSpan.fromOffsetsOnly(42, 64));
		arg = Scored.from(Response.createFrom(docid,
			Symbol.from("Conflict.Attack"),
			Symbol.from("Attacker"),
			cas,
			cas.charOffsetSpan(),
			ImmutableSet.of(CharOffsetSpan.fromOffsetsOnly(327, 397)),
			ImmutableSet.of(CharOffsetSpan.fromOffsetsOnly(373, 383)),
			KBPRealis.Actual), 0.5465980768203735);
		ann = ResponseAssessment.create(Optional.of(FieldAssessment.CORRECT), Optional.of(FieldAssessment.CORRECT),
                Optional.of(FieldAssessment.CORRECT),
                Optional.of(KBPRealis.Actual),
                Optional.of(FieldAssessment.CORRECT), Optional.of(1),
                Optional.of(MentionType.NAME));
		annArg = AssessedResponse.from(arg.item(), ann);
	}

	@Test
	public void testArgumentRoundtrip() throws IOException {
		final File tmpDir = Files.createTempDir();
		tmpDir.deleteOnExit();

		final SystemOutputStore store1 = AssessmentSpecFormats.createSystemOutputStore(tmpDir);
		store1.write(SystemOutput.from(docid, ImmutableList.of(arg)));
		store1.close();

		final SystemOutputStore source2 = AssessmentSpecFormats.openSystemOutputStore(tmpDir);
		final SystemOutput rereadArg = source2.read(docid);
		source2.close();

		assertEquals(1, rereadArg.size());
		assertEquals(arg, Iterables.getFirst(rereadArg.scoredResponses(), null));
	}

	@Test
	public void testAnnotationRountrip() throws IOException {
		final File tmpDir = Files.createTempDir();
		tmpDir.deleteOnExit();

		final AnnotationStore store1 = AssessmentSpecFormats.createAnnotationStore(tmpDir);
		store1.write(AnswerKey.from(docid, ImmutableList.of(annArg), ImmutableList.<Response>of()));
		store1.close();
		final AnnotationStore store2 = AssessmentSpecFormats.openAnnotationStore(tmpDir);
		final AnswerKey rereadArg = store2.read(docid);
		store2.close();
		assertEquals(1, rereadArg.annotatedResponses().size());
		assertEquals(annArg, Iterables.getFirst(rereadArg.annotatedResponses(), null));
	}

    /*@Ignore("temporarily ignored until we can update the file")
    @Test
    public void existing() throws IOException {
        final String docid = "APW_ENG_20030513.0139";

        final File tmpDir = Files.createTempDir();
        tmpDir.deleteOnExit();
        final File tmpFile = new File(tmpDir, docid);
        tmpFile.deleteOnExit();

        Resources.asCharSource(Resources.getResource(TestLDCIO.class, "/"+docid),
                Charsets.UTF_8).copyTo(Files.asCharSink(tmpFile, Charsets.UTF_8));
        final AnnotationStore annStore = AssessmentSpecFormats.openAnnotationStore(tmpDir);
        final AnswerKey key = annStore.read(Symbol.from(docid));

    } */
}
