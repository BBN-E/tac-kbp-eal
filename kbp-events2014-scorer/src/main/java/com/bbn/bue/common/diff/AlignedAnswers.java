package com.bbn.bue.common.diff;

import java.util.Collection;

import com.bbn.bue.common.StringUtils;
import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Holds two different collections of answers to a single {@link Answerable} item.  For example,
 * in scoring a parser, the {@code Answerable} might be a span of words, the {@code leftAnswers}
 * could be the parser predicted non-terminal symbols (could be more than one in the case of
 * unary projections) and the {@code rightAnswers} could be the gold standard answers.
 *
 * @author rgabbard
 *
 * @param <Answerable> The type of item some sort of answer or label is being given to.
 * @param <LeftAnswer> The type of the left answers.
 * @param <RightAnswer> The type of the right answers. This will sometimes but not always be the same as the left answers.
 */
public final class AlignedAnswers<Answerable, LeftAnswer, RightAnswer> {
	private AlignedAnswers(final Answerable answerable, final Iterable<LeftAnswer> leftAnswers, final Iterable<RightAnswer> rightAnswers) {
		this.answerable = checkNotNull(answerable);
		this.leftAnswers = ImmutableList.copyOf(leftAnswers);
		this.rightAnswers = ImmutableList.copyOf(rightAnswers);
	}

	public static <Answerable, LeftAnswer, RightAnswer>
	AlignedAnswers<Answerable, LeftAnswer, RightAnswer> create(
		final Answerable answerable, final Iterable<LeftAnswer> leftAnswers, final Iterable<RightAnswer> rightAnswers)
	{
		return new AlignedAnswers<Answerable, LeftAnswer, RightAnswer>(answerable, leftAnswers, rightAnswers);
	}

	public Answerable answerable() {
		return answerable;
	}

	public Collection<LeftAnswer> leftAnswers() {
		return leftAnswers;
	}

	public Collection<RightAnswer> rightAnswers() {
		return rightAnswers;
	}

	final Answerable answerable;
	final ImmutableList<LeftAnswer> leftAnswers;
	final ImmutableList<RightAnswer> rightAnswers;

	@Override
	public String toString() {
		return "[AlignedAnswers for "+ answerable.toString() + ":\n"
				+ "Left answers:\n"
				+ StringUtils.NewlineJoiner.join(leftAnswers)
				+"\nRight answers:\n"
				+ StringUtils.NewlineJoiner.join(rightAnswers)
				+ "]\n";
	}

}
