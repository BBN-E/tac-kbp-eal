package com.bbn.kbp.events2014;

/**
 * Possible assessments of corpus-level query responses. The order is important: the later
 * values in the order will "trump" other values if there are multiple assessments for a document
 * when merging query responses by ignoring realis.
 */
public enum QueryAssessment2016 {
  UNASSESSED,
  ET_MATCH,
  WRONG,
  CORRECT
}
