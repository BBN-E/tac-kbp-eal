package com.bbn.kbp.events2014.linking;

/**
 * Created by jdeyoung on 4/3/15.
 */
public final class EventArgumentLinkingAligners {

  public static EventArgumentLinkingAligner getExactMatchEventArgumentLinkingAligner() {
    return new ExactMatchEventArgumentLinkingAligner();
  }

  public static EventArgumentLinkingAligner getExactMatchWithIncompletesEventArgumentLinkingAligner() {
    return new ExactMatchEventArgumentLinkingWithIncompletes();
  }
}
