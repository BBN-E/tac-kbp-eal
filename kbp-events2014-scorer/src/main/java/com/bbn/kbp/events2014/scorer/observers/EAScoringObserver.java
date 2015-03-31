package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.diff.FMeasureTableRenderer;
import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.io.GZIPByteSink;
import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.scoring.Scoreds;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.scorer.SystemOutputEquivalenceClasses;
import com.bbn.kbp.events2014.scorer.observers.breakdowns.BreakdownComputer;
import com.bbn.kbp.events2014.scorer.observers.breakdowns.BreakdownFunctions;
import com.bbn.kbp.events2014.scorer.observers.breakdowns.BrokenDownSummaryConfusionMatrix;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.HTMLErrorRecorder;

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.cursors.DoubleCursor;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.io.ByteSink;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;

public final class EAScoringObserver extends KBPScoringObserver<TypeRoleFillerRealis> {

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

  public static KBPScoringObserver<TypeRoleFillerRealis> standardScorer(
      final HTMLErrorRecorder renderer,
      Iterable<? extends Outputter> outputters) {
    return new EAScoringObserver("Standard",
        AssessedResponse.IsCorrectUpToInexactJustifications,
        renderer, BreakdownFunctions.StandardBreakdowns, outputters);
  }

  public static KBPScoringObserver<TypeRoleFillerRealis> standardScorer(
      final HTMLErrorRecorder renderer,
      final Map<String, Function<TypeRoleFillerRealis, Symbol>> additionalBreakdowns,
      final Iterable<? extends Outputter> outputters) {
    return new EAScoringObserver("Standard",
        AssessedResponse.IsCorrectUpToInexactJustifications,
        renderer, plusStandardBreakdowns(additionalBreakdowns), outputters);
  }

  public static Outputter createStandardOutputter() {
    return new StandardOutputter();
  }

  public static Outputter createBootstrapOutputter(int seed, int numSamples) {
    return new BootstrapOutputter(seed, numSamples);
  }

  public static Outputter createEquivalenceClassIDOutputter() {
    return new EquivalenceClassIDOutputter();
  }

  private static ImmutableMap<String, Function<TypeRoleFillerRealis, Symbol>>
  plusStandardBreakdowns(Map<String, Function<TypeRoleFillerRealis, Symbol>> additionalBreakdowns) {
    final ImmutableMap.Builder<String, Function<TypeRoleFillerRealis, Symbol>> breakdowns =
        ImmutableMap.builder();

    breakdowns.putAll(BreakdownFunctions.StandardBreakdowns);
    breakdowns.putAll(additionalBreakdowns);

    return breakdowns.build();
  }


  /**
   * Write the final scoring output to a directory.
   *
   * @param directory Directory to write output to.
   */
  @Override
  public void writeCorpusOutput(File directory) throws IOException {
    for (final Outputter outputter : outputters) {
      outputter.writeOutput(documentResults, directory);
    }
  }

  @Override
  public void observeDocument(final EventArgScoringAlignment<TypeRoleFillerRealis> scoringAlignment,
      final File perDocLogDir) throws IOException {
    final ProvenancedConfusionMatrix.Builder<TypeRoleFillerRealis> confusionMatrixBuilder =
        ProvenancedConfusionMatrix.builder();
    final StringBuilder htmlOut = new StringBuilder();
    final StringBuilder textOut = new StringBuilder();
    final SystemOutputEquivalenceClasses<TypeRoleFillerRealis> systemOutputSource;

    htmlOut.append(renderer.preamble());

    for (final TypeRoleFillerRealis truePositive : scoringAlignment
        .truePositiveEquivalenceClasses()) {
      //textOut.append("True Positive\n");
      confusionMatrixBuilder.record(PRESENT, PRESENT, truePositive);
      //htmlOut.append(renderer.correct(systemOutputSource.systemOutput().score(response)));
    }

    for (final TypeRoleFillerRealis falsePositive : scoringAlignment
        .falsePositiveEquivalenceClasses()) {
      confusionMatrixBuilder.record(PRESENT, ABSENT, falsePositive);
      /*textOut.append("False positive. Response annotated in pool as ")
          .append(annotationForSelected.assessment()).append("\n");
      confusionMatrixBuilder.record(PRESENT, ABSENT, answerable);
      htmlOut.append(renderer
          .vsAnnotated("kbp-false-positive-annotated", "False positive (annotated)", response,
              systemOutputSource.systemOutput().score(response), annotationForSelected));*/

      //checkForFalseNegative(answerable, allAssessments);
    }

    for (final TypeRoleFillerRealis falseNegative : scoringAlignment
        .falseNegativeEquivalenceClasses()) {
      confusionMatrixBuilder.record(ABSENT, PRESENT, falseNegative);
      /*textOut.append(
          "FN: No correct system response present, but the following correct response is in the pool: ")
          .append(Iterables.find(assessedResponses, ResponseCorrect)).append("\n");
      htmlOut.append(renderer.vsAnnotated("kbp-false-negative", "False negative",
          getFirst(assessedResponses, null).response(),
          assessedResponses));*/
    }

    for (final TypeRoleFillerRealis unassessed : scoringAlignment.unassessed()) {
      // we count unassessed as false positives, if we can count them at all
      confusionMatrixBuilder.record(PRESENT, ABSENT, unassessed);

      /*textOut.append("No assessment for ").append(unannotated).append(", counting as wrong\n");
      htmlOut.append(renderer
          .vsAnnotated("kbp-false-positive-unannotated", "False positive (unannotated)",
              unannotated,
              ImmutableList.of(systemOutputSource.systemOutput().score(unannotated)),
              answerKey().answers(answerable)));
      checkForFalseNegative(answerable, annotatedResponses);*/
      // NOTE: something can be unassessed and FN
    }

    final ProvenancedConfusionMatrix<TypeRoleFillerRealis> confusionMatrix =
        confusionMatrixBuilder.build();
    observeDocumentConfusionMatrix(confusionMatrix);

    final StringBuilder sb = new StringBuilder();
    sb.append("===== Confusion matrix for ").append(name()).append(" =====\n");

    final SummaryConfusionMatrix summaryConfusionMatrix = confusionMatrix.buildSummaryMatrix();
    sb.append(summaryConfusionMatrix.prettyPrint()).append("\n");
    sb.append(confusionMatrix.prettyPrint()).append("\n");

    Files.asCharSink(new File(perDocLogDir, "confusionMatrix.txt"), Charsets.UTF_8)
        .write(sb.toString());

    final String html = htmlOut.toString();
    if (!html.isEmpty()) {
      Files.asCharSink(new File(perDocLogDir, "errors.html"), Charsets.UTF_8).write(html);
    }
  }


  private EAScoringObserver(final String name,
      final Predicate<AssessedResponse> ResponseCorrect, final HTMLErrorRecorder renderer,
      final Map<String, Function<TypeRoleFillerRealis, Symbol>> breakdowns,
      final Iterable<? extends Outputter> outputters) {
    super(name);
    this.ResponseCorrect = checkNotNull(ResponseCorrect);
    this.log = LoggerFactory.getLogger(name());
    this.renderer = checkNotNull(renderer);
    this.breakdownComputer = BreakdownComputer.create(breakdowns);
    this.outputters = ImmutableList.copyOf(outputters);
  }

  private void observeDocumentConfusionMatrix(
      final ProvenancedConfusionMatrix<TypeRoleFillerRealis> matrix) {
    documentResults
        .add(new DocumentResult(matrix, breakdownComputer.computeBreakdownSummaries(matrix)));
  }


  /**
   * Scoring results for a single document.
   */
  private static final class DocumentResult {

    public final ProvenancedConfusionMatrix<TypeRoleFillerRealis> confusionMatrix;
    public final Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> breakdownMatrices;

    public DocumentResult(ProvenancedConfusionMatrix<TypeRoleFillerRealis> confusionMatrix,
        Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> breakdownMatrices) {
      this.confusionMatrix = checkNotNull(confusionMatrix);
      this.breakdownMatrices = ImmutableMap.copyOf(breakdownMatrices);
    }

    public static Function<DocumentResult, Map<String, BrokenDownSummaryConfusionMatrix<Symbol>>>
        GetBreakdownMatricesFunction =
        new Function<DocumentResult, Map<String, BrokenDownSummaryConfusionMatrix<Symbol>>>() {
          @Override
          public Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> apply(DocumentResult input) {
            return input.breakdownMatrices;
          }
        };
  }


  /**
   * **************************************** Output Code ******************************************
   */
  public interface Outputter {

    public void writeOutput(Iterable<DocumentResult> documentResults, File outputDirectory)
        throws IOException;
  }

  /**
   * ****************************** Utility methods used by both outputter implementations
   * **********************
   */
  private static final Function<SummaryConfusionMatrix, FMeasureCounts> FmeasureVs(
      final Set<Symbol> positiveSymbols) {
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
      for (final Map.Entry<String, BrokenDownSummaryConfusionMatrix<Symbol>> breakdownEntry : breakdown
          .entrySet()) {
        if (!ret.containsKey(breakdownEntry.getKey())) {
          ret.put(breakdownEntry.getKey(), BrokenDownSummaryConfusionMatrix.<Symbol>builder(
              Ordering.from(new SymbolUtils.ByString())));
        }
        ret.get(breakdownEntry.getKey()).combine(breakdownEntry.getValue());
      }
    }

    final ImmutableMap.Builder<String, BrokenDownSummaryConfusionMatrix<Symbol>> trueRet =
        ImmutableMap.builder();
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

    public void writeOutput(Iterable<DocumentResult> documentResults, File outputDirectory)
        throws IOException {
      final Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> straightResults =
          combineBreakdowns(
              transform(documentResults, DocumentResult.GetBreakdownMatricesFunction).iterator());
      writeBreakdownsToFiles(straightResults, outputDirectory);
    }

    private void writeBreakdownsToFiles(
        Map<String, BrokenDownSummaryConfusionMatrix<Symbol>> breakdowns,
        File directory) throws IOException {
      for (final Map.Entry<String, BrokenDownSummaryConfusionMatrix<Symbol>> printMode
          : breakdowns.entrySet()) {
        final String modeName = printMode.getKey();
        final File scoringBreakdownFilename = new File(directory, modeName);
        writeScoringBreakdown(modeName, printMode.getValue(), scoringBreakdownFilename);
      }
    }

    private void writeScoringBreakdown(String modeName,
        BrokenDownSummaryConfusionMatrix<Symbol> data,
        File outputFile) throws IOException {
      final StringBuilder fMeasures = new StringBuilder();
      final StringBuilder confusionMatrices = new StringBuilder();

      final ImmutableMap.Builder<String, Map<String, FMeasureCounts>> toSerialize =
          ImmutableMap.builder();

      fMeasures.append("BREAKDOWN BY ").append(modeName).append("\n");

      for (final Map.Entry<String, Collection<Symbol>> FMeasureSymbol : F_MEASURES_TO_PRINT.asMap()
          .entrySet()) {
        final Map<String, FMeasureCounts> fMeasuresToPrint =
            MapUtils.copyWithTransformedEntries(data.asMap(),
                Functions.toStringFunction(),
                FmeasureVs(ImmutableSet.copyOf(FMeasureSymbol.getValue())));

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

      final List<Map<String, BrokenDownSummaryConfusionMatrix<Symbol>>> resultsForSamples =
          Lists.newArrayList();
      while (bootstrappedResults.hasNext()) {
        resultsForSamples.add(combineBreakdowns(
            transform(bootstrappedResults.next(), DocumentResult.GetBreakdownMatricesFunction)
                .iterator()));
      }

      final ImmutableMultimap<String, BrokenDownSummaryConfusionMatrix<Symbol>>
          resultsByBreakdownType =
          combineMapsToMultimap(resultsForSamples);
      writeSampledBreakdownsToFiles(resultsByBreakdownType, outputDirectory);
    }


    private void writeSampledBreakdownsToFiles(
        Multimap<String, BrokenDownSummaryConfusionMatrix<Symbol>> data,
        File outputDirectory) throws IOException {
      for (final Map.Entry<String, Collection<BrokenDownSummaryConfusionMatrix<Symbol>>> printMode
          : data.asMap().entrySet()) {
        final String modeName = printMode.getKey();
        final File scoringBreakdownFilename = new File(outputDirectory, modeName + ".bootstrapped");
        final File rawDir = new File(outputDirectory, modeName + ".bootstrapped.raw");
        rawDir.mkdir();
        writeSamplesScoringBreakdown(modeName, printMode.getValue(),
            scoringBreakdownFilename, rawDir);
      }
    }

    private static final ImmutableList<Double> PERCENTILES_TO_PRINT =
        ImmutableList.of(0.01, 0.05, 0.25, 0.5, 0.75, 0.95, 0.99);

    private void writeSamplesScoringBreakdown(String modeName,
        Collection<BrokenDownSummaryConfusionMatrix<Symbol>> data,
        File outputFile, File rawDir) throws IOException {
      final StringBuilder sb = new StringBuilder();

      sb.append("BREAKDOWN BY ").append(modeName).append("\n");

      for (final Map.Entry<String, Collection<Symbol>> FMeasureSymbol : F_MEASURES_TO_PRINT.asMap()
          .entrySet()) {
        final Multimap<String, FMeasureCounts> fMeasuresToPrint = toFMeasures(data, FMeasureSymbol);

        final ImmutableMap.Builder<String, PercentileComputer.Percentiles> precisionPercentiles =
            ImmutableMap.builder();
        final ImmutableMap.Builder<String, PercentileComputer.Percentiles> recallPercentiles =
            ImmutableMap.builder();
        final ImmutableMap.Builder<String, PercentileComputer.Percentiles> fPercentiles =
            ImmutableMap.builder();

        final PercentileComputer nistComputer = PercentileComputer.nistPercentileComputer();

        for (final Map.Entry<String, Collection<FMeasureCounts>> entry : fMeasuresToPrint.asMap()
            .entrySet()) {
          final DoubleArrayList precisions = new DoubleArrayList(entry.getValue().size());
          final DoubleArrayList recalls = new DoubleArrayList(entry.getValue().size());
          final DoubleArrayList fs = new DoubleArrayList(entry.getValue().size());

          for (final FMeasureCounts counts : entry.getValue()) {
            precisions.add((double) counts.precision());
            recalls.add((double) counts.recall());
            fs.add((double) counts.F1());
          }

          final String rawPrefix = entry.getKey() + "_" + FMeasureSymbol.getKey();
          writeArray(precisions, new File(rawDir, rawPrefix + ".precisions.txt"));
          writeArray(recalls, new File(rawDir, rawPrefix + ".recalls.txt"));
          writeArray(fs, new File(rawDir, rawPrefix + ".fs.txt"));
          precisionPercentiles.put(entry.getKey(),
              nistComputer.calculatePercentilesAdoptingData(precisions.toArray()));
          recallPercentiles.put(entry.getKey(),
              nistComputer.calculatePercentilesAdoptingData(recalls.toArray()));
          fPercentiles.put(entry.getKey(),
              nistComputer.calculatePercentilesAdoptingData(fs.toArray()));
        }

        dumpPercentilesForMetric("Precision", precisionPercentiles.build(), FMeasureSymbol, sb);
        dumpPercentilesForMetric("Recall", recallPercentiles.build(), FMeasureSymbol, sb);
        dumpPercentilesForMetric("F1", fPercentiles.build(), FMeasureSymbol, sb);
      }

      Files.asCharSink(outputFile, Charsets.UTF_8).write(sb.toString());
    }

    private void writeArray(DoubleArrayList numbers, File file) throws IOException {
      final ByteSink sink = GZIPByteSink.gzipCompress(Files.asByteSink(file));
      final PrintWriter out = new PrintWriter(sink.asCharSink(Charsets.UTF_8).openBufferedStream());
      for (final DoubleCursor cursor : numbers) {
        out.println(cursor.value);
      }
      out.close();
      ;
    }

    private void dumpPercentilesForMetric(String metricName,
        ImmutableMap<String, PercentileComputer.Percentiles> percentiles,
        Map.Entry<String, Collection<Symbol>> FMeasureSymbol, StringBuilder output) {
      output.append(metricName).append(" vs ").append(FMeasureSymbol.getKey()).append("\n\n");
      output.append(renderLine("Name", PERCENTILES_TO_PRINT));
      output.append(Strings.repeat("*", 70)).append("\n");
      for (final Map.Entry<String, PercentileComputer.Percentiles> percentileEntry : percentiles
          .entrySet()) {
        output.append(renderLine(percentileEntry.getKey(),
            percentileEntry.getValue().percentiles(PERCENTILES_TO_PRINT)));
      }
      output.append("\n\n\n");
    }

    private String renderLine(String name, List<Double> values) {
      final StringBuilder ret = new StringBuilder();

      ret.append(String.format("%20s", name));
      for (double val : values) {
        ret.append(String.format("%15.2f", 100.0 * val));
      }
      ret.append("\n");
      return ret.toString();
    }

    private ImmutableMultimap<String, FMeasureCounts> toFMeasures(
        Collection<BrokenDownSummaryConfusionMatrix<Symbol>> data,
        Map.Entry<String, Collection<Symbol>> FMeasureSymbol) {
      final ImmutableMultimap.Builder<String, FMeasureCounts> ret = ImmutableMultimap.builder();

        /*
         MapUtils.copyWithTransformedEntries(data.asMap(),
                            Functions.toStringFunction(), FmeasureVs(FMeasureSymbol.getValue()));
         */
      final Function<SummaryConfusionMatrix, FMeasureCounts> scoringFunction =
          FmeasureVs(ImmutableSet.copyOf(FMeasureSymbol.getValue()));
      for (final BrokenDownSummaryConfusionMatrix<Symbol> dataPoint : data) {
        for (final Map.Entry<Symbol, SummaryConfusionMatrix> entry : dataPoint.asMap().entrySet()) {
          ret.put(entry.getKey().toString(), scoringFunction.apply(entry.getValue()));
        }
      }

      return ret.build();
    }

    @MoveToBUECommon
    private static <K, V> ImmutableMultimap<K, V> combineMapsToMultimap(
        Iterable<? extends Map<K, V>> maps) {
      final ImmutableMultimap.Builder<K, V> ret = ImmutableMultimap.builder();

      for (final Map<K, V> map : maps) {
        ret.putAll(Multimaps.forMap(map));
      }

      return ret.build();
    }

  }

  /**
   * Outputs a list of the IDs of the true and false positive equivalence classes for later
   * analysis.
   */
  public static final class EquivalenceClassIDOutputter implements Outputter {

    private enum IDListType {
      TruePositives {
        @Override
        public Symbol leftKey() {
          return PRESENT;
        }

        @Override
        public Symbol rightKey() {
          return PRESENT;
        }
      },

      FalsePositive {
        @Override
        public Symbol leftKey() {
          return PRESENT;
        }

        @Override
        public Symbol rightKey() {
          return ABSENT;
        }
      };

      public abstract Symbol leftKey();

      public abstract Symbol rightKey();
    }

    @Override
    public void writeOutput(Iterable<DocumentResult> documentResults,
        File outputDirectory) throws IOException {
      // we use an ImmutableSets as values to get deterministic iteration order
      final Map<IDListType, ImmutableSet.Builder<String>> equivalenceClassesByType =
          Maps.newHashMap();

      // initialize the map value set builders
      for (final IDListType idListType : IDListType.values()) {
        equivalenceClassesByType.put(idListType, ImmutableSet.<String>builder());
      }

      // for each document...
      for (final DocumentResult documentResult : documentResults) {
        // for true positives and false positives...
        for (final IDListType idListType : IDListType.values()) {
          final ImmutableSet.Builder<String> idsForType = equivalenceClassesByType.get(idListType);
          List<TypeRoleFillerRealis> confusionCellContents =
              documentResult.confusionMatrix.cell(idListType.leftKey(), idListType.rightKey());
          // record the Ids of all answers of that type
          for (final TypeRoleFillerRealis equivClass : confusionCellContents) {
            idsForType.add(equivClass.uniqueIdentifier());
          }
        }
      }

      for (final IDListType idListType : IDListType.values()) {
        final File outputFile = new File(outputDirectory, idListType.name() + ".ids.txt");
        Files.asCharSink(outputFile, Charsets.UTF_8).writeLines(
            equivalenceClassesByType.get(idListType).build());
      }
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

      public abstract FMeasureCounts improve(FMeasureCounts baseCounts,
          FMeasureCounts improvingCounts);
    }

    private static void writeImprovements(final File directory, final String modeName,
        final FMeasureCounts corpusFMeasure,
        final Map<String, SummaryConfusionMatrix> data) throws IOException {
      writeImprovements(modeName, corpusFMeasure, data, ImprovementMode.PERFECT_PRECISION,
          new File(directory, modeName + ".improvementsWithPerfectPrecision"));
      writeImprovements(modeName, corpusFMeasure, data, ImprovementMode.PERFECT_RECALL,
          new File(directory, modeName + ".improvementsWithPerfectRecall"));
      writeImprovements(modeName, corpusFMeasure, data, ImprovementMode.PERFECT_EVERYTHING,
          new File(directory, modeName + ".improvementsWithPerfectEverything"));
    }

    private static void writeImprovements(final String modeName,
        final FMeasureCounts corpusFMeasure,
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

      sb.append("Score changes for " + modeName + " when improvement " + improvementMode.toString()
          + " is applied\n");

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
