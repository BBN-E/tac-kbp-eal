package com.bbn.kbp.events2014;

/**
 * The type of a canonical argument string
 */
public enum CASType implements Comparable<CASType> {
  PRONOUN, NOMINAL, NAME;

  public static CASType parseFromERE(String ereType) {
    if ("NAM".equals(ereType)) {
      return NAME;
    } else if ("NOM".equals(ereType)) {
      return NOMINAL;
    } else if ("PRO".equals(ereType)) {
      return PRONOUN;
    } else {
      throw new TACKBPEALException("Cannot parse ERE entity mention type "
          + ereType);
    }
  }
}
