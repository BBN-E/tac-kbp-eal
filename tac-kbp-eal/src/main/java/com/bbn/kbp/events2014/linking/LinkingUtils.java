package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.AnswerKey;

public final class LinkingUtils {

  private LinkingUtils() {
    throw new UnsupportedOperationException();
  }

  private static final LinkableResponseFilter2015ForGold linkableResponseFilter2015ForGold =
      new LinkableResponseFilter2015ForGold();

  private static final LinkableResponseFilter2015ForSystem linkableResponseFilter2015ForSystem =
      new LinkableResponseFilter2015ForSystem();

  public static AnswerKey.Filter linkableResponseFilter2015ForGold() {
    return linkableResponseFilter2015ForGold;
  }

  public static AnswerKey.Filter linkableResponseFilter2015ForSystemOutput() {
    return linkableResponseFilter2015ForSystem;
  }
}
