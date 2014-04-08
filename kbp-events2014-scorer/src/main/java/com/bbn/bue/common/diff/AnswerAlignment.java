package com.bbn.bue.common.diff;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

/**
 * Represents an alignment between two collections of 'answers' where answers are grouped
 * by the question they are trying to answer.
 *
 * @author rgabbard
 *
 * @param <Answerable>
 * @param <LeftAnswer>
 * @param <RightAnswer>
 */
public final class AnswerAlignment<Answerable, LeftAnswer, RightAnswer> {
	private final ImmutableSet<Answerable> answerables;
	private final ImmutableMultimap<Answerable, LeftAnswer> ecToLeft;
	private final ImmutableMultimap<Answerable, RightAnswer> ecToRight;

	public static <Answerable, LeftAnswer, RightAnswer>
		AnswerAlignment<Answerable, LeftAnswer, RightAnswer> create(
		final Multimap<Answerable, LeftAnswer> equivalenceClassesToLeftItems,
		final Multimap<Answerable, RightAnswer> equivalenceClassesToRightItems)
	{
		return new AnswerAlignment<Answerable, LeftAnswer, RightAnswer>(equivalenceClassesToLeftItems, equivalenceClassesToRightItems);
	}

	public List<AlignedAnswers<Answerable, LeftAnswer, RightAnswer>> alignmentsForAnswerables() {
		return ImmutableList.copyOf(Iterables.transform(answerables,
			new Function<Answerable, AlignedAnswers<Answerable, LeftAnswer, RightAnswer>> () {
				@Override
				public AlignedAnswers<Answerable, LeftAnswer, RightAnswer> apply(final Answerable answerable) {
					return AlignedAnswers.create(answerable,
						ecToLeft.get(answerable), ecToRight.get(answerable));
				}
		}));
	}

	/**
	 * Returns {@link AlignedAnswers} where there is a left answer but no right answer.
	 * @return
	 */
	public List<AlignedAnswers<Answerable, LeftAnswer, RightAnswer>> unalignedLeft() {
		return ImmutableList.copyOf(Iterables.filter(alignmentsForAnswerables(), new Predicate<AlignedAnswers<?,?,?>>() {
			@Override
			public boolean apply(final AlignedAnswers<?,?,?> cell) {
				return !cell.leftAnswers().isEmpty() && cell.rightAnswers().isEmpty();
			}
		}));
	}

	private AnswerAlignment(
			final Multimap<Answerable, LeftAnswer> equivalenceClassesToLeftItems,
			final Multimap<Answerable, RightAnswer> equivalenceClassesToRightItems)
	{
		this.ecToLeft = ImmutableMultimap.copyOf(equivalenceClassesToLeftItems);
		this.ecToRight = ImmutableMultimap.copyOf(equivalenceClassesToRightItems);

		// build set of equivalence classes
		final ImmutableSet.Builder<Answerable> classesBuilder = ImmutableSet.builder();
		classesBuilder.addAll(equivalenceClassesToLeftItems.keySet());
		classesBuilder.addAll(equivalenceClassesToRightItems.keySet());
		this.answerables = classesBuilder.build();
	}
}
