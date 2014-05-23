package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.diff.FMeasureTableRenderer;
import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.scoring.Scoreds;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
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
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.getFirst;

public final class StrictStandardScoringObserver extends KBPScoringObserver<TypeRoleFillerRealis> {
	public static final Symbol PRESENT = Symbol.from("PRESENT");
	public static final Symbol ABSENT = Symbol.from("ABSENT");

	private final Predicate<AssessedResponse> ResponseCorrect;
	private final Logger log;
	private final SummaryConfusionMatrix.Builder corpusConfusionMatrixBuilder =
			SummaryConfusionMatrix.builder();
    private final Map<Symbol, SummaryConfusionMatrix.Builder> byTypeConfusionMatrices =
            Maps.newHashMap();
    private final Map<Symbol, SummaryConfusionMatrix.Builder> byRoleConfusionMatrices =
            Maps.newHashMap();
    private final HTMLErrorRecorder renderer;
    private final JacksonSerializer jackson = JacksonSerializer.forNormalJSON();

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
        final FMeasureCounts corpusFMeasure = corpusConfusionMatrixBuilder.build().FMeasureVsAllOthers(PRESENT);
        Iterable<Map.Entry<String, Map<Symbol, SummaryConfusionMatrix.Builder>>> printModes = ImmutableMap.of(
                "Aggregate", ImmutableMap.of(Symbol.from("Aggregate"), corpusConfusionMatrixBuilder),
                "Type", byTypeConfusionMatrices, "Role", byRoleConfusionMatrices).entrySet();


        for (final Map.Entry<String, Map<Symbol, SummaryConfusionMatrix.Builder>> printMode : printModes) {
            final String modeName = printMode.getKey();
            final Map<String, SummaryConfusionMatrix> data =
                    MapUtils.copyWithTransformedEntries(printMode.getValue(),
                            Functions.toStringFunction(), SummaryConfusionMatrix.Builder.Build);

            final File scoringBreakdownFilename = new File(directory, modeName);

            writeScoringBreakdown(modeName, data, scoringBreakdownFilename);
            writeImprovements(modeName, corpusFMeasure, data, ImprovementMode.PERFECT_PRECISION,
                    new File(directory, modeName + ".improvementsWithPerfectPrecision"));
            writeImprovements(modeName, corpusFMeasure, data, ImprovementMode.PERFECT_RECALL,
                    new File(directory, modeName + ".improvementsWithPerfectRecall"));
            writeImprovements(modeName, corpusFMeasure, data, ImprovementMode.PERFECT_EVERYTHING,
                    new File(directory, modeName + ".improvementsWithPerfectEverything"));
        }
    }

    private static final Function<SummaryConfusionMatrix, FMeasureCounts> FmeasureVsPresent = new Function<SummaryConfusionMatrix, FMeasureCounts>() {
        @Override
        public FMeasureCounts apply(SummaryConfusionMatrix input) {
            return input.FMeasureVsAllOthers(PRESENT);
        }
    };

    private void writeScoringBreakdown(String modeName, Map<String, SummaryConfusionMatrix> data, File scoringBreakdownFilename) throws IOException {

        final Map<String, FMeasureCounts> fMeasuresToPrint = Maps.transformValues(data,
                FmeasureVsPresent);

        final FMeasureTableRenderer tableRenderer = FMeasureTableRenderer.create()
                .setNameFieldLength(4 + MapUtils.longestKeyLength(fMeasuresToPrint));

        Files.asCharSink(scoringBreakdownFilename, Charsets.UTF_8)
            .write(modeName + "\n" + tableRenderer.render(fMeasuresToPrint));

        // also write this as JSON
        final File JSONFilename = new File(scoringBreakdownFilename.getAbsolutePath() + ".json");

        // we make a copy to ensure what is written is Jackson-serializable
        jackson.serializeTo(ImmutableMap.copyOf(fMeasuresToPrint),
                Files.asByteSink(JSONFilename));
    }

    private enum ImprovementMode  {
        PERFECT_PRECISION {
            public FMeasureCounts improve(FMeasureCounts baseCounts, FMeasureCounts improvingCounts) {
                return FMeasureCounts.from(baseCounts.truePositives(),
                        baseCounts.falsePositives()-improvingCounts.falsePositives(),
                        baseCounts.falseNegatives());
            }},
        PERFECT_RECALL {
            public FMeasureCounts improve(FMeasureCounts baseCounts, FMeasureCounts improvingCounts) {
                return FMeasureCounts.from(baseCounts.truePositives()+improvingCounts.truePositives(),
                        baseCounts.falsePositives(),
                        baseCounts.falseNegatives()-improvingCounts.falseNegatives());
            }},
        PERFECT_EVERYTHING {
            public FMeasureCounts improve(FMeasureCounts baseCounts, FMeasureCounts improvingCounts) {
                return FMeasureCounts.from(baseCounts.truePositives()+improvingCounts.falseNegatives(),
                        baseCounts.falsePositives()-improvingCounts.falsePositives(),
                        baseCounts.falseNegatives()-improvingCounts.falseNegatives());
        }};

        public abstract FMeasureCounts improve(FMeasureCounts baseCounts, FMeasureCounts improvingCounts);
    };

    private void writeImprovements(final String modeName, final FMeasureCounts corpusFMeasure,
                                   final Map<String, SummaryConfusionMatrix> data,
                                   final ImprovementMode improvementMode,
                                   final File outputFile) throws IOException
    {
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

        final int nameFieldSize = 4+ MapUtils.longestKeyLength(data);
        final String headerFormat = "%"+nameFieldSize + "s    Change in F1\n";
        final String entryFormat = "%"+nameFieldSize + "s    %6.3f\n";

        sb.append(String.format(headerFormat, modeName));
        sb.append(Strings.repeat("-", nameFieldSize+18)).append("\n");
        for (Scored<String> improvement : improvements) {
            sb.append(String.format(entryFormat, improvement.item(), 100.0*improvement.score()));
        }

        Files.asCharSink(outputFile, Charsets.UTF_8).write(sb.toString());
    }


    public void appendSummaryHTML(final StringBuilder sb) {
		final SummaryConfusionMatrix corpusMatrix = corpusConfusionMatrixBuilder.build();

//		sb.append(KBPHTMLRenderer.confusionMatrix(name, corpusMatrix));
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
		corpusConfusionMatrixBuilder.accumulate(matrix.buildSummaryMatrix());
        for (final Symbol eventType : FluentIterable.from(matrix.entries())
                .transform(TypeRoleFillerRealis.Type).toSet())
        {
            accumulateConfusionMatrix(byTypeConfusionMatrices, eventType,
                    matrix.filteredCopy(compose(equalTo(eventType), TypeRoleFillerRealis.Type)));
        }

        for (final Symbol eventRole : FluentIterable.from(matrix.entries()).transform(TypeDotRole).toSet()) {
            accumulateConfusionMatrix(byRoleConfusionMatrices, eventRole,
                    matrix.filteredCopy(compose(equalTo(eventRole), TypeDotRole)));
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
            private final StringBuilder htmlOut = new StringBuilder();
            private final StringBuilder textOut = new StringBuilder();

			@Override
			public void start() {
				htmlOut.append(renderer.preamble());
			}

			@Override
			public void annotatedSelectedResponse(final TypeRoleFillerRealis answerable, final Response response,
					final AssessedResponse annotated)
			{
				if (ResponseCorrect.apply(annotated)) {
					textOut.append("True Positive\n");
					confusionMatrixBuilder.record(PRESENT, PRESENT, answerable);
				} else {
					textOut.append("False positive. Response annotated in pool as ")
                            .append(annotated.assessment()).append("\n");
					confusionMatrixBuilder.record(PRESENT, ABSENT, answerable);
					htmlOut.append(renderer.vsAnnotated("kbp-false-positive-annotated", "False positive (annotated)", response,
                            systemOutputSource.systemOutput().score(response), annotated));
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

    private void accumulateConfusionMatrix(Map<Symbol,SummaryConfusionMatrix.Builder> matrixMap,
                                           Symbol key, ProvenancedConfusionMatrix<TypeRoleFillerRealis> toAdd)
    {
        if (!matrixMap.containsKey(key)) {
            matrixMap.put(key, SummaryConfusionMatrix.builder());
        }
        matrixMap.get(key).accumulate(toAdd.buildSummaryMatrix());
    }

    private static final Function<TypeRoleFillerRealis,Symbol> TypeDotRole = new Function<TypeRoleFillerRealis, Symbol> () {

        @Override
        public Symbol apply(TypeRoleFillerRealis input) {
            return Symbol.from(input.type().toString() + "." + input.role().toString());
        }
    };
}
