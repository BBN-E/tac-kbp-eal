package com.bbn.kbp.events2014;

import com.google.common.base.Optional;

/**
 * Created by rgabbard on 3/22/16.
 */
public enum FillerMentionType {
  NOMINAL, NAME;

  public static String stringOrNil(final Optional<FillerMentionType> mt) {
    if (mt.isPresent()) {
      return mt.get().toString();
    } else {
      return "NIL";
    }
  }

  public static Optional<FillerMentionType> parseOptional(final String s) {
    if ("NIL".equals(s)) {
      return Optional.absent();
    } else {
      return Optional.of(FillerMentionType.valueOf(s));
    }
  }
}
