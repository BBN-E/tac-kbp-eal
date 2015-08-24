package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.kbp.linking.EALScorer2015Style;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Created by rgabbard on 8/10/15.
 */
final class AggregateAndPerDocResultWriter implements KBP2015Scorer.SimpleResultWriter {

  private final double lambda;

  AggregateAndPerDocResultWriter(final double lambda) {
    checkArgument(lambda >= 0.0);
    this.lambda = lambda;
  }

  @Override
  public void writeResult(final List<EALScorer2015Style.Result> perDocResults,
      final File outputDir) throws IOException {
    final File perDocOutput = new File(outputDir, "scoresByDocument.txt");
    Files.asCharSink(perDocOutput, Charsets.UTF_8).write(
        String.format("%40s\t%10s\t%10s\t%10s\t%10s\n", "Document", "Arg", "Link-P,R,F", "Link",
            "Combined") +
            Joiner.on("\n").join(
                FluentIterable.from(perDocResults)
                    .transform(new Function<EALScorer2015Style.Result, String>() {
                      @Override
                      public String apply(final EALScorer2015Style.Result input) {
                        return String.format("%40s\t%10.2f\t%7s%7s%7s\t%10.2f\t%10.2f",
                            input.docID(),
                            100.0 * input.argResult().scaledArgumentScore(),
                            String
                                .format("%.1f",
                                    100.0 * input.linkResult().linkingScore().precision()),
                            String.format("%.1f",
                                100.0 * input.linkResult().linkingScore().recall()),
                            String.format("%.1f", 100.0 * input.linkResult().linkingScore().F1()),
                            100.0 * input.linkResult().scaledLinkingScore(),
                            100.0 * input.scaledScore());
                      }
                    })));

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

    Files.asCharSink(new File(outputDir, "aggregateScore.txt"), Charsets.UTF_8).write(
        String
            .format("%30s:%8.2f\n", "Aggregate argument precision", 100.0 * aggregateArgPrecision)
            +
            String.format("%30s:%8.2f\n", "Aggregate argument recall", 100.0 * aggregateArgRecall)
            +
            String.format("%30s:%8.2f\n\n", "Aggregate argument score", 100.0 * aggregateArgScore)
            +
            String.format("%30s:%8.2f\n", "Aggregate linking precision",
                100.0 * aggregateLinkPrecision) +
            String.format("%30s:%8.2f\n", "Aggregate linking recall", 100.0 * aggregateLinkRecall)
            +
            String.format("%30s:%8.2f\n\n", "Aggregate linking score", 100.0 * aggregateLinkScore)
            +
            String.format("%30s:%8.2f\n", "Overall score", 100.0 * aggregateScore));

    final ImmutableMap<String, Double> resultsForJson = ImmutableMap.<String, Double>builder()
        .put("argPrecision", 100.0 * aggregateArgPrecision)
        .put("argRecall", 100.0 * aggregateArgRecall)
        .put("argOverall", 100.0 * aggregateArgScore)
        .put("linkPrecision", 100.0 * aggregateLinkPrecision)
        .put("linkRecall", 100.0 * aggregateLinkRecall)
        .put("linkOverall", 100.0 * aggregateLinkScore)
        .put("overall", 100.0 * aggregateScore)
        .put("argTP", argTP)
        .put("argFP", argFP)
        .put("argFN", argFN)
        .build();

    final File jsonFile = new File(outputDir, "aggregateScore.json");
    final JacksonSerializer jacksonSerializer = JacksonSerializer.json().prettyOutput().build();
    jacksonSerializer.serializeTo(resultsForJson, Files.asByteSink(jsonFile));
  }
}
