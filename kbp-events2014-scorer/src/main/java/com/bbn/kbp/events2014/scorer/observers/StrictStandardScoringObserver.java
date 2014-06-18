package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.scoring.Scoreds;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.scorer.observers.breakdowns.BreakdownFunctions;
import com.bbn.kbp.events2014.scorer.observers.breakdowns.BreakdownWriter;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.HTMLErrorRecorder;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.getFirst;

public final class StrictStandardScoringObserver extends KBPScoringObserver<TypeRoleFillerRealis> {
	public static final Symbol PRESENT = Symbol.from("PRESENT");
	public static final Symbol ABSENT = Symbol.from("ABSENT");

	private final Predicate<AssessedResponse> ResponseCorrect;
	private final Logger log;
	private final ProvenancedConfusionMatrix.Builder corpusConfusionMatrixBuilder =
			ProvenancedConfusionMatrix.builder();

    private final HTMLErrorRecorder renderer;

	public static KBPScoringObserver<TypeRoleFillerRealis> strictScorer(final HTMLErrorRecorder renderer) {
		return new StrictStandardScoringObserver("Strict", AssessedResponse.IsCompletelyCorrect,
			renderer);
	}

	public static KBPScoringObserver<TypeRoleFillerRealis> standardScorer(final HTMLErrorRecorder renderer) {
		return new StrictStandardScoringObserver("Standard", AssessedResponse.IsCorrectUpToInexactJustifications,
			renderer);
	}

    @Override
    public void writeCorpusOutput(File directory) throws IOException {
        final ProvenancedConfusionMatrix<TypeRoleFillerRealis> corpusConfusionMatrix =
                corpusConfusionMatrixBuilder.build();

        final BreakdownWriter breakdownWriter = BreakdownWriter.create(BreakdownFunctions.StandardBreakdowns);
        breakdownWriter.addFMeasureToPrint("Present", PRESENT);
        breakdownWriter.writeBreakdownsToFiles(corpusConfusionMatrix, directory);

        final FMeasureCounts corpusFMeasure = corpusConfusionMatrix.buildSummaryMatrix()
                .FMeasureVsAllOthers(PRESENT);
        ImprovementWriter.writeImprovements(directory, "Aggregate", corpusFMeasure,
                ImmutableMap.of("Aggregate", corpusConfusionMatrix.buildSummaryMatrix()));
    }


    public void appendSummaryHTML(final StringBuilder sb) {
		final SummaryConfusionMatrix corpusMatrix = corpusConfusionMatrixBuilder.build().buildSummaryMatrix();

		sb.append(corpusMatrix.FMeasureVsAllOthers(Symbol.from("PRESENT")).compactPrettyString());
	}

	private StrictStandardScoringObserver(final String name,
		final Predicate<AssessedResponse> ResponseCorrect, final HTMLErrorRecorder renderer)
	{
		super(name);
		this.ResponseCorrect = checkNotNull(ResponseCorrect);
		this.log = LoggerFactory.getLogger(name());
		this.renderer = checkNotNull(renderer);
	}

	private void observeDocumentConfusionMatrix(final ProvenancedConfusionMatrix<TypeRoleFillerRealis> matrix) {
		corpusConfusionMatrixBuilder.accumulate(matrix);
	}


    @Override
	public KBPAnswerSourceObserver answerSourceObserver(
			final SystemOutputAnswerSource<TypeRoleFillerRealis> systemOutputSource,
			final AnswerKeyAnswerSource<TypeRoleFillerRealis> answerKeyAnswerSource)
	{
		return new KBPAnswerSourceObserver(systemOutputSource, answerKeyAnswerSource) {
			private final ProvenancedConfusionMatrix.Builder<TypeRoleFillerRealis> confusionMatrixBuilder =
				ProvenancedConfusionMatrix.builder();
            private final StringBuilder htmlOut = new StringBuilder();
            private final StringBuilder textOut = new StringBuilder();

			@Override
			public void start() {
				htmlOut.append(renderer.preamble());
			}

			@Override
			public void annotatedSelectedResponse(final TypeRoleFillerRealis answerable, final Response response,
					final AssessedResponse annotationForSelected, final Set<AssessedResponse> allAssessments)
			{
				if (ResponseCorrect.apply(annotationForSelected)) {
					textOut.append("True Positive\n");
					confusionMatrixBuilder.record(PRESENT, PRESENT, answerable);
				} else {
					textOut.append("False positive. Response annotated in pool as ")
                            .append(annotationForSelected.assessment()).append("\n");
					confusionMatrixBuilder.record(PRESENT, ABSENT, answerable);
					htmlOut.append(renderer.vsAnnotated("kbp-false-positive-annotated", "False positive (annotated)", response,
                            systemOutputSource.systemOutput().score(response), annotationForSelected));

                    if (any(allAssessments, ResponseCorrect)) {
                        // if any correct answer was found for this equivalence class in the answer key,
                        // then this is also a false negative
                        textOut.append("FN: System response was assessed as incorrect, but the following correct response is in the pool: ")
                                .append(Iterables.find(allAssessments, ResponseCorrect)).append("\n");
                        confusionMatrixBuilder.record(ABSENT, PRESENT, answerable);
                        htmlOut.append(renderer.vsAnnotated("kbp-false-negative", "False negative", getFirst(allAssessments, null).response(),
                                allAssessments));
                    }
				}
			}

			@Override
			public void unannotatedSelectedResponse(final TypeRoleFillerRealis answerable, final Response unannotated) {
				textOut.append("No assessment for ").append(unannotated).append(", counting as wrong\n");
				confusionMatrixBuilder.record(PRESENT, ABSENT, answerable);
				htmlOut.append(renderer.vsAnnotated("kbp-false-positive-unannotated", "False positive (unannotated)", unannotated,
                        ImmutableList.of(systemOutputSource.systemOutput().score(unannotated)), answerKey().answers(answerable)));
			}

			@Override
			public void annotationsOnlyNonEmpty(final TypeRoleFillerRealis answerable,
				final Set<AssessedResponse> annotatedResponses)
			{
				// we check at the beginning of the method that these
				// answers all agree,
				// so we can take the first
				if (any(annotatedResponses, ResponseCorrect)) {
					textOut.append("FN: No system response, but the following correct response is in the pool: ")
                            .append(Iterables.find(annotatedResponses, ResponseCorrect)).append("\n");
					confusionMatrixBuilder.record(ABSENT, PRESENT, answerable);
					htmlOut.append(renderer.vsAnnotated("kbp-false-negative", "False negative", getFirst(annotatedResponses, null).response(),
                            annotatedResponses));
				} else {
					textOut.append("TN: No system response, but all matching answers in the pool are wrong.\n");
					// true negative, no action
				}
			}

			@Override
			public void end() {
                final ProvenancedConfusionMatrix<TypeRoleFillerRealis> confusionMatrix = confusionMatrixBuilder.build();
                observeDocumentConfusionMatrix(confusionMatrix);

			}

            @Override
            public void writeDocumentOutput(File directory) throws IOException {
                final StringBuilder sb = new StringBuilder();
                sb.append("===== Confusion matrix for ").append(name()).append(" =====\n");

                final ProvenancedConfusionMatrix<TypeRoleFillerRealis> confusionMatrix = confusionMatrixBuilder.build();
                final SummaryConfusionMatrix summaryConfusionMatrix = confusionMatrix.buildSummaryMatrix();
                sb.append(summaryConfusionMatrix.prettyPrint()).append("\n");
                sb.append(confusionMatrix.prettyPrint()).append("\n");

                Files.asCharSink(new File(directory, "confusionMatrix.txt"), Charsets.UTF_8).write(sb.toString());

                final String html = htmlOut.toString();
                if (!html.isEmpty()) {
                    Files.asCharSink(new File(directory, "errors.html"), Charsets.UTF_8).write(html);
                }
            }
		};
	}

    /**
     * Computes and outputs what would happen if we had perfect precision, recall, etc.
     */
    private static class ImprovementWriter {
        private enum ImprovementMode {
            PERFECT_PRECISION {
                public FMeasureCounts improve(FMeasureCounts baseCounts, FMeasureCounts improvingCounts) {
                    return FMeasureCounts.from(baseCounts.truePositives(),
                            baseCounts.falsePositives() - improvingCounts.falsePositives(),
                            baseCounts.falseNegatives());
                }
            },
            PERFECT_RECALL {
                public FMeasureCounts improve(FMeasureCounts baseCounts, FMeasureCounts improvingCounts) {
                    return FMeasureCounts.from(baseCounts.truePositives() + improvingCounts.truePositives(),
                            baseCounts.falsePositives(),
                            baseCounts.falseNegatives() - improvingCounts.falseNegatives());
                }
            },
            PERFECT_EVERYTHING {
                public FMeasureCounts improve(FMeasureCounts baseCounts, FMeasureCounts improvingCounts) {
                    return FMeasureCounts.from(baseCounts.truePositives() + improvingCounts.falseNegatives(),
                            baseCounts.falsePositives() - improvingCounts.falsePositives(),
                            baseCounts.falseNegatives() - improvingCounts.falseNegatives());
                }
            };

            public abstract FMeasureCounts improve(FMeasureCounts baseCounts, FMeasureCounts improvingCounts);
        }

        private static void writeImprovements(final File directory, final String modeName, final FMeasureCounts corpusFMeasure,
                                              final Map<String, SummaryConfusionMatrix> data) throws IOException
        {
            writeImprovements(modeName, corpusFMeasure, data, ImprovementMode.PERFECT_PRECISION,
                    new File(directory, modeName + ".improvementsWithPerfectPrecision"));
            writeImprovements(modeName, corpusFMeasure, data, ImprovementMode.PERFECT_RECALL,
                    new File(directory, modeName + ".improvementsWithPerfectRecall"));
            writeImprovements(modeName, corpusFMeasure, data, ImprovementMode.PERFECT_EVERYTHING,
                    new File(directory, modeName + ".improvementsWithPerfectEverything"));
        }

        private static void writeImprovements(final String modeName, final FMeasureCounts corpusFMeasure,
                                              final Map<String, SummaryConfusionMatrix> data,
                                              final ImprovementMode improvementMode,
                                              final File outputFile) throws IOException {
            final List<Scored<String>> improvements = Lists.newArrayList();
            for (Map.Entry<String, SummaryConfusionMatrix> entry : data.entrySet()) {
                final FMeasureCounts improvedCounts = improvementMode.improve(corpusFMeasure,
                        entry.getValue().FMeasureVsAllOthers(PRESENT));
                final double F1Improvement = improvedCounts.F1() - corpusFMeasure.F1();
                improvements.add(Scored.from(entry.getKey(), F1Improvement));
            }

            Collections.sort(improvements, Scoreds.<String>ByScoreThenByItem().reverse());

            final StringBuilder sb = new StringBuilder();

            sb.append("Score changes for " + modeName + " when improvement " + improvementMode.toString() + " is applied\n");

            final int nameFieldSize = 4 + MapUtils.longestKeyLength(data);
            final String headerFormat = "%" + nameFieldSize + "s    Change in F1\n";
            final String entryFormat = "%" + nameFieldSize + "s    %6.3f\n";

            sb.append(String.format(headerFormat, modeName));
            sb.append(Strings.repeat("-", nameFieldSize + 18)).append("\n");
            for (Scored<String> improvement : improvements) {
                sb.append(String.format(entryFormat, improvement.item(), 100.0 * improvement.score()));
            }

            Files.asCharSink(outputFile, Charsets.UTF_8).write(sb.toString());
        }
    }

}
