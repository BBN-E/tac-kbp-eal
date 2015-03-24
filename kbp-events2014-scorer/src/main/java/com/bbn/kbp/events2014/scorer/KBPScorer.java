package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.scorer.bin.Preprocessor;
import com.bbn.kbp.events2014.scorer.observers.KBPScoringObserver;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
import static com.google.common.collect.Iterables.concat;
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

      final SystemOutputEquivalenceClasses<TypeRoleFillerRealis> systemOutputEquivalenceClasses =
          SystemOutputEquivalenceClasses.forAnswerable(preprocessorResult.systemOutput(),
              equivalenceClassFunction);

      final AnswerKeyEquivalenceClasses<TypeRoleFillerRealis> answerKeyEquivalenceClasses =
          AnswerKeyEquivalenceClasses.forAnswerable(
              preprocessorResult.answerKey(), equivalenceClassFunction);

      final ImmutableMap<KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver,
          KBPScoringObserver<TypeRoleFillerRealis>> docObserversToCorpusObservers =
          documentObserversForCorpusObservers(scoringObservers, answerKeyEquivalenceClasses,
              systemOutputEquivalenceClasses);

      final Set<KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver> docObservers =
          docObserversToCorpusObservers.keySet();

      // notify observers we are starting a new document
      for (final KBPScoringObserver<?>.KBPAnswerSourceObserver observer : docObservers) {
        observer.start();
      }

      final Set<TypeRoleFillerRealis> allAnswerables = ImmutableSet.copyOf(
          concat(systemOutputEquivalenceClasses.answerables(), answerKeyEquivalenceClasses.answerables()));

      // TODO: ByJustification location is not consistent with equals
      //final Ordering<TypeRoleFillerRealis> order = ByJustificationLocation.create(answerKeyEquivalenceClasses, systemOutputEquivalenceClasses);
      //final Ordering<TypeRoleFillerRealis> order = Ordering.

      for (final TypeRoleFillerRealis answerable : allAnswerables) {
        log.info("Scoring equivalence class {}", answerable);

        // notify observers we are starting a new equivalence class
        for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
          observer.startAnswerable(answerable);
        }

        final Set<Response> responses = systemOutputEquivalenceClasses.answers(answerable);
        final Set<AssessedResponse> annotatedResponses = answerKeyEquivalenceClasses.answers(answerable);

        // let observers see the responses jointly
        for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
          observer.observe(answerable, responses, annotatedResponses);
        }

        if (!responses.isEmpty()) {
          // get safe because responses non-empty
          final Response selectedSystemResponse = systemOutputEquivalenceClasses.systemOutput()
              .selectFromMultipleSystemResponses(
                  responses).get();
          final Optional<AssessedResponse> annotationForArgument =
              AssessedResponse.findAnnotationForArgument(
                  selectedSystemResponse, annotatedResponses);

          if (annotationForArgument.isPresent()) {
            // notify observers we have a selected system response
            // with aligned assessment
            for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
              observer.annotatedSelectedResponse(answerable, selectedSystemResponse,
                  annotationForArgument.get(), annotatedResponses);
            }
          } else {
            // notify observers we have a selected system response
            // but not assessment to align to it
            for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
              observer.unannotatedSelectedResponse(answerable, selectedSystemResponse,
                  annotatedResponses);
            }
          }

          if (annotatedResponses.isEmpty()) {
            // notify observers we have system responses but not annotated responses
            for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
              observer.responsesOnlyNonEmpty(answerable, responses);
            }
          }
        } else if (!annotatedResponses.isEmpty()) {
          // notify observers we have annotated responses but not system responses
          for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
            observer.annotationsOnlyNonEmpty(answerable, annotatedResponses);
          }
        } else {
          throw new RuntimeException("Can't happen: alignment failure.");
        }

        // notify observers we are ending this equivalence class
        for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
          observer.endAnswerable(answerable);
        }
      }

      // notify observers we are finished with this document
      for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
        observer.end();
      }

			/*for (final Map.Entry<KBPScoringObserver<TypeRoleFillerRealis>, File> corpusOutput : scorerToOutputDir.entrySet()) {
                                corpusOutput.getKey().writeCorpusHTML(corpusOutput.getValue());
			}*/

      for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
        File outputDir =
            new File(scorerToOutputDir.get(docObserversToCorpusObservers.get(observer)),
                docid.toString());
        outputDir.mkdirs();
        observer.writeDocumentOutput(outputDir);
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

  private static ImmutableMap<KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver, KBPScoringObserver<TypeRoleFillerRealis>> documentObserversForCorpusObservers(
      List<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers,
      AnswerKeyEquivalenceClasses<TypeRoleFillerRealis> answerKey,
      SystemOutputEquivalenceClasses<TypeRoleFillerRealis> systemOutput) {
    // for each corpus observer, get a document observer, and maintain a mapping back to the corpus
    // observer it came from. I really wish Java had typedefs
    final ImmutableMap.Builder<KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver, KBPScoringObserver<TypeRoleFillerRealis>>
        docObserverToCorpusObserversBuilder = ImmutableMap.builder();
    for (final KBPScoringObserver<TypeRoleFillerRealis> corpusObserver : corpusObservers) {
      final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver docObserver =
          corpusObserver.answerSourceObserver(systemOutput, answerKey);
      docObserverToCorpusObserversBuilder.put(docObserver, corpusObserver);
    }
    return docObserverToCorpusObserversBuilder.build();
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
