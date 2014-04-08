package com.bbn.kbp.events2014.scorer.observers;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * When applied to incomplete assessment, determines what items need to be annotated in order to end up with
 * complete assessment.  For example, in the KBP argument attachment task, if there is a system answer which does
 * not appear in the gold standard, we don't know if it is a false positive or true positive unless it is
 * annotated.
 *
 * @author rgabbard
 *
 * @param <Answerable>
 */
public class FindUnannotated<Answerable> extends KBPScoringObserver<Answerable>
{
	private static final Logger log = LoggerFactory.getLogger(FindUnannotated.class);

	private final AnnotationStore annStore;
	private int documentsNeedingAnnotation = 0;
	private int responsesNeedingAnnotation = 0;
	private int totalResponses = 0;
	private int totalDocuments = 0;

	@Override
	public KBPAnswerSourceObserver answerSourceObserver(
			final SystemOutputAnswerSource<Answerable> systemOutputSource,
			final AnswerKeyAnswerSource<Answerable> answerKeyAnswerSource)
	{
		return new KBPAnswerSourceObserver(systemOutputSource, answerKeyAnswerSource)  {
			@Override
			public void start() {
				++totalDocuments;
			}

			@Override
			public void startAnswerable(final Answerable answerable) {
				++totalResponses;
			}

			@Override
			public void unannotatedSelectedResponse(final Answerable answerable, final Response response) {
				log.info("{} needs assessment", response);
				needsAnnotation.add(response);
			}

			@Override
			public void end()  {
				final ImmutableSet<Response> needs = needsAnnotation.build();
				final AnswerKey newAnswerKey = answerKeyAnswerSource().answerKey()
						.copyAddingPossiblyUnannotated(needs);

				responsesNeedingAnnotation += needs.size();
				++documentsNeedingAnnotation;

				try {
					annStore.write(newAnswerKey);
				} catch (final IOException ioe) {
					throw new RuntimeException(ioe);
				}
			}

			private final ImmutableSet.Builder<Response> needsAnnotation = ImmutableSet.builder();
		};
	}

	private FindUnannotated(final AnnotationStore annStore) {
		this.annStore = checkNotNull(annStore);
	}

	public static <Answerable> FindUnannotated<Answerable>
		create(final AnnotationStore annStore)
	{
		return new FindUnannotated<Answerable>(annStore);
	}

	@Override
	public void endCorpus() {
		if (totalResponses > 0) {
			log.info("Need to annotate {}/{} responses ({{}%) for {} documents", responsesNeedingAnnotation,
				totalResponses, (100.0*responsesNeedingAnnotation)/totalResponses,
				documentsNeedingAnnotation);
		}
	}
}