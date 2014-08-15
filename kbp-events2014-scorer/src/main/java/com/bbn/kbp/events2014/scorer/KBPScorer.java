package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.*;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.scorer.observers.KBPScoringObserver;
import com.bbn.kbp.events2014.transformers.DeleteInjureForCorrectDie;
import com.bbn.kbp.events2014.transformers.OnlyMostSpecificTemporal;
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
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Sets.union;

/**
 * A scorer for the KBP event argument-attachment task.
 *
 * @author rgabbard
 *
 */
public final class KBPScorer {
	private static final Logger log = LoggerFactory.getLogger(KBPScorer.class);

    private KBPScorer() {}

    public static KBPScorer create() {
        return new KBPScorer();
    }

    /**
     * Runs the scorer over all documents found in either the system output store
     * or gold annotation store.
     * @throws IOException
     */
    public void run(SystemOutputStore systemOutputStore, AnnotationStore goldAnswerStore,
                    ScoringConfiguration scoringConfiguration,
                    File baseOutputDir) throws IOException
    {
        run(systemOutputStore, goldAnswerStore,
                union(systemOutputStore.docIDs(), goldAnswerStore.docIDs()),
                scoringConfiguration, baseOutputDir);
    }

    /**
     * Runs the scorer over the specified documents.
     * @throws IOException
     */
	public void run(final SystemOutputStore systemAnswerStore,
			final AnnotationStore goldAnswerStore,
            final Set<Symbol> documentsToScore,
			final ScoringConfiguration scoringConfiguration,
			final File baseOutputDir)
			throws IOException
	{
		final Map<KBPScoringObserver<TypeRoleFillerRealis>, File> scorerToOutputDir =
                makeScorerToOutputDir(baseOutputDir, scoringConfiguration.scoringObservers());

        for (final KBPScoringObserver<TypeRoleFillerRealis> observer : scoringConfiguration.scoringObservers()) {
            observer.startCorpus();
        }

		for (final Symbol docid : documentsToScore) {
			log.info("Scoring document: {}", docid);

			AnswerKey key = goldAnswerStore.readOrEmpty(docid);

            for (Function<AnswerKey, AnswerKey> answerKeyTransformation : scoringConfiguration.answerKeyTransformations()) {
                key = answerKeyTransformation.apply(key);
            }

            // Annotators will mark coreference between the entity fillers for arguments.  We load
            // these annotations in order to group e.g. (BasketballGame, Winner, Louisville, Actual)
            // and (BasketballGame, Winner, The Cards, Actual) if the annotator had coreffed
            // "Louisville" and "The Cards"
            final EntityNormalizer entityNormalizer = EntityNormalizer.fromAnnotation(key);

            // hard-coding the following two in is an ugly hack. Eventually they should get refactored
            // into a collection of filters built from each answer key.

            // if a correct temporal role is present in the answer key, then
            // all less specific versions of that temporal role are removed
            // from all system output before scoring
            final OnlyMostSpecificTemporal temporalFilter = OnlyMostSpecificTemporal.forAnswerKey(key);

            // remove injure events which match die events (see "Departures from ACE 2005" in
            // the evaluation plan)
            final DeleteInjureForCorrectDie injureFilter =
                    DeleteInjureForCorrectDie.fromEntityNormalizer(entityNormalizer);
            final Function<SystemOutput,SystemOutput> injureSystemOutputFilter =
                    injureFilter.systemOutputTransformerForAnswerKey(key);
            key = injureFilter.answerKeyTransformer().apply(key);

			// we're going to group responses by type, role, filler, realis, where the filler is normalized
			final AnswerKeyAnswerSource<TypeRoleFillerRealis> answerKey = AnswerKeyAnswerSource.forAnswerable(
                    key, TypeRoleFillerRealis.extractFromSystemResponse(entityNormalizer));

            SystemOutput rawSystemOutput = systemAnswerStore.readOrEmpty(docid);
            for (final Function<SystemOutput, SystemOutput> systemOutputTransformation : scoringConfiguration.systemOutputTransformations()) {
                rawSystemOutput = systemOutputTransformation.apply(rawSystemOutput);
            }
            rawSystemOutput = temporalFilter.apply(rawSystemOutput);
            rawSystemOutput = injureSystemOutputFilter.apply(rawSystemOutput);

			final SystemOutputAnswerSource<TypeRoleFillerRealis> systemOutput =
				SystemOutputAnswerSource.forAnswerable(rawSystemOutput,
					TypeRoleFillerRealis.extractFromSystemResponse(entityNormalizer));

            final ImmutableMap<KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver,
                    KBPScoringObserver<TypeRoleFillerRealis>> docObserversToCorpusObservers =
                    documentObserversForCorpusObservers(scoringConfiguration.scoringObservers(), answerKey, systemOutput);

            final Set<KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver> docObservers =
                    docObserversToCorpusObservers.keySet();

			// notify observers we are starting a new document
			for (final KBPScoringObserver<?>.KBPAnswerSourceObserver observer : docObservers) {
				observer.start();
			}

			final Set<TypeRoleFillerRealis> allAnswerables = ImmutableSet.copyOf(
				concat(systemOutput.answerables(), answerKey.answerables()));

            // TODO: ByJustification location is not consistent with equals
        		//final Ordering<TypeRoleFillerRealis> order = ByJustificationLocation.create(answerKey, systemOutput);
            //final Ordering<TypeRoleFillerRealis> order = Ordering.

			for (final TypeRoleFillerRealis answerable : allAnswerables) {
				log.info("Scoring equivalence class {}", answerable);

				// notify observers we are starting a new equivalence class
				for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
					observer.startAnswerable(answerable);
				}

				final Set<Response> responses = systemOutput.answers(answerable);
				final Set<AssessedResponse> annotatedResponses = answerKey.answers(answerable);

				// let observers see the responses jointly
				for (final KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver observer : docObservers) {
					observer.observe(answerable, responses, annotatedResponses);
				}

				if (!responses.isEmpty()) {
					// get safe because responses non-empty
					final Response selectedSystemResponse = systemOutput.systemOutput()
							.selectFromMultipleSystemResponses(responses).get();
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
							observer.unannotatedSelectedResponse(answerable, selectedSystemResponse, annotatedResponses);
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
                File outputDir = new File(scorerToOutputDir.get(docObserversToCorpusObservers.get(observer)),
                        docid.toString());
                outputDir.mkdirs();
                observer.writeDocumentOutput(outputDir);
            }
		}

		log.info("Reports for corpus:");
		for (final KBPScoringObserver<?> observer : scoringConfiguration.scoringObservers) {
			observer.endCorpus();
		}

		for (final Map.Entry<KBPScoringObserver<TypeRoleFillerRealis>, File> corpusOutput : scorerToOutputDir.entrySet()) {
			corpusOutput.getKey().writeCorpusOutput(corpusOutput.getValue());
		}

    }

    private static ImmutableMap<KBPScoringObserver<TypeRoleFillerRealis>.KBPAnswerSourceObserver, KBPScoringObserver<TypeRoleFillerRealis>> documentObserversForCorpusObservers(List<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers, AnswerKeyAnswerSource<TypeRoleFillerRealis> answerKey, SystemOutputAnswerSource<TypeRoleFillerRealis> systemOutput) {
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
		final File baseOutputDir, final List<KBPScoringObserver<T>> corpusObservers)
	{
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


	public static final Function<Collection<Response>, Symbol> IsPresent = new Function<Collection<Response>, Symbol> () {
		@Override
		public Symbol apply(final Collection<Response> args) {
			return args.isEmpty()?ABSENT:PRESENT;
		}
	};

	public static final Function<Collection<AssessedResponse>, Symbol> AnyAnswerCorrect = new Function<Collection<AssessedResponse>, Symbol> () {
		@Override
		public Symbol apply(final Collection<AssessedResponse> args) {
			return any(args, AssessedResponse.IsCompletelyCorrect)?PRESENT:ABSENT;
		}
	};

	public static final Function<Collection<AssessedResponse>, Symbol> AnyAnswerSemanticallyCorrect = new Function<Collection<AssessedResponse>, Symbol> () {
		@Override
		public Symbol apply(final Collection<AssessedResponse> args) {
			return any(args, IsCorrectUpToInexactJustifications)?PRESENT:ABSENT;
		}
	};

    /**
     * This object is mutable for convenience, and since it won't be stored.
     */
    public static class ScoringConfiguration {
        private final ImmutableList<Function<AnswerKey, AnswerKey>> answerKeyTransformations;
        private final ImmutableList<Function<SystemOutput, SystemOutput>> systemOutputTransformations;
        private final ImmutableList<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers;

        private ScoringConfiguration(Iterable<Function<AnswerKey, AnswerKey>> answerKeyTransformations,
                                    Iterable<Function<SystemOutput, SystemOutput>> systemOutputTransformations,
                                    Iterable<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers)
        {
            this.answerKeyTransformations = ImmutableList.copyOf(answerKeyTransformations);
            this.systemOutputTransformations = ImmutableList.copyOf(systemOutputTransformations);
            this.scoringObservers = ImmutableList.copyOf(scoringObservers);
        }

        public static ScoringConfiguration create(Iterable<Function<AnswerKey, AnswerKey>> answerKeyTransformations,
                              Iterable<Function<SystemOutput, SystemOutput>> systemOutputTransformations,
                              Iterable<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers)
        {
            return new ScoringConfiguration(answerKeyTransformations, systemOutputTransformations,
                    scoringObservers);
        }

        public static ScoringConfiguration create(Iterable<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers)
        {
            return new ScoringConfiguration(
                    ImmutableList.<Function<AnswerKey, AnswerKey>>of(),
                    ImmutableList.<Function<SystemOutput, SystemOutput>>of(),
                    scoringObservers);
        }

        public ImmutableList<Function<AnswerKey, AnswerKey>> answerKeyTransformations() {
            return answerKeyTransformations;
        }

        public ImmutableList<Function<SystemOutput, SystemOutput>> systemOutputTransformations() {
            return systemOutputTransformations;
        }

        public ImmutableList<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers() {
            return scoringObservers;
        }


    }
}