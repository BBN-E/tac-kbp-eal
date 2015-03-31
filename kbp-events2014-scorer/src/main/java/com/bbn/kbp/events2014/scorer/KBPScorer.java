package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.EventArgScoringAlignment;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.scorer.bin.Preprocessor;
import com.bbn.kbp.events2014.scorer.observers.KBPScoringObserver;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bbn.kbp.events2014.AssessedResponse.IsCorrectUpToInexactJustifications;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Sets.union;

/**
 * A scorer for the KBP event argument-attachment task.
 *
 * @author rgabbard
 */
public final class KBPScorer {

  private static final Logger log = LoggerFactory.getLogger(KBPScorer.class);
  private final Preprocessor preprocessor;
  private final ImmutableList<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers;

  private KBPScorer(Preprocessor preprocessor,
      final Iterable<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers) {
    this.preprocessor = checkNotNull(preprocessor);
    this.scoringObservers = ImmutableList.copyOf(scoringObservers);
  }

  public static KBPScorer create(Preprocessor preprocessor,
      Iterable<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers) {
    return new KBPScorer(preprocessor, scoringObservers);
  }

  /**
   * Runs the scorer over all documents found in either the system output store or gold annotation
   * store.
   */
  public void run(SystemOutputStore systemOutputStore, AnnotationStore goldAnswerStore,
      File baseOutputDir) throws IOException {
    run(systemOutputStore, goldAnswerStore,
        union(systemOutputStore.docIDs(), goldAnswerStore.docIDs()), baseOutputDir);
  }

  /**
   * Runs the scorer over the specified documents.
   */
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

      final Preprocessor.Result preprocessorResult = preprocessor.preprocess(
          systemAnswerStore.readOrEmpty(docid), goldAnswerStore.readOrEmpty(docid));

      final Function<Response, TypeRoleFillerRealis> equivalenceClassFunction =
          TypeRoleFillerRealis.extractFromSystemResponse(preprocessorResult.normalizer());

      final StandardScoringAligner<TypeRoleFillerRealis> scoringAligner =
          StandardScoringAligner.forEquivalenceClassFunction(equivalenceClassFunction);
      final EventArgScoringAlignment<TypeRoleFillerRealis> scoringAlignment =
          scoringAligner.align(preprocessorResult.answerKey(), preprocessorResult.systemOutput());

      final SystemOutputEquivalenceClasses<TypeRoleFillerRealis> systemOutputEquivalenceClasses =
          SystemOutputEquivalenceClasses.forAnswerable(preprocessorResult.systemOutput(),
              equivalenceClassFunction);

      final AnswerKeyEquivalenceClasses<TypeRoleFillerRealis> answerKeyEquivalenceClasses =
          AnswerKeyEquivalenceClasses.forAnswerable(
              preprocessorResult.answerKey(), equivalenceClassFunction);

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


  private static final Symbol PRESENT = Symbol.from("PRESENT");
  private static final Symbol ABSENT = Symbol.from("ABSENT");


  public static final Function<Collection<Response>, Symbol> IsPresent =
      new Function<Collection<Response>, Symbol>() {
        @Override
        public Symbol apply(final Collection<Response> args) {
          return args.isEmpty() ? ABSENT : PRESENT;
        }
      };

  public static final Function<Collection<AssessedResponse>, Symbol> AnyAnswerCorrect =
      new Function<Collection<AssessedResponse>, Symbol>() {
        @Override
        public Symbol apply(final Collection<AssessedResponse> args) {
          return any(args, AssessedResponse.IsCompletelyCorrect) ? PRESENT : ABSENT;
        }
      };

  public static final Function<Collection<AssessedResponse>, Symbol> AnyAnswerSemanticallyCorrect =
      new Function<Collection<AssessedResponse>, Symbol>() {
        @Override
        public Symbol apply(final Collection<AssessedResponse> args) {
          return any(args, IsCorrectUpToInexactJustifications) ? PRESENT : ABSENT;
        }
      };

}
