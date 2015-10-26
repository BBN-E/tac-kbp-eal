package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.io.GZIPByteSink;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.kbp.events2014.scorer.ImmutableAggregate2015ArgScoringResult;
import com.bbn.kbp.events2014.scorer.ImmutableAggregate2015LinkScoringResult;
import com.bbn.kbp.events2014.scorer.ImmutableAggregate2015ScoringResult;
import com.bbn.kbp.linking.EALScorer2015Style;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

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
    final ImmutableAggregate2015ScoringResult result = computeAggregateScore(perDocResults);

    Files.asCharSink(new File(outputDir, "aggregateScore.txt"), Charsets.UTF_8).write(
        String
            .format("%30s:%8.2f\n", "Aggregate argument precision", result.argument().precision())
            +
            String.format("%30s:%8.2f\n", "Aggregate argument recall", result.argument().recall())
            +
            String.format("%30s:%8.2f\n\n", "Aggregate argument score", result.argument().overall())
            +
            String.format("%30s:%8.2f\n", "Aggregate linking precision",
                result.linking().precision()) +
            String.format("%30s:%8.2f\n", "Aggregate linking recall", result.linking().recall())
            +
            String.format("%30s:%8.2f\n\n", "Aggregate linking score", result.linking().overall())
            +
            String.format("%30s:%8.2f\n", "Overall score", result.overall()));

    final File jsonFile = new File(outputDir, "aggregateScore.json");
    final JacksonSerializer jacksonSerializer = JacksonSerializer.json().prettyOutput().build();
    jacksonSerializer.serializeTo(result, GZIPByteSink.gzipCompress(Files.asByteSink(jsonFile)));
  }

  private ImmutableAggregate2015ScoringResult computeAggregateScore(
      final List<EALScorer2015Style.Result> perDocResults) {
    final ImmutableAggregate2015ArgScoringResult argScores = computeArgScores(perDocResults);
    final ImmutableAggregate2015LinkScoringResult linkScores = computeLinkScores(perDocResults);

    final double aggregateScore = (1.0 - lambda) * argScores.overall()
        + lambda * linkScores.overall();

    return ImmutableAggregate2015ScoringResult.builder()
        .argument(argScores)
        .linking(linkScores)
        .overall(aggregateScore)
        .build();
  }

  private ImmutableAggregate2015LinkScoringResult computeLinkScores(
      final List<EALScorer2015Style.Result> perDocResults) {
    double rawLinkScoreSum = 0.0;
    double linkNormalizerSum = 0.0;
    double rawLinkPrecisionSum = 0.0;
    double rawLinkRecallSum = 0.0;
    for (final EALScorer2015Style.Result perDocResult : perDocResults) {
      rawLinkScoreSum += perDocResult.linkResult().unscaledLinkingScore();
      linkNormalizerSum += perDocResult.linkResult().linkingNormalizer();
      rawLinkPrecisionSum += perDocResult.linkResult().unscaledLinkingPrecision();
      rawLinkRecallSum += perDocResult.linkResult().unscaledLinkingRecall();
    }

    double aggregateLinkScore =
        (linkNormalizerSum > 0.0) ? rawLinkScoreSum / linkNormalizerSum : 0.0;

    double aggregateLinkPrecision =
        (linkNormalizerSum > 0.0) ? rawLinkPrecisionSum / linkNormalizerSum : 0.0;
    double aggregateLinkRecall =
        (linkNormalizerSum > 0.0) ? rawLinkRecallSum / linkNormalizerSum : 0.0;

    return ImmutableAggregate2015LinkScoringResult.builder()
        .precision(100.0 * aggregateLinkPrecision)
        .recall(100.0 * aggregateLinkRecall)
        .overall(100.0 * aggregateLinkScore).build();
  }

  static ImmutableAggregate2015ArgScoringResult computeArgScores(
      final List<EALScorer2015Style.Result> perDocResults) {
    return computeArgScoresFromArgResults(Lists.transform(perDocResults,
        new Function<EALScorer2015Style.Result, EALScorer2015Style.ArgResult>() {
          @Override
          public EALScorer2015Style.ArgResult apply(final EALScorer2015Style.Result input) {
            return input.argResult();
          }
        }));
  }

  static ImmutableAggregate2015ArgScoringResult computeArgScoresFromArgResults(
      final List<EALScorer2015Style.ArgResult> perDocResults) {
    double rawArgScoreSum = 0.0;
    double argNormalizerSum = 0.0;
    double argTruePositives = 0.0;
    double argFalsePositives = 0.0;

    double argTP = 0.0;
    double argFP = 0.0;
    double argFN = 0.0;
    for (final EALScorer2015Style.ArgResult perDocResult : perDocResults) {
      rawArgScoreSum += Math.max(0.0, perDocResult.unscaledArgumentScore());
      argNormalizerSum += perDocResult.argumentNormalizer();
      argTruePositives += perDocResult.unscaledTruePositiveArguments();
      argFalsePositives += perDocResult.unscaledFalsePositiveArguments();

      argTP += perDocResult.unscaledTruePositiveArguments();
      argFP += perDocResult.unscaledFalsePositiveArguments();
      argFN += perDocResult.unscaledFalseNegativeArguments();
    }

    double aggregateArgPrecision = (argTruePositives > 0.0)
                                   ? (argTruePositives) / (argFalsePositives + argTruePositives)
                                   : 0.0;
    double aggregateArgRecall =
        (argTruePositives > 0.0) ? (argTruePositives / argNormalizerSum) : 0.0;
    double aggregateArgScore = (argNormalizerSum > 0.0) ? rawArgScoreSum / argNormalizerSum : 0.0;

    return ImmutableAggregate2015ArgScoringResult.builder()
        .precision(100.0 * aggregateArgPrecision)
        .recall(100.0 * aggregateArgRecall)
        .overall(100.0 * aggregateArgScore)
        .truePositives(argTP)
        .falsePositives(argFP)
        .falseNegatives(argFN).build();
  }

  public KBP2015Scorer.BootstrappedResultWriterSource asBootstrappedResultWriterSource() {
    return new KBP2015Scorer.BootstrappedResultWriterSource() {
      @Override
      public KBP2015Scorer.BootstrappedResultWriter getResultWriter() {
        return new BootstrappedAggregateResultWriter();
      }
    };
  }

  public final class BootstrappedAggregateResultWriter implements
      KBP2015Scorer.BootstrappedResultWriter {

    private List<ImmutableAggregate2015ScoringResult> results = Lists.newArrayList();

    @Override
    public void observeSample(final Iterable<EALScorer2015Style.Result> perDocResults) {
      results.add(computeAggregateScore(ImmutableList.copyOf(perDocResults)));
    }

    @Override
    public void writeResult(final File baseOutputDir) throws IOException {
      final ImmutableList.Builder<Double> ealScoresBuilder = ImmutableList.builder();
      final ImmutableList.Builder<Double> f1ScoresBuilder = ImmutableList.builder();

      for(final ImmutableAggregate2015ScoringResult result : results) {
        ealScoresBuilder.add(result.linking().overall());
        final double p = result.argument().precision();
        final double r = result.argument().recall();
        f1ScoresBuilder.add((2*p*r)/(p+r));
      }

      final ImmutableList<Double> sortedEalScores = Ordering.natural().immutableSortedCopy(ealScoresBuilder.build());
      final ImmutableList<Double> sortedF1Scores = Ordering.natural().immutableSortedCopy(f1ScoresBuilder.build());

      Files.asCharSink(new File(baseOutputDir, "aggregate.bootstrapped.txt"), Charsets.UTF_8).write(
          String
              .format("%45s:%8.2f\n", "Aggregate argument F1 25th-percentile", percentileScore(sortedF1Scores, 0.25))
              +
              String.format("%45s:%8.2f\n\n", "Aggregate argument F1 75th-percentile", percentileScore(sortedF1Scores, 0.75))
              +
              String.format("%45s:%8.2f\n", "Aggregate linking score 25th-percentile", percentileScore(
                  sortedEalScores, 0.25))
              +
              String.format("%45s:%8.2f\n", "Aggregate linking score 75th-percentile",
                  percentileScore(sortedEalScores, 0.75)));


      final JacksonSerializer jacksonSerializer = JacksonSerializer.json().prettyOutput().build();
      jacksonSerializer.serializeTo(results,
          GZIPByteSink.gzipCompress(
              Files.asByteSink(new File(baseOutputDir, "aggregate.bootstrapped.json"))));
    }

    private double percentileScore(final ImmutableList<Double> sortedScores, final double percentile) {
      final int index = (int)Math.floor(percentile*(sortedScores.size()-1));
      if(index < 0) {
        return sortedScores.get(0);
      }
      if(index >= sortedScores.size()-1) {
        return sortedScores.get(sortedScores.size()-1);
      }
      return sortedScores.get(index);
    }
  }
}
