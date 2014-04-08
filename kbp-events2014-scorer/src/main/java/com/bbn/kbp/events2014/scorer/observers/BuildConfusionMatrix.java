package com.bbn.kbp.events2014.scorer.observers;

import java.util.Collection;
import java.util.Set;

import com.bbn.kbp.events2014.AsssessedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import com.google.common.base.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Builds a confusion matrix for an alignment by making one matrix entry for
 * every aligned answer, where the left and right labels are determined by
 * applying the supplied functions to the left and right answers, respectively.
 *
 * @param <Answerable>
 */
public final class BuildConfusionMatrix<Answerable> extends KBPScoringObserver<Answerable> {
	private final Logger log;
	private final Function<Collection<AsssessedResponse>, Symbol> rightAnswerFunction;
	private final Function<Collection<Response>, Symbol> leftAnswerFunction;
	private final SummaryConfusionMatrix.Builder corpusConfusionMatrixBuilder = SummaryConfusionMatrix.builder();

	@Override
	public void endCorpus() {
		final SummaryConfusionMatrix corpusMatrix = corpusConfusionMatrixBuilder.build();
		log.info("\n" + corpusMatrix.prettyPrint() + "\n\n" +
				corpusMatrix.FMeasureVsAllOthers(Symbol.from("PRESENT")).compactPrettyString());
	}

	public void observeDocumentConfusionMatrix(final SummaryConfusionMatrix matrix) {
		corpusConfusionMatrixBuilder.accumulate(matrix);
	}

	@Override
	public KBPAnswerSourceObserver answerSourceObserver(
			final SystemOutputAnswerSource<Answerable> systemOutputSource,
			final AnswerKeyAnswerSource<Answerable> answerKeyAnswerSource)
	{
		return new KBPAnswerSourceObserver(systemOutputSource, answerKeyAnswerSource)  {
			private final ProvenancedConfusionMatrix.Builder<Answerable> confusionMatrixBuilder =
				ProvenancedConfusionMatrix.builder();

			@Override
			public void observe(final Answerable answerable, final Set<Response> responses,
				final Set<AsssessedResponse> annotations)
			{
				final Symbol leftAnswer = leftAnswerFunction.apply(responses);
				final Symbol rightAnswer = rightAnswerFunction.apply(annotations);

				log.info("Adding confusion matrix entry {}/{}", leftAnswer, rightAnswer);
				confusionMatrixBuilder.record(leftAnswer, rightAnswer, answerable);
			}

			@Override
			public void end() {
				log.info("Confusion matrix for {}", log.getName());
				final ProvenancedConfusionMatrix<?> confusionMatrix = confusionMatrixBuilder.build();
				final SummaryConfusionMatrix summaryMatrix = confusionMatrix.buildSummaryMatrix();
				log.info(summaryMatrix.prettyPrint());
				log.info(confusionMatrix.prettyPrint());
				observeDocumentConfusionMatrix(summaryMatrix);
			}
		};
	}

	private BuildConfusionMatrix(final String name,
			final Function<Collection<Response>, Symbol> leftAnswerFunction,
			final Function<Collection<AsssessedResponse>, Symbol> rightAnswerFunction)
	{
		super(name);
		this.log = LoggerFactory.getLogger(name);
		this.leftAnswerFunction = checkNotNull(leftAnswerFunction);
		this.rightAnswerFunction = checkNotNull(rightAnswerFunction);
	}

	public static <Answerable> BuildConfusionMatrix<Answerable> forAnswerFunctions(
			final String observerName, final Function<Collection<Response>, Symbol> leftAnswerFunction,
			final Function<Collection<AsssessedResponse>, Symbol> rightAnswerFunction)
	{
		return new BuildConfusionMatrix<Answerable>(observerName, leftAnswerFunction, rightAnswerFunction);
	}
}