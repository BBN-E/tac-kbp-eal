package com.bbn.kbp.events;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.evaluation.BootstrapInspector;
import com.bbn.bue.common.evaluation.BootstrapWriter;
import com.bbn.bue.common.evaluation.EvalPair;
import com.bbn.bue.common.math.PercentileComputer;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.TACKBPEALException;
import com.bbn.kbp.linking.ExplicitFMeasureInfo;
import com.bbn.kbp.linking.LinkF1;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;

final class LinkingInspector implements
    BootstrapInspector.BootstrapStrategy<EvalPair<DocLevelArgLinking, DocLevelArgLinking>, LinkingInspector.LinkingScoreDocRecord> {
  private static final Logger log = LoggerFactory.getLogger(LinkingInspector.class);

  private final File outputDir;

  private LinkingInspector(final File outputDir) {
    this.outputDir = outputDir;
  }

  public static LinkingInspector createOutputtingTo(final File outputFile) {
    return new LinkingInspector(outputFile);
  }

  @Override
  public BootstrapInspector.ObservationSummarizer<EvalPair<DocLevelArgLinking, DocLevelArgLinking>, LinkingScoreDocRecord> createObservationSummarizer() {
    return new BootstrapInspector.ObservationSummarizer<EvalPair<DocLevelArgLinking, DocLevelArgLinking>, LinkingScoreDocRecord>() {
      @Override
      public LinkingScoreDocRecord summarizeObservation(
          final EvalPair<DocLevelArgLinking, DocLevelArgLinking> item) {
        checkArgument(ImmutableSet.copyOf(concat(item.key())).containsAll(
            ImmutableSet.copyOf(concat(item.test()))), "Must contain only answers in test set!");
        if (!item.key().docID().equalTo(item.test().docID())) {
          log.warn("DocIDs do not match: {} vs {}", item.key().docID(), item.test().docID());
        }

        final ExplicitFMeasureInfo counts = LinkF1.create().score(item.test(), item.key());

        final Symbol docid = item.key().docID();

        final File docOutput = new File(outputDir, docid.asString());
        docOutput.mkdirs();
        final PrintWriter outputWriter;
        try {
          outputWriter = new PrintWriter(new File(docOutput, "linkingF.txt"));
          outputWriter.println(counts.toString());
          outputWriter.close();
        } catch (FileNotFoundException e) {
          throw new TACKBPEALException(e);
        }

        final ImmutableSet<DocLevelEventArg> args = ImmutableSet.copyOf(concat(
            transform(concat(item.test().eventFrames(), item.key().eventFrames()),
                ScoringEventFrameFunctions.arguments())));

        return new LinkingScoreDocRecord.Builder()
            .fMeasureInfo(counts)
            .predictedCounts(ImmutableSet.copyOf(concat(item.test().eventFrames())).size())
            .actualCounts(ImmutableSet.copyOf(concat(item.key().eventFrames())).size())
            .linkingArgCounts(args.size())
            .build();
      }
    };
  }

  @Override
  public Collection<BootstrapInspector.SummaryAggregator<LinkingScoreDocRecord>> createSummaryAggregators() {
    return ImmutableList.<BootstrapInspector.SummaryAggregator<LinkingScoreDocRecord>>of(
        new BootstrapInspector.SummaryAggregator<LinkingScoreDocRecord>() {

          private final ImmutableListMultimap.Builder<String, Double> f1sB =
              ImmutableListMultimap.builder();
          private final ImmutableListMultimap.Builder<String, Double> precisionsB =
              ImmutableListMultimap.builder();
          private final ImmutableListMultimap.Builder<String, Double> recallsB =
              ImmutableListMultimap.builder();

          private static final String F1 = "F1";
          private static final String PRECISION = "Precision";
          private static final String RECALL = "Recall";

          private final BootstrapWriter writer = new BootstrapWriter.Builder()
              .measures(ImmutableList.of(F1, PRECISION, RECALL))
              .percentilesToPrint(
                  ImmutableList.of(0.005, 0.025, 0.05, 0.25, 0.5, 0.75, 0.95, 0.975, 0.995))
              .percentileComputer(PercentileComputer.nistPercentileComputer())
              .build();

          @Override
          public void observeSample(
              final Collection<LinkingScoreDocRecord> collection) {
            // copies logic from com.bbn.kbp.events2014.scorer.bin.AggregateResultWriter.computeLinkScores()

            double precision = 0;
            double recall = 0;
            double f1 = 0;
            double linkNormalizerSum = 0;

            for (final LinkingScoreDocRecord docRecord : collection) {
              precision += docRecord.fMeasureInfo().precision() * docRecord.predictedCounts();
              recall += docRecord.fMeasureInfo().recall() * docRecord.actualCounts();
              f1 += docRecord.fMeasureInfo().f1() * docRecord.actualCounts();
              linkNormalizerSum += docRecord.linkingArgCounts();
            }

            // the normalizer sum can't actually be negative here, but this minimizes divergence with the source logic.
            f1sB.put("Aggregate", (linkNormalizerSum > 0.0) ? f1 / linkNormalizerSum : 0.0);
            precisionsB.put("Aggregate",
                (linkNormalizerSum > 0.0) ? precision / linkNormalizerSum : 0.0);
            recallsB
                .put("Aggregate", (linkNormalizerSum > 0.0) ? recall / linkNormalizerSum : 0.0);
          }

          //@Override
          public void finish() throws IOException {
            writer.writeBootstrapData("linkScores",
                ImmutableMap.of(
                    F1, f1sB.build(),
                    PRECISION, precisionsB.build(),
                    RECALL, recallsB.build()
                ),
                new File(outputDir, "linkScores"));
          }
        });
  }

  @TextGroupImmutable
  @Value.Immutable
  abstract static class LinkingScoreDocRecord {

    abstract ExplicitFMeasureInfo fMeasureInfo();

    abstract int predictedCounts();

    abstract int actualCounts();

    abstract int linkingArgCounts();

    public static class Builder extends ImmutableLinkingScoreDocRecord.Builder {

    }
  }
}
