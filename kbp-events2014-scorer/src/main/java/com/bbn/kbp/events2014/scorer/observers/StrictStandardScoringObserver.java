package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.diff.FMeasureTableRenderer;
import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.scoring.Scoreds;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import com.bbn.kbp.events2014.scorer.observers.breakdowns.BreakdownComputer;
import com.bbn.kbp.events2014.scorer.observers.breakdowns.BreakdownFunctions;
import com.bbn.kbp.events2014.scorer.observers.breakdowns.BrokenDownSummaryConfusionMatrix;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.HTMLErrorRecorder;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.*;

public final class StrictStandardScoringObserver extends KBPScoringObserver<TypeRoleFillerRealis> {
	public static final Symbol PRESENT = Symbol.from("PRESENT");
	public static final Symbol ABSENT = Symbol.from("ABSENT");

    private static final ImmutableSetMultimap<String, Symbol> F_MEASURES_TO_PRINT =
            ImmutableSetMultimap.of("Present", PRESENT);

    private final Predicate<AssessedResponse> ResponseCorrect;
	private final Logger log;
    // DocumentResult is defined within this class
    private final List<DocumentResult> documentResults = Lists.newArrayList();
    private final BreakdownComputer<TypeRoleFillerRealis> breakdownComputer;

    // I/O variables
    private final HTMLErrorRecorder renderer;
    private final ImmutableList<Outputter> outputters;

    public static KBPScoringObserver<TypeRoleFillerRealis> strictScorer(final HTMLErrorRecorder renderer,
                                                                        Iterable<? extends Outputter> outputters) {
		return new StrictStandardScoringObserver("Strict", AssessedResponse.IsCompletelyCorrect,
			renderer, BreakdownFunctions.StandardBreakdowns, outputters);
	}

	public static KBPScoringObserver<TypeRoleFillerRealis> standardScorer(final HTMLErrorRecorder renderer,
                                                                          Iterable<? extends Outputter> outputters) {
		return new StrictStandardScoringObserver("Standard", AssessedResponse.IsCorrectUpToInexactJustifications,
			renderer, BreakdownFunctions.StandardBreakdowns, outputters);
	}

    public static KBPScoringObserver<TypeRoleFillerRealis> standardScorer(final HTMLErrorRecorder renderer,
                final Map<String, Function<TypeRoleFillerRealis, Symbol>> additionalBreakdowns,
                final Iterable<? extends Outputter> outputters)
    {
        return new StrictStandardScoringObserver("Standard", AssessedResponse.IsCorrectUpToInexactJustifications,
                renderer, plusStandardBreakdowns(additionalBreakdowns), outputters);
    }

    public static Outputter createStandardOutputter() {
        return new StandardOutputter();
    }

    public static Outputter createBootstrapOutputter(int seed, int numSamples) {
        return new BootstrapOutputter(seed, numSamples);
    }

    private static ImmutableMap<String, Function<TypeRoleFillerRealis, Symbol>>
        plusStandardBreakdowns(Map<String, Function<TypeRoleFillerRealis, Symbol>> additionalBreakdowns)
    {
        final ImmutableMap.Builder<String, Function<TypeRoleFillerRealis,Symbol>> breakdowns = ImmutableMap.builder();

        breakdowns.putAll(BreakdownFunctions.StandardBreakdowns);
        breakdowns.putAll(additionalBreakdowns);

        return breakdowns.build();
    }


    /**
     * Write the final scoring output to a directory.
     *
     * @param directory Directory to write output to.
     * @throws IOException
     */
    @Override
    public void writeCorpusOutput(File directory) throws IOException {
        for (final Outputter outputter : outputters) {
            outputter.writeOutput(documentResults, directory);
        }

        // nobody was using this, I think...
        /*final FMeasureCounts corpusFMeasure = corpusConfusionMatrix.buildSummaryMatrix()
                .FMeasureVsAllOthers(PRESENT);
        ImprovementWriter.writeImprovements(directory, "Aggregate", corpusFMeasure,
                ImmutableMap.of("Aggregate", corpusConfusionMatrix.buildSummaryMatrix()));*/
    }

	private StrictStandardScoringObserver(final String name,
		final Predicate<AssessedResponse> ResponseCorrect, final HTMLErrorRecorder renderer,
        final Map<String, Function<TypeRoleFillerRealis, Symbol>> breakdowns,
        final Iterable<? extends Outputter> outputters)
    {
        super(name);
		this.ResponseCorrect = checkNotNull(ResponseCorrect);
		this.log = LoggerFactory.getLogger(name());
		this.renderer = checkNotNull(renderer);
        this.breakdownComputer = BreakdownComputer.create(breakdowns);
        this.outputters = ImmutableList.copyOf(outputters);
	}

    private void observeDocumentConfusionMatrix(final ProvenancedConfusionMatrix<TypeRoleFillerRealis> matrix) {
        documentResults.add(new DocumentResult(matrix, breakdownComputer.computeBreakdownSummaries(matrix)));
	}


    @Override
	public KBPAnswerSourceObserver answerSourceObserver(
			final SystemOutputAnswerSource<TypeRoleFillerRealis> systemOutputSource,
			final AnswerKeyAnswerSource<TypeRoleFillerRealis> answerKeyAnswerSource)
	{
		return new StrictStandardAnswerSourceObserver(systemOutputSource, answerKeyAnswerSource);
	}

    /****************************************** Inner classes *****************************/
    private class StrictStandardAnswerSourceObserver extends KBPAnswerSourceObserver {
        private final ProvenancedConfusionMatrix.Builder<TypeRoleFillerRealis> confusionMatrixBuilder;
        private final StringBuilder htmlOut;
        private final StringBuilder textOut;
        private final SystemOutputAnswerSource<TypeRoleFillerRealis> systemOutputSource;

        public StrictStandardAnswerSourceObserver(SystemOutputAnswerSource<TypeRoleFillerRealis> systemOutputSource, AnswerKeyAnswerSource<TypeRoleFillerRealis> answerKeyAnswerSource) {
            super(systemOutputSource, answerKeyAnswerSource);
            this.systemOutputSource = systemOutputSource;
            confusionMatrixBuilder = ProvenancedConfusionMatrix.builder();
            htmlOut = new StringBuilder();
            textOut = new StringBuilder();
        }

        @Override
        public void start() {
            htmlOut.append(renderer.preamble());
        }

        @Override
        public void annotatedSelectedResponse(final TypeRoleFillerRealis answerable, final Response response,
                                              final AssessedResponse annotationForSelected, final Set<AssessedResponse> allAssessments) {
            if (ResponseCorrect.apply(annotationForSelected)) {
                textOut.append("True Positive\n");
                confusionMatrixBuilder.record(PRESENT, PRESENT, answerable);
                htmlOut.append(renderer.correct(systemOutputSource.systemOutput().score(response)));
            } else {
                textOut.append("False positive. Response annotated in pool as ")
                        .append(annotationForSelected.assessment()).append("\n");
                confusionMatrixBuilder.record(PRESENT, ABSENT, answerable);
                htmlOut.append(renderer.vsAnnotated("kbp-false-positive-annotated", "False positive (annotated)", response,
                        systemOutputSource.systemOutput().score(response), annotationForSelected));

                checkForFalseNegative(answerable, allAssessments);
            }
        }

        private boolean checkForFalseNegative(final TypeRoleFillerRealis answerable, final Set<AssessedResponse> assessedResponses) {
            if (any(assessedResponses, ResponseCorrect)) {
                // if any correct answer was found for this equivalence class in the answer key,
                // then this is  a false negative
                textOut.append("FN: No correct system response present, but the following correct response is in the pool: ")
                        .append(Iterables.find(assessedResponses, ResponseCorrect)).append("\n");
                confusionMatrixBuilder.record(ABSENT, PRESENT, answerable);
                htmlOut.append(renderer.vsAnnotated("kbp-false-negative", "False negative", getFirst(assessedResponses, null).response(),
                        assessedResponses));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void unannotatedSelectedResponse(final TypeRoleFillerRealis answerable, final Response unannotated,
                                                Set<AssessedResponse> annotatedResponses) {
            textOut.append("No assessment for ").append(unannotated).append(", counting as wrong\n");
            confusionMatrixBuilder.record(PRESENT, ABSENT, answerable);
            htmlOut.append(renderer.vsAnnotated("kbp-false-positive-unannotated", "False positive (unannotated)", unannotated,
                    ImmutableList.of(systemOutputSource.systemOutput().score(unannotated)), answerKey().answers(answerable)));
            checkForFalseNegative(answerable, annotatedResponses);
        }

        @Override
        public void annotationsOnlyNonEmpty(final TypeRoleFillerRealis answerable,
                                            final Set<AssessedResponse> annotatedResponses) {
            if (checkForFalseNegative(answerable, annotatedResponses)) {
// pass - recording done within checkForFalseNegative
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
    }


    /**
     * Scoring results for a single document.
     */
    private static final class DocumentResult {
        public final ProvenancedConfusionMatrix<TypeRoleFillerRealis> confusionMatrix;
        public final Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> breakdownMatrices;

        public DocumentResult(ProvenancedConfusionMatrix<TypeRoleFillerRealis> confusionMatrix,
                              Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> breakdownMatrices)
        {
            this.confusionMatrix = checkNotNull(confusionMatrix);
            this.breakdownMatrices = ImmutableMap.copyOf(breakdownMatrices);
        }

        public static Function<DocumentResult, Map<String,BrokenDownSummaryConfusionMatrix<Symbol>>>
            GetBreakdownMatricesFunction = new Function<DocumentResult, Map<String, BrokenDownSummaryConfusionMatrix<Symbol>>>() {
            @Override
            public Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> apply(DocumentResult input) {
                return input.breakdownMatrices;
            }
        };
    }


    /******************************************* Output Code *******************************************/
    public interface Outputter {
        public void writeOutput(Iterable<DocumentResult> documentResults, File outputDirectory) throws IOException;
    }

    /********************************* Utility methods used by both outputter implementations ***********************/
    private static final Function<SummaryConfusionMatrix, FMeasureCounts> FmeasureVs(final Set<Symbol> positiveSymbols) {
        return new Function<SummaryConfusionMatrix, FMeasureCounts>() {
            @Override
            public FMeasureCounts apply(SummaryConfusionMatrix input) {
                return input.FMeasureVsAllOthers(positiveSymbols);
            }
        };
    }

    public static ImmutableMap<String, BrokenDownSummaryConfusionMatrix<Symbol>> combineBreakdowns(
            Iterator<Map<String, BrokenDownSummaryConfusionMatrix<Symbol>>> breakdowns) {


        final Map<String, BrokenDownSummaryConfusionMatrix.Builder<Symbol>> ret = Maps.newHashMap();
        while (breakdowns.hasNext()) {
            final Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> breakdown = breakdowns.next();
            for (final Map.Entry<String, BrokenDownSummaryConfusionMatrix<Symbol>> breakdownEntry : breakdown.entrySet()) {
                if (!ret.containsKey(breakdownEntry.getKey())) {
                    ret.put(breakdownEntry.getKey(), BrokenDownSummaryConfusionMatrix.<Symbol>builder(
                            Ordering.from(new SymbolUtils.ByString())));
                }
                ret.get(breakdownEntry.getKey()).combine(breakdownEntry.getValue());
            }
        }

        final ImmutableMap.Builder<String, BrokenDownSummaryConfusionMatrix<Symbol>> trueRet = ImmutableMap.builder();
        // return map in alphabetical order
        for (final String key : Ordering.natural().sortedCopy(ret.keySet())) {
            trueRet.put(key, ret.get(key).build());
        }
        return trueRet.build();
    }


    public static final class StandardOutputter implements Outputter {
        private final JacksonSerializer jackson = JacksonSerializer.forNormalJSON();

        private StandardOutputter() {
        }

        public void writeOutput(Iterable<DocumentResult> documentResults, File outputDirectory) throws IOException {
            final Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> straightResults =
                    combineBreakdowns(transform(documentResults, DocumentResult.GetBreakdownMatricesFunction).iterator());
            writeBreakdownsToFiles(straightResults, outputDirectory);
        }

        private void writeBreakdownsToFiles(Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> breakdowns,
                                           File directory) throws IOException
        {
            for (final Map.Entry<String, BrokenDownSummaryConfusionMatrix<Symbol>> printMode
                    : breakdowns.entrySet())
            {
                final String modeName = printMode.getKey();
                final File scoringBreakdownFilename = new File(directory, modeName);
                writeScoringBreakdown(modeName, printMode.getValue(), scoringBreakdownFilename);
            }
        }

        private void writeScoringBreakdown(String modeName, BrokenDownSummaryConfusionMatrix<Symbol> data,
                                           File outputFile) throws IOException
        {
            final StringBuilder fMeasures = new StringBuilder();
            final StringBuilder confusionMatrices = new StringBuilder();

            final ImmutableMap.Builder<String, Map<String, FMeasureCounts>> toSerialize = ImmutableMap.builder();

            fMeasures.append("BREAKDOWN BY ").append(modeName).append("\n");

            for (final Map.Entry<String, Collection<Symbol>> FMeasureSymbol : F_MEASURES_TO_PRINT.asMap().entrySet()) {
                final Map<String, FMeasureCounts> fMeasuresToPrint =
                        MapUtils.copyWithTransformedEntries(data.asMap(),
                                Functions.toStringFunction(), FmeasureVs(ImmutableSet.copyOf(FMeasureSymbol.getValue())));

                final FMeasureTableRenderer tableRenderer = FMeasureTableRenderer.create()
                        .setNameFieldLength(4 + MapUtils.longestKeyLength(fMeasuresToPrint))
                        .sortByErrorCountDescending();

                fMeasures.append("F-Measure vs ").append(FMeasureSymbol.getKey()).append("\n\n");
                fMeasures.append(tableRenderer.render(fMeasuresToPrint));
                // copy to ensure it can be serialized
                toSerialize.put(FMeasureSymbol.getKey(), ImmutableMap.copyOf(fMeasuresToPrint));
            }

            for (final Map.Entry<Symbol, SummaryConfusionMatrix> breakdown : data.asMap().entrySet()) {
                confusionMatrices.append("Confusion matrix for ").append(breakdown.getKey()).append("\n");
                confusionMatrices.append(breakdown.getValue().prettyPrint()).append("\n");
            }

            Files.asCharSink(outputFile, Charsets.UTF_8).write(confusionMatrices.toString()
                    + "\n\n\n" + fMeasures.toString());

            // also write this as JSON
            final File JSONFilename = new File(outputFile.getAbsolutePath() + ".json");

            // we make a copy to ensure what is written is Jackson-serializable
            jackson.serializeTo(toSerialize.build(), Files.asByteSink(JSONFilename));
        }
    }

    public static final class BootstrapOutputter implements Outputter {
        private final int bootstrapSeed;
        private final int numBootstrapSamples;

        public BootstrapOutputter(int bootstrapSeed, int numBootstrapSamples) {
            this.bootstrapSeed = bootstrapSeed;
            this.numBootstrapSamples = numBootstrapSamples;
            checkArgument(numBootstrapSamples > 0);
        }

        public void writeOutput(Iterable<DocumentResult> documentResults, File outputDirectory)
                throws IOException {
            // now we compute many "samples" of possible corpora based on our existing corpus. We score each of
            // these samples and compute confidence intervals from them
            final Random rng = new Random(bootstrapSeed);
            final Iterator<List<DocumentResult>> bootstrappedResults =
                    Iterators.limit(BootstrapIterator.forData(documentResults, rng), numBootstrapSamples);

            final List<Map<String, BrokenDownSummaryConfusionMatrix<Symbol>>> resultsForSamples = Lists.newArrayList();
            while (bootstrappedResults.hasNext()) {
                resultsForSamples.add(combineBreakdowns(
                        transform(bootstrappedResults.next(), DocumentResult.GetBreakdownMatricesFunction).iterator()));
            }

            writeSampledBreakdownsToFiles(combineMapsToMultimap(resultsForSamples), outputDirectory);
        }


        private void writeSampledBreakdownsToFiles(Multimap<String, BrokenDownSummaryConfusionMatrix<Symbol>> data,
                                                  File outputDirectory) throws IOException {
            for (final Map.Entry<String, Collection<BrokenDownSummaryConfusionMatrix<Symbol>>> printMode
                    : data.asMap().entrySet())
            {
                final String modeName = printMode.getKey();
                final File scoringBreakdownFilename = new File(outputDirectory, modeName + ".bootstrapped");
                writeSamplesScoringBreakdown(modeName, printMode.getValue(), scoringBreakdownFilename);
            }
        }

        private static final ImmutableList<Double> PERCENTILES_TO_PRINT =
                ImmutableList.of(0.01, 0.05, 0.25, 0.5, 0.75, 0.95, 0.99);

        private void writeSamplesScoringBreakdown(String modeName, Collection<BrokenDownSummaryConfusionMatrix<Symbol>> data,
                                                  File outputFile) throws IOException {
            final StringBuilder sb = new StringBuilder();

            sb.append("BREAKDOWN BY ").append(modeName).append("\n");

            for (final Map.Entry<String, Collection<Symbol>> FMeasureSymbol : F_MEASURES_TO_PRINT.asMap().entrySet()) {
                final Multimap<String, FMeasureCounts> fMeasuresToPrint = toFMeasures(data, FMeasureSymbol);

                final ImmutableMap.Builder<String, PercentileComputer.Percentiles> precisionPercentiles = ImmutableMap.builder();
                final ImmutableMap.Builder<String, PercentileComputer.Percentiles> recallPercentiles = ImmutableMap.builder();
                final ImmutableMap.Builder<String, PercentileComputer.Percentiles> fPercentiles = ImmutableMap.builder();

                final PercentileComputer nistComputer = PercentileComputer.nistPercentileComputer();

                for (final Map.Entry<String, Collection<FMeasureCounts>> entry : fMeasuresToPrint.asMap().entrySet()) {
                    final List<Double> precisions = Lists.newArrayList();
                    final List<Double> recalls = Lists.newArrayList();
                    final List<Double> fs = Lists.newArrayList();

                    for (final FMeasureCounts counts : entry.getValue()) {
                        precisions.add((double)counts.precision());
                        recalls.add((double)counts.recall());
                        fs.add((double) counts.F1());
                    }

                    precisionPercentiles.put(entry.getKey(), nistComputer.calculatePercentiles(precisions));
                    recallPercentiles.put(entry.getKey(), nistComputer.calculatePercentiles(recalls));
                    fPercentiles.put(entry.getKey(), nistComputer.calculatePercentiles(fs));
                }

                dumpPercentilesForMetric("Precision", precisionPercentiles.build(), FMeasureSymbol, sb);
                dumpPercentilesForMetric("Recall", recallPercentiles.build(), FMeasureSymbol, sb);
                dumpPercentilesForMetric("F1", fPercentiles.build(), FMeasureSymbol, sb);
            }

            Files.asCharSink(outputFile, Charsets.UTF_8).write(sb.toString());
        }

        private void dumpPercentilesForMetric(String metricName,
                ImmutableMap<String, PercentileComputer.Percentiles> percentiles,
                Map.Entry<String, Collection<Symbol>> FMeasureSymbol, StringBuilder output) {
            output.append(metricName).append(" vs ").append(FMeasureSymbol.getKey()).append("\n\n");
            output.append(renderLine("Name", PERCENTILES_TO_PRINT));
            output.append(Strings.repeat("*", 70)).append("\n");
            for (final Map.Entry<String, PercentileComputer.Percentiles> percentileEntry : percentiles.entrySet()) {
                output.append(renderLine(percentileEntry.getKey(),
                        percentileEntry.getValue().percentiles(PERCENTILES_TO_PRINT)));
            }
            output.append("\n\n\n");
        }

        private String renderLine(String name, List<Double> values) {
            final StringBuilder ret = new StringBuilder();

            ret.append(String.format("%20s", name));
            for (double val : values) {
                ret.append(String.format("%15.2f", 100.0*val));
            }
            ret.append("\n");
            return ret.toString();
        }

        private ImmutableMultimap<String, FMeasureCounts> toFMeasures(Collection<BrokenDownSummaryConfusionMatrix<Symbol>> data,
                                                                      Map.Entry<String, Collection<Symbol>> FMeasureSymbol) {
            final ImmutableMultimap.Builder<String, FMeasureCounts> ret = ImmutableMultimap.builder();

        /*
         MapUtils.copyWithTransformedEntries(data.asMap(),
                            Functions.toStringFunction(), FmeasureVs(FMeasureSymbol.getValue()));
         */
            final Function<SummaryConfusionMatrix, FMeasureCounts> scoringFunction = FmeasureVs(ImmutableSet.copyOf(FMeasureSymbol.getValue()));
            for (final BrokenDownSummaryConfusionMatrix<Symbol> dataPoint : data) {
                for (final Map.Entry<Symbol, SummaryConfusionMatrix> entry : dataPoint.asMap().entrySet()) {
                    ret.put(entry.getKey().toString(), scoringFunction.apply(entry.getValue()));
                }
            }

            return ret.build();
        }

        @MoveToBUECommon
        private static <K,V> ImmutableMultimap<K,V> combineMapsToMultimap(Iterable<? extends Map<K,V>> maps) {
            final ImmutableMultimap.Builder<K,V> ret = ImmutableMultimap.builder();

            for (final Map<K,V> map : maps) {
                ret.putAll(Multimaps.forMap(map));
            }

            return ret.build();
        }

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
