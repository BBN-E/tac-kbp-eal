package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AsssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import com.bbn.kbp.events2014.scorer.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.HTMLErrorRecorder;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.getFirst;

public final class StrictStandardScoringObserver extends KBPScoringObserver<TypeRoleFillerRealis> {
	public static final Symbol PRESENT = Symbol.from("PRESENT");
	public static final Symbol ABSENT = Symbol.from("ABSENT");

	private final Predicate<AsssessedResponse> ResponseCorrect;
	private final Logger log;
	private final SummaryConfusionMatrix.Builder corpusConfusionMatrixBuilder =
			SummaryConfusionMatrix.builder();
    private final Map<Symbol, SummaryConfusionMatrix.Builder> byTypeConfusionMatrices =
            Maps.newHashMap();
    private final Map<Symbol, SummaryConfusionMatrix.Builder> byRoleConfusionMatrices =
            Maps.newHashMap();
    private final HTMLErrorRecorder renderer;

	public static KBPScoringObserver<TypeRoleFillerRealis> strictScorer(final HTMLErrorRecorder renderer) {
		return new StrictStandardScoringObserver("Strict", AsssessedResponse.IsCompletelyCorrect,
			renderer);
	}

	public static KBPScoringObserver<TypeRoleFillerRealis> standardScorer(final HTMLErrorRecorder renderer) {
		return new StrictStandardScoringObserver("Standard", AsssessedResponse.IsCorrectUpToInexactJustifications,
			renderer);
	}

    @Override
	public void endCorpus() {
        // gather the matrices to output (each splitting up the results different ways)
        // using an ImmutableMap to maintain iteration order
        final ImmutableMap.Builder<String, SummaryConfusionMatrix> outputMatrices = ImmutableMap.builder();
        outputMatrices.put("Aggregate over all event types", corpusConfusionMatrixBuilder.build());

        Iterable<Map.Entry<String, Map<Symbol, SummaryConfusionMatrix.Builder>>> printModes = ImmutableMap.of(
                "Type", byTypeConfusionMatrices, "Role", byRoleConfusionMatrices).entrySet();

        for (final Map.Entry<String, Map<Symbol, SummaryConfusionMatrix.Builder>> printMode : printModes) {
            final String printModeName = printMode.getKey();
            for (final Map.Entry<Symbol, SummaryConfusionMatrix.Builder> matrixEntry : printMode.getValue().entrySet()) {
                outputMatrices.put(String.format("For %s %s", printModeName,
                        matrixEntry.getKey().toString()), matrixEntry.getValue().build());
            }
        }

        final StringBuilder sb = new StringBuilder();

        // actually output them
        for (final Map.Entry<String, SummaryConfusionMatrix> outputMatrixEntry : outputMatrices.build().entrySet()) {
            final String title = outputMatrixEntry.getKey();
            final SummaryConfusionMatrix outputMatrix = outputMatrixEntry.getValue();
            sb.append("\n" + "==============" + title + "====================\n" + outputMatrix.prettyPrint() + "\n\n"
                    + outputMatrix.FMeasureVsAllOthers(PRESENT).compactPrettyString()+"\n\n");
        }

		log.info("\n"+sb.toString());
	}

	@Override
	public void appendSummaryHTML(final StringBuilder sb) {
		final SummaryConfusionMatrix corpusMatrix = corpusConfusionMatrixBuilder.build();

//		sb.append(KBPHTMLRenderer.confusionMatrix(name, corpusMatrix));
		sb.append(corpusMatrix.FMeasureVsAllOthers(Symbol.from("PRESENT")).compactPrettyString());
	}

	private StrictStandardScoringObserver(final String name,
		final Predicate<AsssessedResponse> ResponseCorrect, final HTMLErrorRecorder renderer)
	{
		super(name);
		this.ResponseCorrect = checkNotNull(ResponseCorrect);
		this.log = LoggerFactory.getLogger(name());
		this.renderer = checkNotNull(renderer);
	}

	private void observeDocumentConfusionMatrix(final ProvenancedConfusionMatrix<TypeRoleFillerRealis> matrix) {
		corpusConfusionMatrixBuilder.accumulate(matrix.buildSummaryMatrix());
        for (final Symbol eventType : FluentIterable.from(matrix.entries())
                .transform(TypeRoleFillerRealis.Type).toSet())
        {
            accumulateConfusionMatrix(byTypeConfusionMatrices, eventType,
                    matrix.filteredCopy(compose(equalTo(eventType), TypeRoleFillerRealis.Type)));
        }

        for (final Symbol eventRole : FluentIterable.from(matrix.entries()).transform(TypeSlashRole).toSet()) {
            accumulateConfusionMatrix(byTypeConfusionMatrices, eventRole,
                    matrix.filteredCopy(compose(equalTo(eventRole), TypeSlashRole)));
        }
	}


    @Override
	public KBPAnswerSourceObserver answerSourceObserver(
			final SystemOutputAnswerSource<TypeRoleFillerRealis> systemOutputSource,
			final AnswerKeyAnswerSource<TypeRoleFillerRealis> answerKeyAnswerSource)
	{
		return new KBPAnswerSourceObserver(systemOutputSource, answerKeyAnswerSource) {
			private final ProvenancedConfusionMatrix.Builder<TypeRoleFillerRealis> confusionMatrixBuilder =
				ProvenancedConfusionMatrix.builder();

			@Override
			public void start() {
				documentOut().append(renderer.preamble());
			}

			@Override
			public void annotatedSelectedResponse(final TypeRoleFillerRealis answerable, final Response response,
					final AsssessedResponse annotated)
			{
				if (ResponseCorrect.apply(annotated)) {
					log.info("True Positive");
					confusionMatrixBuilder.record(PRESENT, PRESENT, answerable);
				} else {
					log.info("False positive. Response annotated in pool as {}",
						annotated.assessment());
					confusionMatrixBuilder.record(PRESENT, ABSENT, answerable);
					documentOut().append(renderer.vsAnnotated("kbp-false-positive-annotated", "False positive (annotated)", response,
						systemOutputSource.systemOutput().score(response), annotated));
				}
			}

			@Override
			public void unannotatedSelectedResponse(final TypeRoleFillerRealis answerable, final Response unannotated) {
				log.info("No assessment for {}, counting as wrong", unannotated);
				confusionMatrixBuilder.record(PRESENT, ABSENT, answerable);
				documentOut().append(renderer.vsAnnotated("kbp-false-positive-unannotated", "False positive (unannotated)", unannotated,
					ImmutableList.of(systemOutputSource.systemOutput().score(unannotated)), answerKey().answers(answerable)));
			}

			@Override
			public void annotationsOnlyNonEmpty(final TypeRoleFillerRealis answerable,
				final Set<AsssessedResponse> annotatedResponses)
			{
				// we check at the beginning of the method that these
				// answers all agree,
				// so we can take the first
				if (any(annotatedResponses, ResponseCorrect)) {
					log.info("FN: No system response, but the following correct response is in the pool: {}",
						Iterables.find(annotatedResponses, ResponseCorrect));
					confusionMatrixBuilder.record(ABSENT, PRESENT, answerable);
					documentOut().append(renderer.vsAnnotated("kbp-false-negative", "False negative", getFirst(annotatedResponses, null).response(),
						annotatedResponses));
				} else {
					log.info("TN: No system response, but all matching answers in the pool are wrong.");
					// true negative, no action
				}
			}

			@Override
			public void end() {
				log.info("===== Confusion matrix for {} =====", name());

				final ProvenancedConfusionMatrix<TypeRoleFillerRealis> confusionMatrix = confusionMatrixBuilder.build();
				final SummaryConfusionMatrix summaryConfusionMatrix = confusionMatrix.buildSummaryMatrix();
				log.info("\n"+summaryConfusionMatrix.prettyPrint());
				log.info("\n"+confusionMatrix.prettyPrint());
				observeDocumentConfusionMatrix(confusionMatrix);
			}
		};
	}

    private void accumulateConfusionMatrix(Map<Symbol,SummaryConfusionMatrix.Builder> matrixMap,
                                           Symbol key, ProvenancedConfusionMatrix<TypeRoleFillerRealis> toAdd)
    {
        if (!matrixMap.containsKey(key)) {
            matrixMap.put(key, SummaryConfusionMatrix.builder());
        }
        matrixMap.get(key).accumulate(toAdd.buildSummaryMatrix());
    }

    private static final Function<TypeRoleFillerRealis,Symbol> TypeSlashRole = new Function<TypeRoleFillerRealis, Symbol> () {

        @Override
        public Symbol apply(TypeRoleFillerRealis input) {
            return Symbol.from(input.type().toString() + "/" + input.role().toString());
        }
    };
}
