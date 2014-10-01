package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.ResponseLinking;

/**
 * Creates an {@link com.bbn.kbp.events2014.EventArgumentLinking} from
 * a {@link com.bbn.kbp.events2014.ResponseLinking} and an {@link com.bbn.kbp.events2014.AnswerKey}
 * and vice-versa.
 */
public interface EventArgumentLinkingAligner {
    EventArgumentLinking align(ResponseLinking responseLinking,
                         AnswerKey corefAnnotation) throws InconsistentLinkingException;

    ResponseLinking alignToResponseLinking(EventArgumentLinking eventArgumentLinking,
                                                  AnswerKey answerKey);

    public static class InconsistentLinkingException extends Exception {
        public InconsistentLinkingException(String msg) {
            super(msg);
        }
    }
}
