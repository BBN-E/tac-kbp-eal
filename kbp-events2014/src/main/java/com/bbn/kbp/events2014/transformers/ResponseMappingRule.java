package com.bbn.kbp.events2014.transformers;

import com.bbn.kbp.events2014.AnswerKey;

/**
 * A pre-processor rule which for every response either (a) leaves it untouched, (b) delete it, or
 * (c) maps it to another, possibly new, response.
 */
public interface ResponseMappingRule {

  ResponseMapping computeResponseTransformation(AnswerKey answerKey);

}
