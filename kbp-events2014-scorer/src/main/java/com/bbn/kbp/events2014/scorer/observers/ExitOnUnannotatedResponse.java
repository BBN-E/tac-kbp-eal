package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.IncompleteAnnotationException;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;

/**
 * Does nothing except throw an exception if it encounters and unannotated response. You want to do this
 * a evaluation-time scoring, for example.
 * @param <Answerable>
 */
public final class ExitOnUnannotatedResponse<Answerable> extends KBPScoringObserver<Answerable> {

	private ExitOnUnannotatedResponse() {}
	public static <Answerable> ExitOnUnannotatedResponse<Answerable> create() { return new ExitOnUnannotatedResponse<Answerable>();}

	@Override
	public KBPAnswerSourceObserver answerSourceObserver(
			final SystemOutputAnswerSource<Answerable> systemOutputSource,
			final AnswerKeyAnswerSource<Answerable> answerKeyAnswerSource)
	{
		return new KBPAnswerSourceObserver(systemOutputSource, answerKeyAnswerSource) {
			@Override
			public void unannotatedSelectedResponse(final Answerable answerable, final Response unannotated) {
				throw IncompleteAnnotationException.forMissingResponse(unannotated);
			}
		};
	}

}
