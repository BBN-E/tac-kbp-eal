package com.bbn.kbp.events2014.scorer;

import java.io.IOException;

import junit.framework.TestCase;

import org.junit.Test;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.SystemOutput;

public class ScorerTest extends TestCase {
	private SystemOutput gold;
	private SystemOutput predicted;
	private AnswerKey completelyAnnotated;

	@Override
	public void setUp() throws IOException {
		/*gold = AssessmentSpecFormats.argumentSourceFrom(asCharSource(
			getResource(ScorerTest.class, "/AFP_ENG_20030304.0250.sgm.gold.ldc.txt"),
			Charsets.UTF_8)).get();

		predicted = AssessmentSpecFormats.argumentSourceFrom(asCharSource(
			getResource(ScorerTest.class, "/AFP_ENG_20030304.0250.sgm.predicted.ldc.txt"),
			Charsets.UTF_8)).get();

		completelyAnnotated = AssessmentSpecFormats.annotationSourceFrom((asCharSource(
			getResource(ScorerTest.class, "/AFP_ENG_20030304.0250.sgm.annotated.ldc.txt"),
			Charsets.UTF_8))).get();*/
	}

	@Test
	public void testCompleteAnnotationScoring() {
		/*final AnswerableExtractorAligner<AllFields, KBPOutputArgument, KBPAnnotatedArgument> aligner = AnswerableExtractorAligner.forAnswerExtractors(
			KBPScorer.ExtractAllFieldsFromSystemResponse, KBPScorer.ExtractAllFieldsFromAnnotation);

		final AnswerAlignment<AllFields, KBPOutputArgument, KBPAnnotatedArgument> alignment =
				aligner.align(predicted, completelyAnnotated);

		KBPScorer.scoreComplete(alignment);*/
	}

	private static final Symbol PRESENT = Symbol.from("PRESENT");
	private static final Symbol ABSENT = Symbol.from("ABSENT");
	private static final Symbol CORRECT = Symbol.from("CORRECT");
	private static final Symbol INCORRECT = Symbol.from("INCORRECT");

/*	@Test
	public void testIncompleteOnComplete() {
		final AnswerableExtractorAligner<AllFields, KBPOutputArgument, KBPAnnotatedArgument> aligner = AnswerableExtractorAligner.forAnswerExtractors(
			KBPScorer.ExtractAllFieldsFromSystemResponse, KBPScorer.ExtractAllFieldsFromAnnotation);

		final AnswerAlignment<AllFields, KBPOutputArgument, KBPAnnotatedArgument> alignment =
				aligner.align(predicted, completelyAnnotated);

		final CollectNeedingAnnotationFromIncompleteAnnotation<AllFields, KBPOutputArgument, KBPAnnotatedArgument> needingAnnotation =
			new CollectNeedingAnnotationFromIncompleteAnnotation<AllFields, KBPOutputArgument, KBPAnnotatedArgument>();
		final BuildConfusionMatrix<AllFields, KBPOutputArgument, KBPAnnotatedArgument> confusionMatrixBuilder =
			BuildConfusionMatrix.<AllFields, KBPOutputArgument, KBPAnnotatedArgument>forAnswerFunctions(KBPScorer.IsPresent, KBPScorer.ScoreByRoleJustified);

		for (final AlignedAnswers<AllFields, KBPOutputArgument, KBPAnnotatedArgument> alignedAnswer
				: alignment.alignmentsForAnswerables())
		{
			needingAnnotation.observe(alignedAnswer);
			confusionMatrixBuilder.observe(alignedAnswer);
		}

		assertEquals(0, needingAnnotation.buildResult().size());
		final ProvenancedConfusionMatrix<AlignedAnswers<AllFields, KBPOutputArgument, KBPAnnotatedArgument>> confusionMatrix =
			confusionMatrixBuilder.buildConfusionMatrix();
		final List<AlignedAnswers<AllFields, KBPOutputArgument, KBPAnnotatedArgument>> present_correct = confusionMatrix.cell(PRESENT, CORRECT);
		final List<AlignedAnswers<AllFields, KBPOutputArgument, KBPAnnotatedArgument>> absent_correct = confusionMatrix.cell(ABSENT, CORRECT);
		final List<AlignedAnswers<AllFields, KBPOutputArgument, KBPAnnotatedArgument>> present_incorrect = confusionMatrix.cell(PRESENT, INCORRECT);

		assertEquals(1, present_incorrect.size());
		assertEquals(6, present_correct.size());
		assertEquals(13, absent_correct.size());
	}

	@Test
	public void testIncompleteOnCompleteWithRedundancy() {
		final RedundancyInfo<AllFields> accountForRedundancy = RedundancyInfo.createFromKBPRedundancyAnnotations(completelyAnnotated,
			KBPScorer.ExtractAllFieldsFromAnnotation);
		final Function<KBPOutputArgument,AllFields> scoringFunction = accountForRedundancy.accountForRedundancy(KBPScorer.ExtractAllFieldsFromSystemResponse);
		final Function<KBPAnnotatedArgument,AllFields> scoringFunctionAnn = accountForRedundancy.accountForRedundancy(KBPScorer.ExtractAllFieldsFromAnnotation);

		final AnswerableExtractorAligner<AllFields, KBPOutputArgument, KBPAnnotatedArgument> aligner = AnswerableExtractorAligner.forAnswerExtractors(
			scoringFunction, scoringFunctionAnn);

		final AnswerAlignment<AllFields, KBPOutputArgument, KBPAnnotatedArgument> alignment =
				aligner.align(predicted, completelyAnnotated);

		final CollectNeedingAnnotationFromIncompleteAnnotation<AllFields, KBPOutputArgument, KBPAnnotatedArgument> needingAnnotation =
			new CollectNeedingAnnotationFromIncompleteAnnotation<AllFields, KBPOutputArgument, KBPAnnotatedArgument>();
		final BuildConfusionMatrix<AllFields, KBPOutputArgument, KBPAnnotatedArgument> confusionMatrixBuilder =
			BuildConfusionMatrix.<AllFields, KBPOutputArgument, KBPAnnotatedArgument>forAnswerFunctions(KBPScorer.IsPresent, KBPScorer.ScoreByRoleJustified);

		for (final AlignedAnswers<AllFields, KBPOutputArgument, KBPAnnotatedArgument> alignedAnswer
				: alignment.alignmentsForAnswerables())
		{
			needingAnnotation.observe(alignedAnswer);
			confusionMatrixBuilder.observe(alignedAnswer);
		}

		assertEquals(0, needingAnnotation.buildResult().size());
		final ProvenancedConfusionMatrix<AlignedAnswers<AllFields, KBPOutputArgument, KBPAnnotatedArgument>> confusionMatrix =
			confusionMatrixBuilder.buildConfusionMatrix();
		final List<AlignedAnswers<AllFields, KBPOutputArgument, KBPAnnotatedArgument>> present_correct = confusionMatrix.cell(PRESENT, CORRECT);
		final List<AlignedAnswers<AllFields, KBPOutputArgument, KBPAnnotatedArgument>> absent_correct = confusionMatrix.cell(ABSENT, CORRECT);
		final List<AlignedAnswers<AllFields, KBPOutputArgument, KBPAnnotatedArgument>> present_incorrect = confusionMatrix.cell(PRESENT, INCORRECT);

		assertEquals(1, present_incorrect.size());
		assertEquals(6, present_correct.size());
		assertEquals(11, absent_correct.size());
	}*/
}
