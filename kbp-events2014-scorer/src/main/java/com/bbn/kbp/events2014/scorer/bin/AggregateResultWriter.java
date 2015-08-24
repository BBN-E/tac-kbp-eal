package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.kbp.linking.EALScorer2015Style;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.immutables.value.Value;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

final class AggregateResultWriter implements KBP2015Scorer.SimpleResultWriter {
  private final double lambda;

  AggregateResultWriter(final double lambda) {
    checkArgument(lambda >= 0.0);
    this.lambda = lambda;
  }

  @Override
  public void writeResult(final List<EALScorer2015Style.Result> perDocResults,
      final File outputDir) throws IOException {
    final ImmutableAggregateResult result = computeAggregateScore(perDocResults);

    Files.asCharSink(new File(outputDir, "aggregateScore.txt"), Charsets.UTF_8).write(
        String
            .format("%30s:%8.2f\n", "Aggregate argument precision", result.argPrecision())
            +
            String.format("%30s:%8.2f\n", "Aggregate argument recall", result.argRecall())
            +
            String.format("%30s:%8.2f\n\n", "Aggregate argument score", result.argOverall())
            +
            String.format("%30s:%8.2f\n", "Aggregate linking precision",
                result.linkPrecision()) +
            String.format("%30s:%8.2f\n", "Aggregate linking recall", result.linkRecall())
            +
            String.format("%30s:%8.2f\n\n", "Aggregate linking score", result.linkOverall())
            +
            String.format("%30s:%8.2f\n", "Overall score", result.overall()));

    final File jsonFile = new File(outputDir, "aggregateScore.json");
    final JacksonSerializer jacksonSerializer = JacksonSerializer.json().prettyOutput().build();
    jacksonSerializer.serializeTo(result, Files.asByteSink(jsonFile));
  }

  private ImmutableAggregateResult computeAggregateScore(
      final List<EALScorer2015Style.Result> perDocResults) {
    double rawArgScoreSum = 0.0;
    double argNormalizerSum = 0.0;
    double argTruePositives = 0.0;
    double argFalsePositives = 0.0;
    double rawLinkScoreSum = 0.0;
    double linkNormalizerSum = 0.0;
    double rawLinkPrecisionSum = 0.0;
    double rawLinkRecallSum = 0.0;
    double argTP = 0.0;
    double argFP = 0.0;
    double argFN = 0.0;
    for (final EALScorer2015Style.Result perDocResult : perDocResults) {
      rawArgScoreSum += Math.max(0.0, perDocResult.argResult().unscaledArgumentScore());
      argNormalizerSum += perDocResult.argResult().argumentNormalizer();
      argTruePositives += perDocResult.argResult().unscaledTruePositiveArguments();
      argFalsePositives += perDocResult.argResult().unscaledFalsePositiveArguments();
      rawLinkScoreSum += perDocResult.linkResult().unscaledLinkingScore();
      linkNormalizerSum += perDocResult.linkResult().linkingNormalizer();
      rawLinkPrecisionSum += perDocResult.linkResult().unscaledLinkingPrecision();
      rawLinkRecallSum += perDocResult.linkResult().unscaledLinkingRecall();
      argTP += perDocResult.argResult().unscaledTruePositiveArguments();
      argFP += perDocResult.argResult().unscaledFalsePositiveArguments();
      argFN += perDocResult.argResult().unscaledFalseNegativeArguments();
    }

    double aggregateArgScore = (argNormalizerSum > 0.0) ? rawArgScoreSum / argNormalizerSum : 0.0;
    double aggregateLinkScore =
        (linkNormalizerSum > 0.0) ? rawLinkScoreSum / linkNormalizerSum : 0.0;
    double aggregateScore = (1.0 - lambda) * aggregateArgScore + lambda * aggregateLinkScore;

    double aggregateLinkPrecision =
        (linkNormalizerSum > 0.0) ? rawLinkPrecisionSum / linkNormalizerSum : 0.0;
    double aggregateLinkRecall =
        (linkNormalizerSum > 0.0) ? rawLinkRecallSum / linkNormalizerSum : 0.0;

    double aggregateArgPrecision = (argTruePositives > 0.0)
                                   ? (argTruePositives) / (argFalsePositives + argTruePositives)
                                   : 0.0;
    double aggregateArgRecall =
        (argTruePositives > 0.0) ? (argTruePositives / argNormalizerSum) : 0.0;

    return ImmutableAggregateResult.builder()
        .argPrecision(100.0 * aggregateArgPrecision)
        .argRecall(100.0 * aggregateArgRecall)
        .argOverall(100.0 * aggregateArgScore)
        .linkPrecision(100.0 * aggregateLinkPrecision)
        .linkRecall(100.0 * aggregateLinkRecall)
        .linkOverall(100.0 * aggregateLinkScore)
        .overall(100.0 * aggregateScore)
        .argTruePositives(argTP)
        .argFalsePositives(argFP)
        .argFalseNegatives(argFN)
        .build();
  }

  public KBP2015Scorer.BootstrappedResultWriterSource asBootstrappedResultWriterSource() {
    return new KBP2015Scorer.BootstrappedResultWriterSource() {
      @Override
      public KBP2015Scorer.BootstrappedResultWriter getResultWriter() {
        return new BootstrappedAggregateResultWriter();
      }
    };
  }

  @Value.Immutable
  @JsonSerialize(as = ImmutableAggregateResult.class)
  @JsonDeserialize(as = ImmutableAggregateResult.class)
  public static abstract class AggregateResult {

    abstract double argPrecision();

    abstract double argRecall();

    abstract double argOverall();

    abstract double linkPrecision();

    abstract double linkRecall();

    abstract double linkOverall();

    abstract double overall();

    abstract double argTruePositives();

    abstract double argFalsePositives();

    abstract double argFalseNegatives();

    @Value.Check
    protected void check() {
      checkArgument(argPrecision() >= 0.0);
      checkArgument(argRecall() >= 0.0);
      checkArgument(linkPrecision() >= 0.0);
      checkArgument(linkRecall() >= 0.0);
      checkArgument(linkOverall() >= 0.0);
      checkArgument(overall() >= 0.0);
      checkArgument(argTruePositives() >= 0.0);
      checkArgument(argFalsePositives() >= 0.0);
      checkArgument(argFalseNegatives() >= 0.0);
    }
  }

  public final class BootstrappedAggregateResultWriter implements
      KBP2015Scorer.BootstrappedResultWriter {

    private List<ImmutableAggregateResult> results = Lists.newArrayList();

    @Override
    public void observeSample(final Iterable<EALScorer2015Style.Result> perDocResults) {
      results.add(computeAggregateScore(ImmutableList.copyOf(perDocResults)));
    }

    @Override
    public void writeResult(final File baseOutputDir) throws IOException {
      final JacksonSerializer jacksonSerializer = JacksonSerializer.json().prettyOutput().build();
      jacksonSerializer.serializeTo(results,
          Files.asByteSink(new File(baseOutputDir, "aggregate.bootstrapped.json")));
    }
  }
}
