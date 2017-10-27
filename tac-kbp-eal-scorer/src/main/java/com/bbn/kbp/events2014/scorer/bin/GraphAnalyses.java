package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.io.GZIPByteSource;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.gnuplot.Axis;
import com.bbn.bue.gnuplot.BoxPlot;
import com.bbn.bue.gnuplot.ClusteredBarChart;
import com.bbn.bue.gnuplot.Color;
import com.bbn.bue.gnuplot.GnuPlotRenderer;
import com.bbn.bue.gnuplot.Grid;
import com.bbn.bue.gnuplot.LineStyle;
import com.bbn.bue.gnuplot.LineType;
import com.bbn.bue.gnuplot.NormalGrid;
import com.bbn.bue.gnuplot.Palette;
import com.bbn.bue.gnuplot.Point2D;
import com.bbn.bue.gnuplot.ScatterData;
import com.bbn.bue.gnuplot.ScatterPlot;
import com.bbn.bue.gnuplot.StackedBarChart;
import com.bbn.kbp.events2014.scorer.Aggregate2015ScoringResult;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Functions.compose;

/**
 * @author Yee Seng Chan
 */
public final class GraphAnalyses {

  private GraphAnalyses() {
    throw new UnsupportedOperationException();
  }

  public static void main(String[] argv) {
    // we wrap the main method in this way to
    // ensure a non-zero return value on failure
    try {
      trueMain(argv);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void trueMain(String[] argv) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    //final File scoringRoot = params.getExistingDirectory("scoringDirectory");
    final File outputDir = params.getCreatableDirectory("outputDir");
    // you need to use a new version of GnuPlot, such as
    // /nfs/mercury-06/u14/apps/bin/gnuplot
    final GnuPlotRenderer renderer = GnuPlotRenderer.createForGnuPlotExecutable(
        params.getExistingFile("gnuplotBin"));



    final ScoreType scoreType = params.getEnum("scoreType", ScoreType.class);
    scoreType.plotScores(outputDir, renderer, params);
  }

  private static void plotOverallScores(final File scoringRoot, final File outputDir,
      final GnuPlotRenderer renderer, final List<String> systemNames) throws IOException {
    // a Function mapping a system's name to the file with its bootstrapped overall scores
    final Function<String, File> systemToOverallScoreFile = new Function<String, File>() {
      @Override
      public File apply(final String systemName) {
        return new File(new File(new File(scoringRoot, systemName), "aggregate"),
            "aggregate.bootstrapped.json");
      }
    };

    // a map from systems to the bootstrapped samples of their overall scores
    final Map<String, List<Double>> overallScoreMap = FluentIterable.from(systemNames)
        .toMap(compose(deserializeAndTransformFunction(EXTRACT_OVERALL_SCORE),
            systemToOverallScoreFile));

    boxPlot(overallScoreMap, renderer, "Overall score by System", "Overall score",
        new File(outputDir, "scoresGraph.png"));
  }

  private static void plotArgumentScores(final File scoringRoot, final File outputDir,
      final GnuPlotRenderer renderer, final List<String> systemNames) throws IOException {
    // a Function mapping a system's name to the file with its bootstrapped overall scores
    final Function<String, File> systemToOverallScoreFile = new Function<String, File>() {
      @Override
      public File apply(final String systemName) {
        return new File(new File(new File(scoringRoot, systemName), "aggregate"),
            "aggregate.bootstrapped.json");
      }
    };

    // a map from systems to the bootstrapped samples of their overall scores
    final Map<String, List<Double>> overallScoreMap = FluentIterable.from(systemNames)
        .toMap(compose(deserializeAndTransformFunction(EXTRACT_ARGUMENT_SCORE),
            systemToOverallScoreFile));

    boxPlot(overallScoreMap, renderer, "Argument score by System", "Argument score",
        new File(outputDir, "argumentScoresGraph.png"));
  }

  private static void plotArgumentF1(final File scoringRoot, final File outputDir,
      final GnuPlotRenderer renderer, final List<String> systemNames) throws IOException {
    // a Function mapping a system's name to the file with its bootstrapped overall scores
    final Function<String, File> systemToOverallScoreFile = new Function<String, File>() {
      @Override
      public File apply(final String systemName) {
        return new File(new File(new File(scoringRoot, systemName), "aggregate"),
            "aggregate.bootstrapped.json");
      }
    };

    // a map from systems to the bootstrapped samples of their overall scores
    final Map<String, List<Double>> overallScoreMap = FluentIterable.from(systemNames)
        .toMap(compose(deserializeAndTransformFunction(EXTRACT_ARGUMENT_F1),
            systemToOverallScoreFile));

    boxPlot(overallScoreMap, renderer, "Argument F1 by System", "Argument F1",
        new File(outputDir, "argumentF1Graph.png"));
  }

  private static void plotLinkingScores(final File outputDir,
      final GnuPlotRenderer renderer, final Parameters params) throws IOException {

    final File systemScoreRoot = params.getExistingDirectory("linking.system.scoreDirectory");
    final File baselineScoreRoot = params.getExistingDirectory("linking.baseline.scoreDirectory");
    final File maxScoreRoot = params.getExistingDirectory("linking.max.scoreDirectory");

    final List<String> systemNames = gatherSystemNames(systemScoreRoot);

    // system bootstrap scores
    final Map<String, List<Double>> bootstrapScoreMap = extractLinkingScores(systemScoreRoot,
        "aggregate.bootstrapped.json", systemNames);
    boxPlot(bootstrapScoreMap, renderer, "Linking bootstrap score by System", "Linking score",
        new File(outputDir, "linkingBootstrapScoresGraph.png"));

    // system scores
    final Map<String, Double> systemScoreMap = extractScore(systemScoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_LINKING_SCORE);
    // baseline scores
    final Map<String, Double> baselineScoreMap = extractScore(baselineScoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_LINKING_SCORE);
    // max scores
    final Map<String, Double> maxScoreMap = extractScore(maxScoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_LINKING_SCORE);

    final ImmutableList.Builder<Map<String, Double>> scoresMaps = ImmutableList.builder();
    scoresMaps.add(baselineScoreMap);
    scoresMaps.add(systemScoreMap);
    scoresMaps.add(maxScoreMap);

    final ImmutableMap<String, List<Double>> linkingScores =
        combineNonBootstrapScores(systemNames, scoresMaps.build());

    final ImmutableList.Builder<String> clusterNames = ImmutableList.builder();
    clusterNames.add("baseline-linking");
    clusterNames.add("system-linking");
    clusterNames.add("max-linking");

    final ImmutableList.Builder<Color> clusterColors = ImmutableList.builder();
    clusterColors.add(Color.fromHexString("#ff0000"));  // red
    clusterColors.add(Color.fromHexString("#000000"));  // black
    clusterColors.add(Color.fromHexString("#0000ff"));  // blue

    renderClusteredBarChart(linkingScores,
        new File(outputDir, "linkingScoresGraph.png"), renderer,
        "Linking scores", "Systems", "Linking score", clusterNames.build(), Palette.from(clusterColors.build()));

  }

  private static void plotNeutralizeRealisCorefScores(final File outputDir,
      final GnuPlotRenderer renderer, final Parameters params) throws IOException {

    final File withRealisScoreRoot = params.getExistingDirectory("scoreDirectory.withRealis");
    final File neutralizeRealisScoreRoot = params.getExistingDirectory(
        "scoreDirectory.neutralizeRealis");
    //final File neutralizeCorefScoreRoot = params.getExistingDirectory(
    //    "scoreDirectory.neutralizeCoref");
    final File neutralizeRealisCorefScoreRoot = params.getExistingDirectory(
        "scoreDirectory.neutralizeRealisCoref");

    final List<String> systemNames = gatherSystemNames(withRealisScoreRoot);


    // ==== Overall scores ====
    final Map<String, Double> withRealisScoreMap = extractScore(withRealisScoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_OVERALL_SCORE);
    final Map<String, Double> neutraliseRealisScoreMap = extractScore(neutralizeRealisScoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_OVERALL_SCORE);
    //final Map<String, Double> neutraliseCorefScoreMap = extractScore(neutralizeCorefScoreRoot,
    //    "aggregateScore.json", systemNames, EXTRACT_OVERALL_SCORE);
    final Map<String, Double> neutraliseRealisCorefScoreMap = extractScore(neutralizeRealisCorefScoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_OVERALL_SCORE);

    final ImmutableList.Builder<Map<String, Double>> scoresMaps = ImmutableList.builder();
    scoresMaps.add(withRealisScoreMap);
    scoresMaps.add(neutraliseRealisScoreMap);
    //scoresMaps.add(neutraliseCorefScoreMap);
    scoresMaps.add(neutraliseRealisCorefScoreMap);

    final ImmutableMap<String, List<Double>> scores =
        combineNonBootstrapScores(systemNames, scoresMaps.build());


    // ==== Argument scores ====
    final Map<String, Double> withRealisArgScoreMap = extractScore(withRealisScoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_ARGUMENT_SCORE);
    final Map<String, Double> neutraliseRealisArgScoreMap = extractScore(neutralizeRealisScoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_ARGUMENT_SCORE);
    //final Map<String, Double> neutraliseCorefArgScoreMap = extractScore(neutralizeCorefScoreRoot,
    //    "aggregateScore.json", systemNames, EXTRACT_ARGUMENT_SCORE);
    final Map<String, Double> neutraliseRealisCorefArgScoreMap = extractScore(neutralizeRealisCorefScoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_ARGUMENT_SCORE);

    final ImmutableList.Builder<Map<String, Double>> argScoresMaps = ImmutableList.builder();
    argScoresMaps.add(withRealisArgScoreMap);
    argScoresMaps.add(neutraliseRealisArgScoreMap);
    //argScoresMaps.add(neutraliseCorefArgScoreMap);
    argScoresMaps.add(neutraliseRealisCorefArgScoreMap);

    final ImmutableMap<String, List<Double>> argScores =
        combineNonBootstrapScores(systemNames, argScoresMaps.build());


    // ==== set chart segment names and colors ====
    final ImmutableList.Builder<String> segmentNames = ImmutableList.builder();
    segmentNames.add("with-realis");
    segmentNames.add("neutralize-realis");
    //segmentNames.add("neutralize-coref");
    segmentNames.add("neutralize-realisCoref");

    final ImmutableList.Builder<Color> segmentColors = ImmutableList.builder();
    segmentColors.add(Color.fromHexString("#ff0000"));  // red
    segmentColors.add(Color.fromHexString("#000000"));  // black
    //segmentColors.add(Color.fromHexString("#52b5d4"));
    segmentColors.add(Color.fromHexString("#0000ff"));  // blue

    // ==== now plot the charts ====
    renderClusteredBarChart(scores,
        new File(outputDir, "neutralizeRealisCorefOverallGraph.png"), renderer,
        "Impact of neutralizing realis coref", "Systems", "Aggregate overall score",
        segmentNames.build(), Palette.from(segmentColors.build()));

    renderClusteredBarChart(argScores,
        new File(outputDir, "neutralizeRealisCorefArgumentGraph.png"), renderer,
        "Impact of neutralizing realis coref", "Systems", "Aggregate argument score", segmentNames.build(), Palette.from(segmentColors.build()));
  }

  private static void plotPRScores(final File outputDir,
      final GnuPlotRenderer renderer, final Parameters params) throws IOException {

    final File scoreRoot = params.getExistingDirectory("scoreDirectory");

    final List<String> systemNames = gatherSystemNames(scoreRoot);

    final Map<String, Double> precisionMap = extractScore(scoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_ARGUMENT_PRECISION);

    final Map<String, Double> recallMap = extractScore(scoreRoot,
        "aggregateScore.json", systemNames, EXTRACT_ARGUMENT_RECALL);

    final ImmutableList.Builder<Map<String, Double>> scoresMaps = ImmutableList.builder();
    scoresMaps.add(recallMap);
    scoresMaps.add(precisionMap);

    // for each system: index0:recall, index1:precision
    final ImmutableMap<String, List<Double>> scores =
        combineNonBootstrapScores(systemNames, scoresMaps.build());

    renderScatterChart(scores,
        new File(outputDir, "argumentRPGraph.png"), renderer,
        "Argument recall precision scores", "Recall", "Precision");

  }

  private static void plotPerEventScores(final File scoringRoot, final File outputDir,
      final GnuPlotRenderer renderer, final List<String> systemNames) throws IOException {

    final List<String> eventTypes = gatherSystemNames(new File(new File(scoringRoot, systemNames.get(0)), "byEventTypes"));

    for(final String eventType : eventTypes) {
      // a Function mapping a system's name to the file with its bootstrapped overall scores
      final Function<String, File> systemToOverallScoreFile = new Function<String, File>() {
        @Override
        public File apply(final String systemName) {
          return new File(new File(new File(new File(scoringRoot, systemName), "byEventTypes"), eventType),
              "aggregateScore.json");
        }
      };


      // a map from systems to the bootstrapped samples of their overall scores
      final Map<String, List<Double>> overallScoreMap = FluentIterable.from(systemNames)
          .toMap(compose(deserializeAndTransformFunction(EXTRACT_OVERALL_SCORE),
              systemToOverallScoreFile));

      boxPlot(overallScoreMap, renderer, "Overall score for "+eventType, "Overall score",
          new File(outputDir, eventType+"_overallGraph.png"));


      // a map from systems to the bootstrapped samples of their argument scores
      final Map<String, List<Double>> argumentScoreMap = FluentIterable.from(systemNames)
          .toMap(compose(deserializeAndTransformFunction(EXTRACT_ARGUMENT_SCORE),
              systemToOverallScoreFile));

      boxPlot(overallScoreMap, renderer, "Argument score for "+eventType, "Argument score",
          new File(outputDir, eventType+"_argumentGraph.png"));
    }
  }



  private static ImmutableMap<String, List<Double>> combineNonBootstrapScores(final List<String> systemNames,
      final ImmutableList<Map<String, Double>> scoresMaps) {
    final ImmutableMap.Builder<String, List<Double>> ret = ImmutableMap.builder();

    for(final String systemName : systemNames) {
      List<Double> scores = Lists.newArrayList();

      for(int i=0; i<scoresMaps.size(); i++) {
        scores.add(scoresMaps.get(i).get(systemName));
      }

      ret.put(systemName, scores);
    }

    return ret.build();
  }

  private static Map<String, List<Double>> extractLinkingScores(final File scoringRoot, final String filename,
      final List<String> systemNames) throws IOException {
    // a Function mapping a system's name to the file with its bootstrapped overall scores
    final Function<String, File> systemToOverallScoreFile = new Function<String, File>() {
      @Override
      public File apply(final String systemName) {
        return new File(new File(new File(scoringRoot, systemName), "aggregate"),
            filename);
      }
    };

    // a map from systems to the bootstrapped samples of their overall scores
    final Map<String, List<Double>> overallScoreMap = FluentIterable.from(systemNames)
        .toMap(compose(deserializeAndTransformFunction(EXTRACT_LINKING_SCORE),
            systemToOverallScoreFile));

    return overallScoreMap;
  }

  private static Map<String, Double> extractScore(final File scoringRoot, final String filename,
      final List<String> systemNames,
      final Function<Aggregate2015ScoringResult, Double> scoreExtractionFunction) throws IOException {
    // a Function mapping a system's name to the file with its bootstrapped overall scores
    final Function<String, File> systemToOverallScoreFile = new Function<String, File>() {
      @Override
      public File apply(final String systemName) {
        return new File(new File(new File(scoringRoot, systemName), "aggregate"),
            filename);
      }
    };

    // a map from systems to the bootstrapped samples of their overall scores
    final Map<String, Double> overallScoreMap = FluentIterable.from(systemNames)
        .toMap(compose(deserializeSingleAndTransformFunction(scoreExtractionFunction),
            systemToOverallScoreFile));

    return overallScoreMap;
  }



  private static List<String> gatherSystemNames(final File scoringRoot) {
    final ImmutableList.Builder<String> ret = ImmutableList.builder();

    for (final File f : scoringRoot.listFiles()) {
      if (f.isDirectory()) {
        ret.add(f.getName());
      }
    }
    return Ordering.natural().sortedCopy(ret.build());
  }

  private static final Axis X_AXIS = Axis.xAxis().setLabel("Participant").rotateLabels().build();
  private static final Color MEDIUM_GREY = Color.fromHexString("#777777");
  private static final LineStyle GREY_DASHED =
      LineStyle.builder().setColor(MEDIUM_GREY).setLineType(LineType.DASHED)
          .setWidth(2.0).build();

  private static final Grid GRID = NormalGrid.builder().sendToBack()
      .hideMinorXLines().hideMinorXLines()
      .setMajorLineStyle(GREY_DASHED).build();

  static void boxPlot(Map<String, List<Double>> systemToScores, GnuPlotRenderer renderer,
      String title, String yAxisLabel, File outputFile) throws IOException {
    final BoxPlot.Whiskers whiskers = BoxPlot.Whiskers.builder()
        .setExtentMode(BoxPlot.Whiskers.Fraction.of(0.95)).build();

    final Axis yAxis =
        Axis.yAxis().setLabel(yAxisLabel).setRange(Range.closed(0.0, 60.0)).build();
    final BoxPlot.Builder plot = BoxPlot.builder().hideKey()
        .setTitle(title)
        .setXAxis(X_AXIS)
        .setYAxis(yAxis)
        .setGrid(GRID)
        .setWhiskers(whiskers);
    for (final Map.Entry<String, List<Double>> entry : systemToScores.entrySet()) {
      plot.addDataset(
          BoxPlot.Dataset.createCopyingData(entry.getKey(), Doubles.toArray(entry.getValue())));
    }
    renderer.renderTo(plot.build(), outputFile);
  }

  static void renderClusteredBarChart(final ImmutableMap<String, List<Double>> scores, final File outFile,
      final GnuPlotRenderer renderer,
      final String chartTitle, final String xAxisLabel, final String yAxisLabel,
      final ImmutableList<String> clusterNames, final Palette palette)
      throws IOException {

    final ImmutableSet.Builder<Double> scoreSetBuilder = ImmutableSet.builder();
    for(final List<Double> scoreList : scores.values()) {
      scoreSetBuilder.addAll(scoreList);
    }
    final int maxCount = (int)Math.ceil(Ordering.natural().max(scoreSetBuilder.build()));

    final Axis X_AXIS = Axis.xAxis().setLabel(xAxisLabel).rotateLabels().build();
    final Axis Y_AXIS = Axis.yAxis().setLabel(yAxisLabel).setRange(
        Range.closed(0.0, (double) roundUpNearestFactor(maxCount))).build();

    final ClusteredBarChart.Builder chartBuilder = ClusteredBarChart.builder()
        .setTitle(chartTitle)
        .setXAxis(X_AXIS).setYAxis(Y_AXIS)
        .setBoxWidth(0.9)
        .withPalette(palette);

    for(int i=0; i<clusterNames.size(); i++) {
      chartBuilder.addBarCluster(clusterNames.get(i));
    }

    for(final Map.Entry<String, List<Double>> entry : scores.entrySet()) {
      final String systemName = entry.getKey();
      final List<Double> systemScores = entry.getValue();

      chartBuilder.addClusteredBar(ClusteredBarChart.ClusteredBar.create(systemName, systemScores));
    }

    renderer.renderTo(chartBuilder.build(), outFile);
  }

  static void renderStackedBarChart(final ImmutableMap<String, List<Double>> scores, final File outFile,
      final GnuPlotRenderer renderer,
      final String chartTitle, final String xAxisLabel, final String yAxisLabel,
      final ImmutableList<String> segmentNames)
      throws IOException {

    final Axis X_AXIS = Axis.xAxis().setLabel(xAxisLabel).rotateLabels().build();
    final Axis Y_AXIS = Axis.yAxis().setLabel(yAxisLabel).build();

    final StackedBarChart.Builder chartBuilder = StackedBarChart.builder()
        .setTitle(chartTitle)
        .setXAxis(X_AXIS).setYAxis(Y_AXIS);

    for(final String segmentName : segmentNames) {
      chartBuilder.addBarSegment(segmentName);
    }

    for(final Map.Entry<String, List<Double>> entry : scores.entrySet()) {
      final String systemName = entry.getKey();
      final List<Double> systemScores = entry.getValue();

      chartBuilder.addStackedBar(StackedBarChart.StackedBar.create(systemName, systemScores));
    }

    renderer.renderTo(chartBuilder.build(), outFile);
  }

  static void renderScatterChart(final ImmutableMap<String, List<Double>> scores, final File outFile,
      final GnuPlotRenderer renderer,
      final String chartTitle, final String xAxisLabel, final String yAxisLabel)
      throws IOException {

    final ScatterPlot.Builder chartBuilder = ScatterPlot.builder()
        .setTitle(chartTitle)
        .setXLabel(xAxisLabel).setYLabel(yAxisLabel)
        .setXRange(Range.closed(0.0, 100.0))
        .setYRange(Range.closed(0.0, 100.0))
        .setPointSize(2);

    final Iterator<Color> colorList = Palette.from(ImmutableList.copyOf(colors)).infinitePaletteLoop().iterator();

    for(final Map.Entry<String, List<Double>> entry : scores.entrySet()) {
      final String systemName = entry.getKey();
      final List<Double> systemScores = entry.getValue();

      List<Point2D> points = Lists.newArrayList();
      points.add(Point2D.fromXY(systemScores.get(0), systemScores.get(1)));

      chartBuilder.addScatter(ScatterData.fromPoints(points).withTitle(systemName)
          .withColor(colorList.next()).build());
    }

    renderer.renderTo(chartBuilder.build(), outFile);
  }

  @SuppressWarnings("unchecked")
  static <T, V> Function<File, List<V>> deserializeAndTransformFunction(
      final Function<T, V> transformer)
      throws IOException {
    final JacksonSerializer jacksonSerializer = JacksonSerializer.json().prettyOutput().build();

    return new Function<File, List<V>>() {
      @Override
      public List<V> apply(final File f) {
        final FluentIterable<T> from;
        try {
          from = FluentIterable.from((Iterable<T>) jacksonSerializer
              .deserializeFrom(GZIPByteSource.fromCompressed(Files.asByteSource(f))));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        return from.transform(transformer).toList();
      }
    };
  }

  @SuppressWarnings("unchecked")
  static <T, V> Function<File, V> deserializeSingleAndTransformFunction(
      final Function<T, V> transformer)
      throws IOException {
    final JacksonSerializer jacksonSerializer = JacksonSerializer.json().prettyOutput().build();

    return new Function<File, V>() {
      @Override
      public V apply(final File f) {
        final T from;
        try {
          from = (T) jacksonSerializer
              .deserializeFrom(GZIPByteSource.fromCompressed(Files.asByteSource(f)));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        return transformer.apply(from);
      }
    };
  }

  public enum ScoreType {
    AGGREGATE {
      @Override
      public void plotScores(final File outputDir,
          final GnuPlotRenderer renderer, final Parameters params) throws IOException {
        final File scoringRoot = params.getExistingDirectory("scoringDirectory");
        final List<String> systemNames = gatherSystemNames(scoringRoot);
        plotOverallScores(scoringRoot, outputDir, renderer, systemNames);
      }
    }, ARGUMENT {
      @Override
      public void plotScores(final File outputDir,
          final GnuPlotRenderer renderer, final Parameters params) throws IOException {
        final File scoringRoot = params.getExistingDirectory("scoringDirectory");
        final List<String> systemNames = gatherSystemNames(scoringRoot);
        plotArgumentScores(scoringRoot, outputDir, renderer, systemNames);
      }
    }, F1 {
      @Override
      public void plotScores(final File outputDir,
          final GnuPlotRenderer renderer, final Parameters params) throws IOException {
        final File scoringRoot = params.getExistingDirectory("scoringDirectory");
        final List<String> systemNames = gatherSystemNames(scoringRoot);
        plotArgumentF1(scoringRoot, outputDir, renderer, systemNames);
      }
    }, LINKING {
      @Override
      public void plotScores(final File outputDir,
          final GnuPlotRenderer renderer, final Parameters params) throws IOException {
        plotLinkingScores(outputDir, renderer, params);
      }
    }, NEUTRALIZE_REALISCOREF {
      @Override
      public void plotScores(final File outputDir,
          final GnuPlotRenderer renderer, final Parameters params) throws IOException {
        plotNeutralizeRealisCorefScores(outputDir, renderer, params);
      }
    }, PR {
      @Override
      public void plotScores(final File outputDir,
          final GnuPlotRenderer renderer, final Parameters params) throws IOException {
        plotPRScores(outputDir, renderer, params);
      }
    }, PER_EVENT {
      @Override
      public void plotScores(final File outputDir,
          final GnuPlotRenderer renderer, final Parameters params) throws IOException {
        final File scoringRoot = params.getExistingDirectory("scoringDirectory");
        final List<String> systemNames = gatherSystemNames(scoringRoot);
        plotPerEventScores(scoringRoot, outputDir, renderer, systemNames);
      }
    };

    public abstract void plotScores(final File outputDir,
        final GnuPlotRenderer renderer, final Parameters params) throws IOException;
  }



  private static final Function<Aggregate2015ScoringResult, Double> EXTRACT_OVERALL_SCORE =
      new Function<Aggregate2015ScoringResult, Double>() {
        @Override
        public Double apply(final Aggregate2015ScoringResult input) {
          return input.overall();
        }
      };

  private static final Function<Aggregate2015ScoringResult, Double> EXTRACT_ARGUMENT_SCORE =
      new Function<Aggregate2015ScoringResult, Double>() {
        @Override
        public Double apply(final Aggregate2015ScoringResult input) {
          return input.argument().overall();
        }
      };

  private static final Function<Aggregate2015ScoringResult, Double> EXTRACT_LINKING_SCORE =
      new Function<Aggregate2015ScoringResult, Double>() {
        @Override
        public Double apply(final Aggregate2015ScoringResult input) {
          return input.linking().overall();
        }
      };

  private static final Function<Aggregate2015ScoringResult, Double> EXTRACT_ARGUMENT_F1 =
      new Function<Aggregate2015ScoringResult, Double>() {
        @Override
        public Double apply(final Aggregate2015ScoringResult input) {
          final double p = input.argument().precision();
          final double r = input.argument().recall();
          if(p>0 && r>0) {
            return (2*p*r)/(p+r);
          } else {
            return 0.0;
          }
        }
      };

  private static final Function<Aggregate2015ScoringResult, Double> EXTRACT_ARGUMENT_PRECISION =
      new Function<Aggregate2015ScoringResult, Double>() {
        @Override
        public Double apply(final Aggregate2015ScoringResult input) {
          return input.argument().precision();
        }
      };

  private static final Function<Aggregate2015ScoringResult, Double> EXTRACT_ARGUMENT_RECALL =
      new Function<Aggregate2015ScoringResult, Double>() {
        @Override
        public Double apply(final Aggregate2015ScoringResult input) {
          return input.argument().recall();
        }
      };

  private static final Color[] colors = {Color.fromHexString("#ff0000"),
      Color.fromHexString("#ffa500"),Color.fromHexString("#000000"),Color.fromHexString("#00ffff"),
      Color.fromHexString("#0000ff"),Color.fromHexString("#ee82ee")};

  private static int roundUpNearestFactor(int n) {
    final int factor = n < 100? 10 : 100;

    if((n % factor) == 0) {
      return n;
    } else {
      return (factor - n % factor) + n;
    }
  }

}
