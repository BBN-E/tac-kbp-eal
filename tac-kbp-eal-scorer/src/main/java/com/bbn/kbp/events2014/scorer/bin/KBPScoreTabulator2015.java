package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.io.GZIPByteSource;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.kbp.events2014.scorer.Aggregate2015ScoringResult;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This should be merged with the 2014 version at some point.
 */
public class KBPScoreTabulator2015 {

  private static final Logger log = LoggerFactory.getLogger(KBPScoreTabulator2015.class);
  public static final FilenameFilter IS_DIRECTORY = new FilenameFilter() {
    @Override
    public boolean accept(File current, String name) {
      return new File(current, name).isDirectory();
    }
  };

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

  public static String tableHeader(String dataset) {
    final String[] tableHeader = {"System config", "TP", "FP", "FN", "ArgP", "ArgR", "ArgScore"};
    final StringBuilder sb = new StringBuilder();
    // write "pilot-manual-subset (Standard scoring):" heading
    sb.append("<h0>\n");
    sb.append(dataset + " (2015 scoring):");
    sb.append("</h0>\n");
    sb.append("<br><br>\n");
    sb.append("<table cellpadding=\"4\" border=\"1\">\n");
    sb.append("\t<tr>\n");
    for (String cell : tableHeader) {
      sb.append("\t\t<th>\n");
      sb.append("\t\t").append(cell).append("\n");
      sb.append("\t\t</th>\n");
    }
    sb.append("\t</tr>\n");
    return sb.toString();
  }

  private static void writeOverallScoreTables(final String dataset, final File datasetScoreDir,
      final List<String> configurations, String baselineName,
      final JacksonSerializer jacksonSerializer,
      final OutputStreamWriter writer) throws IOException {
    final ImmutableMap.Builder<String, FMeasureCounts> allFMeasureCountsMapB =
        ImmutableMap.builder();

    writer.write(tableHeader(dataset));

    Optional<Double> baselineScore = Optional.absent();
    final Map<String, Aggregate2015ScoringResult> scoresByConfig = Maps.newHashMap();
    for (String configuration : configurations) {
      final File scoreDir =
          new File(new File(new File(datasetScoreDir, configuration), "score2015"),
              "aggregate");
      final File jsonFile = new File(scoreDir, "aggregateScore.json");
      log.info("Loading scores from {}", jsonFile);
      final Aggregate2015ScoringResult scores =
          (Aggregate2015ScoringResult) jacksonSerializer.deserializeFrom(
              GZIPByteSource.fromCompressed(Files.asByteSource(jsonFile)));

      scoresByConfig.put(configuration, scores);

      if (configuration.equals(baselineName)) {
        baselineScore = Optional.of(scores.argument().overall());
      }
    }

    final Iterable<Map.Entry<String, Aggregate2015ScoringResult>> sortedByScore =
        MapUtils.<String, Aggregate2015ScoringResult>byValueOrdering(
            Ordering.natural().onResultOf(new Function<Aggregate2015ScoringResult, Double>() {
              @Override
              public Double apply(final Aggregate2015ScoringResult input) {
                return input.argument().overall();
              }
            })).reverse().immutableSortedCopy(scoresByConfig.entrySet());

    for (Map.Entry<String, Aggregate2015ScoringResult> entry : sortedByScore) {
      writer.write(row(entry.getKey(), entry.getValue(), baselineScore, baselineName));
    }

    writer.write(tableEnd());
  }


  private static void writePerEventTypeTables(final String dataset, final String eventType,
      final File datasetScoreDir,
      final List<String> configurations, final String baselineName,
      final JacksonSerializer jacksonSerializer, final OutputStreamWriter writer)
      throws IOException {
    writer.write(tableHeader(dataset + " : " + eventType));

    Optional<Double> baselineScore = Optional.absent();
    final Map<String, Aggregate2015ScoringResult> scoresByConfig = Maps.newHashMap();
    for (String configuration : configurations) {
      final File scoreDir = new File(new File(new File(
          new File(datasetScoreDir, configuration), "score2015"), "byEventTypes"), eventType);
      final File jsonFile = new File(scoreDir, "aggregateScore.json");
      log.info("Loading scores from {}", jsonFile);
      final Aggregate2015ScoringResult scores =
          (Aggregate2015ScoringResult) jacksonSerializer.deserializeFrom(
              GZIPByteSource.fromCompressed(Files.asByteSource(jsonFile)));

      scoresByConfig.put(configuration, scores);

      if (configuration.equals(baselineName)) {
        baselineScore = Optional.of(scores.argument().overall());
      }
    }

    final Iterable<Map.Entry<String, Aggregate2015ScoringResult>> sortedByScore =
        MapUtils.<String, Aggregate2015ScoringResult>byValueOrdering(
            Ordering.natural().onResultOf(new Function<Aggregate2015ScoringResult, Comparable>() {
              @Override
              public Comparable apply(final Aggregate2015ScoringResult input) {
                return input.argument().overall();
              }
            })).reverse().immutableSortedCopy(scoresByConfig.entrySet());

    for (Map.Entry<String, Aggregate2015ScoringResult> entry : sortedByScore) {
      writer.write(row(entry.getKey(), entry.getValue(), baselineScore, baselineName));
    }

    writer.write(tableEnd());
  }

  private static String row(String configuration, Aggregate2015ScoringResult scores,
      Optional<Double> baselineScore, String baselineName) {
    final StringBuilder sb = new StringBuilder();
    sb.append("\t<tr>\n");
    sb.append(cell(configuration, false));

    final double tp = scores.argument().truePositives();
    final double fp = scores.argument().falsePositives();
    final double fn = scores.argument().falseNegatives();

    final double p = scores.argument().precision();
    final double r = scores.argument().recall();
    final double overallScore = scores.argument().overall();

    sb.append(cell(String.format("%5.0f", tp), false));
    sb.append(cell(String.format("%5.0f", fp), false));
    sb.append(cell(String.format("%5.0f", fn), false));
    sb.append(cell(String.format("%5.1f", p), true));
    sb.append(cell(String.format("%5.1f", r), true));
    sb.append(cell(String.format("%5.1f", overallScore), true));
    sb.append("\t</tr>\n");
    return sb.toString();
  }

  public static String cell(String value, boolean isEmphasized) {
    final StringBuilder sb = new StringBuilder();
    sb.append(isEmphasized ? "\t\t<td style=\"font-weight:bold\">\n" : "\t\t<td>\n");
    sb.append("\t\t\t").append(value).append("\n");
    sb.append("\t\t</td>\n");
    return sb.toString();
  }


  public static String tableEnd() {
    final StringBuilder sb = new StringBuilder();
    sb.append("</table>\n");
    sb.append("<br><br>\n");
    return sb.toString();
  }


  private static void trueMain(String[] args) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(args[0]));
    final File masterAnnotationRepo = params.getExistingDirectory("annotationRepo");
    final File scoreTrackerDirectory = params.getExistingDirectory("scoreTrackerDirectory");
    final String baselineName = params.getString("baselineConfig");
    final File outputHTMLFile = params.getCreatableFile("outputHTMLFile");

    final JacksonSerializer jacksonSerializer = JacksonSerializer.forNormalJSON();

    final File datasetsDir = new File(masterAnnotationRepo, "datasets");

    try {
      OutputStreamWriter writer =
          new OutputStreamWriter(new FileOutputStream(outputHTMLFile), "UTF-8");

      writer.write("<html><body>");
      writer.write("<br><br>");

      final List<String> datasets =
          Files.readLines(new File(datasetsDir, "datasets.list"), Charsets.UTF_8);
      for (String dataset : datasets) {
        File datasetScoreDir = new File(scoreTrackerDirectory, dataset);

        if (!datasetScoreDir.exists()) {
          log.info("Dataset score directory does not exist: " + datasetScoreDir);
          continue;
        }

        // find the system configurations
        String[] configurationDirs = datasetScoreDir.list(new FilenameFilter() {
          @Override
          public boolean accept(File current, String name) {
            return new File(current, name).isDirectory();
          }
        });
        List<String> configurations = Arrays.asList(configurationDirs);
        Collections.sort(configurations);
        writeOverallScoreTables(dataset, datasetScoreDir, configurations, baselineName,
            jacksonSerializer,
            writer);
      }

      for (String dataset : datasets) {
        File datasetScoreDir = new File(scoreTrackerDirectory, dataset);

        if (!datasetScoreDir.exists()) {
          log.info("Dataset score directory does not exist: " + datasetScoreDir);
          continue;
        }

        // find the system configurations
        String[] configurationDirs = datasetScoreDir.list(IS_DIRECTORY);
        List<String> configurations = Arrays.asList(configurationDirs);
        Collections.sort(configurations);

        for (final String eventType : gatherEventTypes(datasetScoreDir, configurations)) {
          writePerEventTypeTables(dataset, eventType, datasetScoreDir, configurations, baselineName,
              jacksonSerializer,
              writer);
        }
      }

      writer.write("</body></html>");
      writer.close();

    } catch (IOException e) {
      log.error("Exception: {}", e);
      System.exit(1);
    }
  }

  private static List<String> gatherEventTypes(final File dir, List<String> configurations) {
    final Set<String> ret = Sets.newHashSet();
    for (final String config : configurations) {
      ret.addAll(ImmutableList.copyOf(
          new File(new File(new File(dir, config), "score2015"), "byEventTypes")
              .list(IS_DIRECTORY)));
    }
    return Ordering.natural().immutableSortedCopy(ret);
  }

}
