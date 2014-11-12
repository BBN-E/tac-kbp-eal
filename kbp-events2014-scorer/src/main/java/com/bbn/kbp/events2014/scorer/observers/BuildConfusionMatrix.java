package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

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
	private final Function<Collection<AssessedResponse>, Symbol> rightAnswerFunction;
	private final Function<Collection<Response>, Symbol> leftAnswerFunction;
	private final SummaryConfusionMatrix.Builder corpusConfusionMatrixBuilder = SummaryConfusionMatrix.builder();

    @Override
    public void writeCorpusOutput(File directory) throws IOException {
        final SummaryConfusionMatrix corpusMatrix = corpusConfusionMatrixBuilder.build();
        Files.asCharSink(new File(directory, "confusionMatrix.txt"), Charsets.UTF_8).write(
                corpusMatrix.prettyPrint() + "\n\n" +
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
            final StringBuilder textOut = new StringBuilder();

			private final ProvenancedConfusionMatrix.Builder<Answerable> confusionMatrixBuilder =
				ProvenancedConfusionMatrix.builder();

			@Override
			public void observe(final Answerable answerable, final Set<Response> responses,
				final Set<AssessedResponse> annotations)
			{
				final Symbol leftAnswer = leftAnswerFunction.apply(responses);
				final Symbol rightAnswer = rightAnswerFunction.apply(annotations);

				textOut.append(String.format("Adding confusion matrix entry %s/%s\n", leftAnswer, rightAnswer));
				confusionMatrixBuilder.record(leftAnswer, rightAnswer, answerable);
			}

			@Override
			public void end() {
				final ProvenancedConfusionMatrix<?> confusionMatrix = confusionMatrixBuilder.build();
				final SummaryConfusionMatrix summaryMatrix = confusionMatrix.buildSummaryMatrix();
				observeDocumentConfusionMatrix(summaryMatrix);
			}

            @Override
            public void writeDocumentOutput(File directory) throws IOException {
				directory.mkdirs();
                textOut.append("Confusion matrix for ").append(name());
                final ProvenancedConfusionMatrix<?> confusionMatrix = confusionMatrixBuilder.build();
                final SummaryConfusionMatrix summaryMatrix = confusionMatrix.buildSummaryMatrix();
                textOut.append(summaryMatrix.prettyPrint()).append("\n");
                textOut.append(confusionMatrix.prettyPrint()).append("\n");
                Files.asCharSink(new File(directory, "confusionMatrix.txt"), Charsets.UTF_8).write(
                        textOut.toString());
            }
		};
	}

	private BuildConfusionMatrix(final String name,
			final Function<Collection<Response>, Symbol> leftAnswerFunction,
			final Function<Collection<AssessedResponse>, Symbol> rightAnswerFunction)
	{
		super(name);
		this.log = LoggerFactory.getLogger(name);
		this.leftAnswerFunction = checkNotNull(leftAnswerFunction);
		this.rightAnswerFunction = checkNotNull(rightAnswerFunction);
	}

	public static <Answerable> BuildConfusionMatrix<Answerable> forAnswerFunctions(
			final String observerName, final Function<Collection<Response>, Symbol> leftAnswerFunction,
			final Function<Collection<AssessedResponse>, Symbol> rightAnswerFunction)
	{
		return new BuildConfusionMatrix<Answerable>(observerName, leftAnswerFunction, rightAnswerFunction);
	}
}