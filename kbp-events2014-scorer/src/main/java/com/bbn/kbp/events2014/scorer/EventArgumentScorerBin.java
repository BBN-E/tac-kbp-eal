package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.ScoringData;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.scorer.observers.KBPScoringObserver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventArgumentScorerBin {
  private static final Logger log = LoggerFactory.getLogger(EventArgumentScorerBin.class);

  private final EventArgumentScorer eventArgumentScorer;
  private final ImmutableList<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers;

  public EventArgumentScorerBin(final EventArgumentScorer eventArgumentScorer,
      final ImmutableList<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers) {
    this.eventArgumentScorer = eventArgumentScorer;
    this.scoringObservers = scoringObservers;
  }

  public void run(final SystemOutputStore systemAnswerStore, final AnnotationStore goldAnswerStore,
      final Set<Symbol> documentsToScore, final File baseOutputDir)
      throws IOException {
    final Map<KBPScoringObserver<TypeRoleFillerRealis>, File> scorerToOutputDir =
        makeScorerToOutputDir(baseOutputDir, scoringObservers);

    for (final KBPScoringObserver<TypeRoleFillerRealis> observer : scoringObservers) {
      observer.startCorpus();
    }

    for (final Symbol docid : documentsToScore) {
      log.info("Scoring document: {}", docid);

      final EventArgScoringAlignment<TypeRoleFillerRealis> scoringAlignment = eventArgumentScorer
          .score(ScoringData.builder().withAnswerKey(goldAnswerStore.readOrEmpty(docid))
              .withSystemOutput(systemAnswerStore.readOrEmpty(docid)).build());

      for (final KBPScoringObserver<TypeRoleFillerRealis> scoringObserver : scoringObservers) {
        final File docLogDir = new File(scorerToOutputDir.get(scoringObserver), docid.toString());
        docLogDir.mkdirs();
        scoringObserver.observeDocument(scoringAlignment, docLogDir);
      }
    }

    log.info("Reports for corpus:");
    for (final KBPScoringObserver<?> observer : scoringObservers) {
      observer.endCorpus();
    }

    for (final Map.Entry<KBPScoringObserver<TypeRoleFillerRealis>, File> corpusOutput : scorerToOutputDir
        .entrySet()) {
      corpusOutput.getKey().writeCorpusOutput(corpusOutput.getValue());
    }
  }

  private static <T> Map<KBPScoringObserver<T>, File> makeScorerToOutputDir(
      final File baseOutputDir, final List<KBPScoringObserver<T>> corpusObservers) {
    final ImmutableMap.Builder<KBPScoringObserver<T>, File> ret =
        ImmutableMap.builder();

    for (final KBPScoringObserver<T> observer : corpusObservers) {
      final File outdir = new File(baseOutputDir, observer.name());
      outdir.mkdirs();
      ret.put(observer, outdir);
    }

    return ret.build();
  }
}
