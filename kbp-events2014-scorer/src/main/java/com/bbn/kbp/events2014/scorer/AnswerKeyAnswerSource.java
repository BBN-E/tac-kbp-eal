package com.bbn.kbp.events2014.scorer;

import java.util.Set;

import com.bbn.bue.common.diff.AnswerSource;
import com.bbn.kbp.events2014.AsssessedResponse;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import static com.bbn.kbp.events2014.AsssessedResponse.Response;
import static com.google.common.base.Functions.compose;

import static com.google.common.base.Preconditions.checkNotNull;

public class AnswerKeyAnswerSource<Answerable> implements AnswerSource<Answerable, AsssessedResponse> {
	private final AnswerKey answerKey;
	private final Multimap<Answerable, AsssessedResponse> equivalenceClasses;

	private AnswerKeyAnswerSource(final AnswerKey answerKey,
		final Multimap<Answerable, AsssessedResponse> equivalenceClasses)
	{
		this.answerKey = checkNotNull(answerKey);
		this.equivalenceClasses = ImmutableMultimap.copyOf(equivalenceClasses);
	}

	public static <Answerable> AnswerKeyAnswerSource<Answerable> forAnswerable(
		final AnswerKey answerKey, final Function<Response, Answerable> answerableExtractor)
	{
		return new AnswerKeyAnswerSource<Answerable>(answerKey,
			Multimaps.index(answerKey.annotatedResponses(),
				compose(answerableExtractor, Response)));
	}

	@Override
	public Set<Answerable> answerables() {
		return equivalenceClasses.keySet();
	}

	@Override
	public Set<AsssessedResponse> answers(final Answerable answerable) {
		return ImmutableSet.copyOf(equivalenceClasses.get(answerable));
	}

	public AnswerKey answerKey() {
		return answerKey;
	}
}
