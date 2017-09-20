package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.evaluation.BootstrapWriter;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.parameters.ParametersModule;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.inject.Guice;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A one-off class for extracting per event type summaries for events scoring
 */
public final class LinkingSummarizer {

  private static final ImmutableList<String> REALISES = ImmutableList.of("noRealis", "withRealis");
  private static final String AGGREGATE = "linkScores";
  private static final String PER_EVENT_TYPE = "linkScoresPerEventType";
  private static final String BOOTSTRAP_DATA = "bootstrapData";
  private static final String PERCENTILES = "Aggregate.percentile.json";

  private final Parameters parameters;

  @Inject
  private LinkingSummarizer(Parameters params) {
    this.parameters = checkNotNull(params);
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
    Guice.createInjector(ParametersModule.createAndDump(params)).getInstance(LinkingSummarizer.class).go();
  }

  private void go() throws IOException {
    final File scoringDir = parameters.getExistingDirectory("com.bbn.tac.eal.linkingScores");
    final File outputDir = parameters.getCreatableDirectory("com.bbn.tac.eal.outputDirectory");

    final StringBuilder sb =
        new StringBuilder(
            StringUtils.commaJoiner().join("ScoreType", "Realis", "LinkScore(Median F1)"));
    sb.append("\n");
    for (final String realis : REALISES) {
      final File aggregateScoresFile =
          Paths.get(scoringDir.getAbsolutePath(), realis, AGGREGATE, BOOTSTRAP_DATA, PERCENTILES)
              .toFile();
      final Optional<Double> aggregateMedian = extractMedianScore(aggregateScoresFile);
      final String[] aggregateRow = {"Aggregate", realis, Double.toString(aggregateMedian.get())};
      sb.append(StringUtils.commaJoiner().join(aggregateRow));
      sb.append("\n");
      final File perEventTypeDir =
          Paths.get(scoringDir.getAbsolutePath(), realis, PER_EVENT_TYPE).toFile();
      for (final File eventTypeDir : checkNotNull(perEventTypeDir.listFiles())) {
        final File perEventTypeScoreFile =
            Paths.get(eventTypeDir.getAbsolutePath(), BOOTSTRAP_DATA, PERCENTILES).toFile();
        final Optional<Double> eventMedian = extractMedianScore(perEventTypeScoreFile);
        final String eventType = eventTypeDir.getName();
        final String[] eventTypeRow = {eventType, realis, Double.toString(eventMedian.get())};
        sb.append(StringUtils.commaJoiner().join(eventTypeRow));
        sb.append("\n");
      }
    }
    final File outputFile = new File(outputDir, "linkScoresChart.csv");
    Files.asCharSink(outputFile, Charsets.UTF_8).write(sb.toString());
  }

  private Optional<Double> extractMedianScore(final File scoresFile) throws IOException {
    final JacksonSerializer serializer = JacksonSerializer.forNormalJSON();

    final BootstrapWriter.SerializedBootstrapResults results =
        (BootstrapWriter.SerializedBootstrapResults) serializer
            .deserializeFrom(Files.asByteSource(scoresFile));
    return results.percentilesMap().get("F1").median();
  }

}

