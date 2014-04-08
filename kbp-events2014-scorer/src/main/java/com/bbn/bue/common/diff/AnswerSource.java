package com.bbn.bue.common.diff;

import java.util.Set;

public interface AnswerSource<Answerable, Answer> {
	public Set<Answerable> answerables();
	public Set<Answer> answers(Answerable answerable);
}
