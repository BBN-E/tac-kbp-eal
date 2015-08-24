package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.io.GZIPByteSink;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.scorer.ImmutableAggregate2015ArgScoringResult;
import com.bbn.kbp.linking.EALScorer2015Style;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
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
public final class ByEventTypeResultWriter implements KBP2015Scorer.SimpleResultWriter {

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
      writeOverallArgumentScoresForTransformedResults(perDocResults, filterFunction,
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


  private void writeOverallArgumentScoresForTransformedResults(
      final List<EALScorer2015Style.Result> perDocResults,
      final Function<EALScorer2015Style.ArgResult, EALScorer2015Style.ArgResult> filterFunction,
      final File outputDir) throws IOException {
    // this has a lot of repetition with writeBothScores
    // we'd like to fix this eventually
    final ImmutableList<EALScorer2015Style.ArgResult> relevantArgumentScores =
        FluentIterable.from(perDocResults).transform(GET_ARG_SCORES_ONLY)
            .transform(filterFunction)
            .toList();

    PerDocResultWriter.writeArgPerDoc(relevantArgumentScores,
        new File(outputDir, "scoresByDocument.txt"));

    final ImmutableAggregate2015ArgScoringResult argScores =
        AggregateResultWriter.computeArgScoresFromArgResults(relevantArgumentScores);

    Files.asCharSink(new File(outputDir, "aggregateScore.txt"), Charsets.UTF_8).write(
        String
            .format("%30s:%8.2f\n", "Aggregate argument precision", 100.0 * argScores.precision())
            +
            String.format("%30s:%8.2f\n", "Aggregate argument recall", 100.0 * argScores.recall())
            +
            String
                .format("%30s:%8.2f\n\n", "Aggregate argument score", 100.0 * argScores.overall()));

    final File jsonFile = new File(outputDir, "aggregateScore.json");
    final JacksonSerializer jacksonSerializer = JacksonSerializer.json().prettyOutput().build();
    jacksonSerializer.serializeTo(argScores, GZIPByteSink.gzipCompress(Files.asByteSink(jsonFile)));
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
