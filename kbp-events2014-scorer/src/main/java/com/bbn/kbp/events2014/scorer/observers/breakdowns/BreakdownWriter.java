package com.bbn.kbp.events2014.scorer.observers.breakdowns;

import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.diff.FMeasureTableRenderer;
import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public final class BreakdownWriter<ProvenanceType> {
    private final Map<String, Function<? super ProvenanceType, Symbol>> breakdownFunctions;
    private final Map<String, Set<Symbol>> FMeasuresToPrint = Maps.newHashMap();
    private final JacksonSerializer jackson = JacksonSerializer.forNormalJSON();

    private BreakdownWriter(Map<String, Function<ProvenanceType, Symbol>>
                                    breakdownFunctions)
    {
        final ImmutableMap.Builder<String, Function<? super ProvenanceType, Symbol>> builder = ImmutableMap.builder();
        builder.put("Aggregate", Functions.constant(Symbol.from("Aggregate")));
        builder.putAll(breakdownFunctions);
        this.breakdownFunctions = builder.build();
    }

    public static <ProvenanceType> BreakdownWriter create(Map<String, Function<ProvenanceType, Symbol>>
          breakdownFunctions)
    {
        return new BreakdownWriter(breakdownFunctions);
    }

    public BreakdownWriter addFMeasureToPrint(String name, Symbol s) {
        FMeasuresToPrint.put(name, ImmutableSet.of(s));
        return this;
    }

    public BreakdownWriter addFMeasureToPrint(String name, Iterable<Symbol> s) {
        FMeasuresToPrint.put(name, ImmutableSet.copyOf(s));
        return this;
    }


    public void writeBreakdownsToFiles(ProvenancedConfusionMatrix<ProvenanceType> data,
                                       File directory) throws IOException
    {
        final Map<String, Map<Symbol, ProvenancedConfusionMatrix<ProvenanceType>>> printModes =
                BreakdownFunctions.computeBreakdowns(data, breakdownFunctions);

        for (final Map.Entry<String, Map<Symbol, ProvenancedConfusionMatrix<ProvenanceType>>> printMode
                : printModes.entrySet())
        {
            final String modeName = printMode.getKey();
            final Map<String, SummaryConfusionMatrix> summaryData =
                    MapUtils.copyWithTransformedEntries(printMode.getValue(),
                            Functions.toStringFunction(),
                            ProvenancedConfusionMatrix.<ProvenanceType>ToSummaryMatrix());

            final File scoringBreakdownFilename = new File(directory, modeName);

            writeScoringBreakdown(modeName, summaryData, scoringBreakdownFilename);
        }
    }

    private void writeScoringBreakdown(String modeName, Map<String, SummaryConfusionMatrix> data,
                                                                    File outputFile) throws IOException
    {
        final StringBuilder fMeasures = new StringBuilder();
        final StringBuilder confusionMatrices = new StringBuilder();

        final ImmutableMap.Builder<String, Map<String, FMeasureCounts>> toSerialize = ImmutableMap.builder();

        fMeasures.append("BREAKDOWN BY ").append(modeName).append("\n");

        for (final Map.Entry<String, Set<Symbol>> FMeasureSymbol : FMeasuresToPrint.entrySet()) {
            final Map<String, FMeasureCounts> fMeasuresToPrint = Maps.transformValues(data, FmeasureVs(FMeasureSymbol.getValue()));

            final FMeasureTableRenderer tableRenderer = FMeasureTableRenderer.create()
                    .setNameFieldLength(4 + MapUtils.longestKeyLength(fMeasuresToPrint));

            fMeasures.append("F-Measure vs ").append(FMeasureSymbol.getKey()).append("\n\n");
            fMeasures.append(tableRenderer.render(fMeasuresToPrint));
            // copy to ensure it can be serialized
            toSerialize.put(FMeasureSymbol.getKey(), ImmutableMap.copyOf(fMeasuresToPrint));
        }

        for (final Map.Entry<String, SummaryConfusionMatrix> breakdown : data.entrySet()) {
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

    private static final Function<SummaryConfusionMatrix, FMeasureCounts> FmeasureVs(final Set<Symbol> positiveSymbols) {
        return new Function<SummaryConfusionMatrix, FMeasureCounts>() {
            @Override
            public FMeasureCounts apply(SummaryConfusionMatrix input) {
                return input.FMeasureVsAllOthers(positiveSymbols);
            }
        };
    }


}
