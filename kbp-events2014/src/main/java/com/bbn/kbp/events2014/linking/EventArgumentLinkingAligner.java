package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.ResponseLinking;

public interface EventArgumentLinkingAligner {
    public EventArgumentLinking align(ResponseLinking responseLinking,
                         CorefAnnotation corefAnnotation) throws InconsistentLinkingException;

    public static class InconsistentLinkingException extends Exception {
        public InconsistentLinkingException(String msg) {
            super(msg);
        }
    }
}
