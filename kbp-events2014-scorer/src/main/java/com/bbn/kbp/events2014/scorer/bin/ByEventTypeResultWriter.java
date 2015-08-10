package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.linking.EALScorer2015Style;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;

/**
 * Created by rgabbard on 8/10/15.
 */
public final class ByEventTypeResultWriter implements KBP2015Scorer.ResultWriter {

  @Override
  public void writeResult(final List<EALScorer2015Style.Result> perDocResults,
      final File eventTypesDir) throws IOException {
    final Multiset<Symbol> eventTypesSeen = gatherEventTypesSeen(perDocResults);

    for (final Multiset.Entry<Symbol> typeEntry : Multisets.copyHighestCountFirst(eventTypesSeen)
        .entrySet()) {
      final Symbol type = typeEntry.getElement();
      final Function<EALScorer2015Style.ArgResult, EALScorer2015Style.ArgResult>
          filterFunction =
          new Function<EALScorer2015Style.ArgResult, EALScorer2015Style.ArgResult>() {
            @Override
            public EALScorer2015Style.ArgResult apply(final
            EALScorer2015Style.ArgResult input) {
              return input.copyFiltered(compose(equalTo(type), TypeRoleFillerRealis.Type));
            }
          };
      final File eventTypeDir = new File(eventTypesDir, type.asString());
      eventTypeDir.mkdirs();
      writeArgumentScoresForTransformedResults(perDocResults, filterFunction,
          eventTypeDir);
    }
  }

  private Multiset<Symbol> gatherEventTypesSeen(
      final List<EALScorer2015Style.Result> perDocResults) {
    final Multiset<Symbol> eventTypesSeen = HashMultiset.create();
    for (final EALScorer2015Style.Result perDocResult : perDocResults) {
      for (final TypeRoleFillerRealis trfr : perDocResult.argResult().argumentScoringAlignment()
          .allEquivalenceClassess()) {
        eventTypesSeen.add(trfr.type());
      }
    }
    return eventTypesSeen;
  }

  private void writeArgumentScoresForTransformedResults(
      final List<EALScorer2015Style.Result> perDocResults,
      final Function<EALScorer2015Style.ArgResult, EALScorer2015Style.ArgResult> filterFunction,
      final File outputDir) throws IOException {
    // this has a lot of repetition with writeBothScores
    // we'd like to fix this eventually
    final File perDocOutput = new File(outputDir, "scoresByDocument.txt");
    final ImmutableList<EALScorer2015Style.ArgResult> relevantArgumentScores =
        FluentIterable.from(perDocResults).transform(GET_ARG_SCORES_ONLY)
            .transform(filterFunction)
            .toList();

    Files.asCharSink(perDocOutput, Charsets.UTF_8).write(
        String.format("%40s\t%10s\t%10s\t%10s\t%10s\t%10s\t%10s\t", "Document", "ArgTP", "ArgFP",
            "ArgFN", "ArgP", "ArgR", "Arg") +
            Joiner.on("\n").join(
                Lists.transform(relevantArgumentScores,
                    new Function<EALScorer2015Style.ArgResult, String>() {
                      @Override
                      public String apply(final EALScorer2015Style.ArgResult input) {
                        return String.format("%40s\t%10f\t%10f\t%10f\t%10.2f\t%10.2f\t%10.2f",
                            input.docID(),
                            input.unscaledTruePositiveArguments(),
                            input.unscaledFalsePositiveArguments(),
                            input.unscaledFalseNegativeArguments(),
                            100.0 * input.precision(),
                            100.0 * input.recall(),
                            100.0 * input.scaledArgumentScore());
                      }
                    })));

    double rawArgScoreSum = 0.0;
    double argNormalizerSum = 0.0;
    double argTruePositives = 0.0;
    double argFalsePositives = 0.0;
    double argFalseNegatives = 0.0;
    for (final EALScorer2015Style.ArgResult perDocResult : relevantArgumentScores) {
      rawArgScoreSum += Math.max(0.0, perDocResult.unscaledArgumentScore());
      argNormalizerSum += perDocResult.argumentNormalizer();
      argTruePositives += perDocResult.unscaledTruePositiveArguments();
      argFalsePositives += perDocResult.unscaledFalsePositiveArguments();
      argFalseNegatives += perDocResult.unscaledFalseNegativeArguments();
    }

    double aggregateArgScore = (argNormalizerSum > 0.0) ? rawArgScoreSum / argNormalizerSum : 0.0;

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
            String
                .format("%30s:%8.2f\n\n", "Aggregate argument score", 100.0 * aggregateArgScore));

    final ImmutableMap<String, Double> resultsForJson = ImmutableMap.<String, Double>builder()
        .put("argPrecision", 100.0 * aggregateArgPrecision)
        .put("argRecall", 100.0 * aggregateArgRecall)
        .put("argOverall", 100.0 * aggregateArgScore)
        .put("argTP", argTruePositives)
        .put("argFP", argFalsePositives)
        .put("argFN", argFalseNegatives).build();

    final File jsonFile = new File(outputDir, "aggregateScore.json");
    final JacksonSerializer jacksonSerializer = JacksonSerializer.json().prettyOutput().build();
    jacksonSerializer.serializeTo(resultsForJson, Files.asByteSink(jsonFile));
  }

  private static final Function<EALScorer2015Style.Result, EALScorer2015Style.ArgResult>
      GET_ARG_SCORES_ONLY =
      new Function<EALScorer2015Style.Result, EALScorer2015Style.ArgResult>() {
        @Override
        public EALScorer2015Style.ArgResult apply(
            final EALScorer2015Style.Result input) {
          return input.argResult();
        }
      };
}
