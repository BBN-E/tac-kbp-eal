package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.io.GZIPByteSink;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.TypeRoleFillerRealisFunctions;
import com.bbn.kbp.events2014.scorer.ImmutableAggregate2015ArgScoringResult;
import com.bbn.kbp.linking.EALScorer2015Style;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;

/**
 * Created by rgabbard on 8/24/15.
 */
public final class BootstrappedPerEventResultWriter
    implements KBP2015Scorer.BootstrappedResultWriter {

  private final ImmutableMultimap.Builder<String, ImmutableAggregate2015ArgScoringResult>
      eventTypeToArgScores =
      ImmutableMultimap.builder();

  @Override
  public void observeSample(final Iterable<EALScorer2015Style.Result> perDocResults) {
    // TODO: refactor this with non-bootstrapped version
    final Multiset<Symbol>
        eventTypesSeen = ByEventTypeResultWriter.gatherEventTypesSeen(perDocResults);

    for (final Multiset.Entry<Symbol> typeEntry : Multisets.copyHighestCountFirst(eventTypesSeen)
        .entrySet()) {
      final Symbol type = typeEntry.getElement();
      final Function<EALScorer2015Style.ArgResult, EALScorer2015Style.ArgResult>
          filterFunction =
          new Function<EALScorer2015Style.ArgResult, EALScorer2015Style.ArgResult>() {
            @Override
            public EALScorer2015Style.ArgResult apply(final
            EALScorer2015Style.ArgResult input) {
              return input
                  .copyFiltered(compose(equalTo(type), TypeRoleFillerRealisFunctions.type()));
            }
          };
      final ImmutableList<EALScorer2015Style.ArgResult> relevantArgumentScores =
          FluentIterable.from(perDocResults).transform(ByEventTypeResultWriter.GET_ARG_SCORES_ONLY)
              .transform(filterFunction)
              .toList();
      eventTypeToArgScores.put(typeEntry.getElement().asString(),
          AggregateResultWriter.computeArgScoresFromArgResults(relevantArgumentScores));
    }
  }

  @Override
  public void writeResult(final File baseOutputDir) throws IOException {
    for (final Map.Entry<String, Collection<ImmutableAggregate2015ArgScoringResult>> entry : eventTypeToArgScores
        .build().asMap().entrySet()) {
      final File jsonFile =
          new File(new File(baseOutputDir, entry.getKey()), "aggregateScore.json");
      final JacksonSerializer jacksonSerializer =
          JacksonSerializer.builder().forJson().prettyOutput().build();
      jacksonSerializer.serializeTo(entry.getValue(),
          GZIPByteSink.gzipCompress(Files.asByteSink(jsonFile)));
    }
  }

  public static KBP2015Scorer.BootstrappedResultWriterSource source() {
    return new KBP2015Scorer.BootstrappedResultWriterSource() {
      @Override
      public KBP2015Scorer.BootstrappedResultWriter getResultWriter() {
        return new BootstrappedPerEventResultWriter();
      }
    };
  }
}
