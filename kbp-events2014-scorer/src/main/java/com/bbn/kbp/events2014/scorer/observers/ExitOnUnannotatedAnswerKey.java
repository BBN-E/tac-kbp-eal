package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;

/**
 * Does nothing except throw an exception if it encounters an unannotated answer key.
 * @param <Answerable>
 */
public final class ExitOnUnannotatedAnswerKey<Answerable> extends KBPScoringObserver<Answerable> {

    private ExitOnUnannotatedAnswerKey() {}
    public static <Answerable> ExitOnUnannotatedAnswerKey<Answerable> create() {
        return new ExitOnUnannotatedAnswerKey<Answerable>();
    }

    @Override
    public KBPAnswerSourceObserver answerSourceObserver(
            final SystemOutputAnswerSource<Answerable> systemOutputSource,
            final AnswerKeyAnswerSource<Answerable> answerKeyAnswerSource)
    {
        return new KBPAnswerSourceObserver(systemOutputSource, answerKeyAnswerSource) {
            @Override
            public void start() {
                if (!answerKey().answerKey().completelyAnnotated()) {
                    throw new RuntimeException(String.format(
                            "All answer keys required to be completely annotated, but %s is not",
                            answerKey().answerKey().docId()));
                }
            }
        };
    }

}
