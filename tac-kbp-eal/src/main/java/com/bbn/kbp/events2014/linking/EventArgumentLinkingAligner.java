package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.ResponseLinking;

/**
 * Creates an {@link com.bbn.kbp.events2014.EventArgumentLinking} from a {@link
 * com.bbn.kbp.events2014.ResponseLinking} and an {@link com.bbn.kbp.events2014.AnswerKey} and
 * vice-versa.
 */
public interface EventArgumentLinkingAligner {

  /**
   *
   * @param responseLinking
   * @param corefAnnotation
   * @return
   * @throws {@link com.bbn.kbp.events2014.linking.EventArgumentLinkingAligner.InconsistentLinkingException}
   */
  EventArgumentLinking align(ResponseLinking responseLinking,
      AnswerKey corefAnnotation);

  ResponseLinking alignToResponseLinking(EventArgumentLinking eventArgumentLinking,
      AnswerKey answerKey);

  class InconsistentLinkingException extends RuntimeException {

    public InconsistentLinkingException(String msg) {
      super(msg);
    }
  }
}
