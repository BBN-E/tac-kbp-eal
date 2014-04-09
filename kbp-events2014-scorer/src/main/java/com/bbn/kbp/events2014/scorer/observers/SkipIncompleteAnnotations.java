package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Wraps another observer and causes it to skip all documents which are not completely
 * annotated.
 * @param <Answerable>
 */
public class SkipIncompleteAnnotations<Answerable> extends KBPScoringObserver<Answerable> {
	private static Logger log = LoggerFactory.getLogger(SkipIncompleteAnnotations.class);

	private final KBPScoringObserver<Answerable> innerObserver;
	private int docs = 0;
	private int skipped = 0;

	private SkipIncompleteAnnotations(final KBPScoringObserver<Answerable> innerObserver) {
        super(innerObserver.name());
		this.innerObserver = checkNotNull(innerObserver);
	}

	public static <Answerable> SkipIncompleteAnnotations<Answerable> wrap(
		final KBPScoringObserver<Answerable> innerObserver)
	{
		return new SkipIncompleteAnnotations<Answerable>(innerObserver);
	}

    public void startCorpus() {
        innerObserver.startCorpus();
    }

	@Override
	public void endCorpus() {
		if (docs > 0) {
			log.info(String.format("Skipped %d of %d documents (%.2f%%)\n", skipped, docs,
				(100.0*skipped)/docs));
		} else {
			log.info("No documents observed.");
		}
		innerObserver.endCorpus();
	}

	@Override
	public KBPAnswerSourceObserver answerSourceObserver(
			final SystemOutputAnswerSource<Answerable> systemOutputSource,
			final AnswerKeyAnswerSource<Answerable> answerKeyAnswerSource)
	{
		++docs;
		if (answerKeyAnswerSource.answerKey().completelyAnnotated()) {
			return innerObserver.answerSourceObserver(systemOutputSource,
				answerKeyAnswerSource);
		} else {
            ++skipped;
            return new KBPAnswerSourceObserver(systemOutputSource,
				answerKeyAnswerSource) {};
		}
	}

	/*public static <Answerable> Function<KBPScoringObserver<Answerable>, KBPScoringObserver<Answerable>> SkipIncomplete() {
		return new Function<KBPScoringObserver<Answerable>, KBPScoringObserver<Answerable>>() {
			@Override
			public KBPScoringObserver<Answerable> apply(final KBPScoringObserver<Answerable> x) {
				return SkipIncompleteAnnotations.wrap(x);
			}
		};
	}*/
}
