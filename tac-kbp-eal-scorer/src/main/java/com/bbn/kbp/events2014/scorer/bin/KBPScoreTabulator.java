package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KBPScoreTabulator {

  private KBPScoreTabulator() {
    throw new UnsupportedOperationException();
  }

  private static final Logger log = LoggerFactory.getLogger(KBPScoreTabulator.class);

  private static String row(String configuration, FMeasureCounts fMeasureCounts,
      Optional<FMeasureCounts> baselineFMeasureCounts, String baselineName) {
    final StringBuilder sb = new StringBuilder();
    sb.append("\t<tr>\n");
    sb.append(cell(configuration, false));

    final String fMeasure;
    if (baselineFMeasureCounts.isPresent() && !baselineName.equals(configuration)) {
      final double delta = 100.0 * (fMeasureCounts.F1() - baselineFMeasureCounts.get().F1());
      final String deltaString;
      if (delta > 0) {
        deltaString = String.format(" (%+5.1f)", delta);
      } else {
        deltaString = "";
      }
      fMeasure = String.format("%5.1f%s", 100.0 * fMeasureCounts.F1(), deltaString);
    } else {
      fMeasure = String.format("%5.1f", fMeasureCounts.F1() * 100);
    }

    sb.append(cell(fMeasure, true));
    sb.append(cell(String.format("%5.1f", fMeasureCounts.precision() * 100), true));
    sb.append(cell(String.format("%5.1f", fMeasureCounts.recall() * 100), true));
    sb.append(cell(Double.toString(fMeasureCounts.truePositives()), false));
    sb.append(cell(Double.toString(fMeasureCounts.falsePositives()), false));
    sb.append(cell(Double.toString(fMeasureCounts.falseNegatives()), false));
    sb.append("\t</tr>\n");
    return sb.toString();
  }

  public static String tableHeader(String dataset) {
    final String[] tableHeader = {"System config", "F1", "P", "R", "TP", "FP", "FN"};
    final StringBuilder sb = new StringBuilder();
    // write "pilot-manual-subset (Standard scoring):" heading
    sb.append("<h0>\n");
    sb.append(dataset + " (Standard scoring):");
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

  public static String tableEnd() {
    final StringBuilder sb = new StringBuilder();
    sb.append("</table>\n");
    sb.append("<br><br>\n");
    return sb.toString();
  }

  public static String cell(String value, boolean isEmphasized) {
    final StringBuilder sb = new StringBuilder();
    sb.append(isEmphasized ? "\t\t<td style=\"font-weight:bold\">\n" : "\t\t<td>\n");
    sb.append("\t\t\t").append(value).append("\n");
    sb.append("\t\t</td>\n");
    return sb.toString();
  }

  public static Map<String, String> getConfigurationDescriptions(File masterAnnotationRepo)
      throws IOException {
    Map<String, String> configDescriptions = new HashMap<String, String>();
    File outputStoreDir = new File(masterAnnotationRepo, "outputStores");
    if (!outputStoreDir.exists()) {
      throw new FileNotFoundException(
          String.format("Output store dir %s does not exist", outputStoreDir));
    }
    String[] configDirs = outputStoreDir.list(new FilenameFilter() {
      @Override
      public boolean accept(File current, String name) {
        return new File(current, name).isDirectory();
      }
    });
    for (String config : configDirs) {
      log.info("Getting configuration description for {}", config);
      final File descFile = new File(new File(outputStoreDir, config), "description");
      if (!descFile.exists()) {
        throw new FileNotFoundException(
            String.format("Could not find description for configuration %s in %s",
                config, descFile));
      }
      String description = Iterables.getFirst(Files.readLines(descFile, Charsets.UTF_8), "");
      configDescriptions.put(config, description);
    }
    return configDescriptions;
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

  private static void trueMain(String[] args) {
        /* See: /nfs/mercury-04/u10/kbp/masterAnnotationRepository */
    final File masterAnnotationRepo = new File(args[0]);

        /* See: /nfs/mercury-04/u10/kbp/scoreTracker/expts/scoreTracker-2014-05-12_14-09-49 as an example */
    final File scoreTrackerDirectory = new File(args[1]);

    final String baselineName = "emnlp2015-baseline";

    final File outputHTMLFile = new File(args[2]);
    outputHTMLFile.getParentFile().mkdirs();

    final JacksonSerializer jacksonSerializer = JacksonSerializer.forNormalJSON();

    // one table per dataset
    // figure out what the datasets are
    final File datasetsDir = new File(masterAnnotationRepo, "datasets");

    try {
      final Map<String, String> systemDescriptions =
          getConfigurationDescriptions(masterAnnotationRepo);
      OutputStreamWriter writer =
          new OutputStreamWriter(new FileOutputStream(outputHTMLFile), "UTF-8");

      writer.write("<html><body>");
      writer.write("<br><br>");

      List<String> datasets =
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
        writeBrokenDownScoreTables(dataset, datasetScoreDir, "Type.json", "by event type",
            configurations, baselineName, jacksonSerializer,
            writer);
        writeBrokenDownScoreTables(dataset, datasetScoreDir, "Role.json", "by event role",
            configurations, baselineName, jacksonSerializer,
            writer);
      }

      writer.write(buildDescriptionTable(systemDescriptions));

      writer.write("</body></html>");
      writer.close();

    } catch (IOException e) {
      log.error("Exception: {}", e);
      System.exit(1);
    }

  }


  private static void writeBrokenDownScoreTables(final String dataset, final File datasetScoreDir,
      String jsonFile, String chartName,
      final List<String> configurations, final String baselineName,
      final JacksonSerializer jacksonSerializer,
      final OutputStreamWriter writer) throws IOException {
    final ImmutableTable.Builder<String, String, FMeasureCounts> byEventTypeScoresB =
        ImmutableTable.builder();


    Optional<FMeasureCounts> baselineFMeasureCounts = Optional.absent();
    for (String configuration : configurations) {
      File scoreDir = new File(datasetScoreDir, configuration);
      scoreDir = new File(scoreDir, "score2014");
      File standardDir = new File(scoreDir, "Standard");
      File scoreJsonFile = new File(standardDir, jsonFile);
      log.info("Loading {} scores from {}", chartName, scoreJsonFile);
      @SuppressWarnings("unchecked")
      final Map<String, Map<String, FMeasureCounts>> fMeasureCountsMap =
          (Map<String, Map<String, FMeasureCounts>>) jacksonSerializer
              .deserializeFrom(Files.asByteSource(scoreJsonFile));

      for (final Map.Entry<String, FMeasureCounts> entry : fMeasureCountsMap.get("Present").entrySet()) {
        final String eventType = entry.getKey();
        final FMeasureCounts scores = entry.getValue();
        byEventTypeScoresB.put(eventType, configuration, scores);
      }
    }

    final ImmutableTable<String, String, FMeasureCounts> byEventTypeScores =
        byEventTypeScoresB.build();

    for (final Map.Entry<String, Map<String, FMeasureCounts>> entry : byEventTypeScores
        .rowMap().entrySet()) {
      final String eventType = entry.getKey();
      writer.write(tableHeader(dataset + " - " + eventType));
      final Iterable<Map.Entry<String, FMeasureCounts>> configsByFMeasure =
          MapUtils.<String, FMeasureCounts>byValueOrdering(
              FMeasureCounts.byF1Ordering().reverse())
              .immutableSortedCopy(entry.getValue().entrySet());

      for (Map.Entry<String, FMeasureCounts> configScore : configsByFMeasure) {
        writer.write(row(configScore.getKey(), configScore.getValue(),
            Optional.fromNullable(entry.getValue().get(baselineName)), baselineName));
      }

      writer.write(tableEnd());
    }
  }

  private static void writeOverallScoreTables(final String dataset, final File datasetScoreDir,
      final List<String> configurations, String baselineName, final JacksonSerializer jacksonSerializer,
      final OutputStreamWriter writer) throws IOException {
    final ImmutableMap.Builder<String, FMeasureCounts> allFMeasureCountsMapB =
        ImmutableMap.builder();

    writer.write(tableHeader(dataset));

    Optional<FMeasureCounts> baselineFMeasureCounts = Optional.absent();
    for (String configuration : configurations) {
      File scoreDir = new File(datasetScoreDir, configuration);
      scoreDir = new File(scoreDir, "score2014");
      File standardDir = new File(scoreDir, "Standard");
      File scoreJsonFile = new File(standardDir, "Aggregate.json");
      log.info("Loading scores from {}", scoreJsonFile);
      final Map<String, Map<String, FMeasureCounts>> fMeasureCountsMap =
          (Map<String, Map<String, FMeasureCounts>>) jacksonSerializer
              .deserializeFrom(Files.asByteSource(scoreJsonFile));

      final FMeasureCounts fMeasureCounts = fMeasureCountsMap.get("Present").get("Aggregate");
      allFMeasureCountsMapB.put(configuration, fMeasureCounts);
      if (configuration.equals("baseline")) {
        baselineFMeasureCounts = Optional.of(fMeasureCounts);
      }
    }

    final ImmutableMap<String, FMeasureCounts> allFMeasureCounts =
        allFMeasureCountsMapB.build();
    final Iterable<Map.Entry<String, FMeasureCounts>> sortedByFMeasure =
        MapUtils.<String, FMeasureCounts>byValueOrdering(
            FMeasureCounts.byF1Ordering().reverse())
            .immutableSortedCopy(allFMeasureCounts.entrySet());

    for (Map.Entry<String, FMeasureCounts> entry : sortedByFMeasure) {
      writer.write(row(entry.getKey(), entry.getValue(), baselineFMeasureCounts, baselineName));
    }

    writer.write(tableEnd());
  }

  private static String buildDescriptionTable(Map<String, String> systemDescriptions) {
    final StringBuilder sb = new StringBuilder();
    sb.append("<table border=\"1\">\n");

    String[] header = {"System config", "Description"};
    for (String cell : header) {
      sb.append("<th>\n");
      sb.append(cell);
      sb.append("</th>\n");
    }
    List<String> configs = new ArrayList<String>(systemDescriptions.keySet());
    Collections.sort(configs);
    for (String config : configs) {
      sb.append("<tr>\n");
      sb.append("<td>\n");
      sb.append(config);
      sb.append("</td>\n");
      sb.append("<td>\n");
      sb.append(systemDescriptions.get(config));
      sb.append("</td>\n");
      sb.append("</tr>\n");
    }
    sb.append("</table>\n");
    return sb.toString();
  }


}
