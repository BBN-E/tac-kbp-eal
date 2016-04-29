package com.bbn.kbp.events2014.validation;

import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;

/**
 * Validates a ResponseLinking object
 */
public interface LinkingValidator {

  /**
   * Establishes the validity of an {@link ResponseLinking}
   * @param linking
   */
  boolean validate(ResponseLinking linking);

  /**
   * Determines if two Responses are valid to link.
   * This must be symmetric, that is {@code validToLink(a,b) == validToLink(b,a)}
   */
  boolean validToLink(Response a, Response b);
}
