package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.ResponseLinking;

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
