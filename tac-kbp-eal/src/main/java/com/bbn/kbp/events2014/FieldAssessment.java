package com.bbn.kbp.events2014;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;

/**
 * A possible assessment for an assessment field.
 */
public enum FieldAssessment {
  CORRECT, INCORRECT, INEXACT;

  public static final String NIL = "NIL";

  /**
   * Parse a single character to a {@code KBPFieldAnnotation}. "C" is correct, "W" is incorrect, and
   * "I" is inexact.
   */
  public static FieldAssessment parse(final String s) {
    final FieldAssessment ret = characterMap.get(s);
    if (ret == null) {
      throw new RuntimeException(String.format("Cannot parse %s as a field assessment", s));
    }
    return ret;
  }

  /**
   * Same as {@code parse}, except it translates the special values {@code NIL} to {@code
   * Optional.absent()}.
   */
  public static Optional<FieldAssessment> parseOptional(final String s) {
    if (s.equals(NIL)) {
      return Optional.absent();
    } else {
      return Optional.of(parse(s));
    }
  }

  private static final ImmutableBiMap<String, FieldAssessment> characterMap = ImmutableBiMap.of(
      "C", FieldAssessment.CORRECT,
      "W", FieldAssessment.INCORRECT,
      "I", FieldAssessment.INEXACT);


  /**
   * Returns a single-character representation of this which can be parsed by {@code parse}. This
   * character is valid for writing in the KBP task output file.
   */
  public String asCharacter() {
    return characterMap.inverse().get(this);
  }

  /**
   * Same as {@code asCharacter}, except it returns {@code NIL} if provided with {@code
   * Optional.absent()}.
   */
  public static String asCharacterOrNil(final Optional<FieldAssessment> ann) {
    if (ann.isPresent()) {
      return ann.get().asCharacter();
    } else {
      return NIL;
    }
  }


  /**
   * Returns true if the assessment is not wholly wrong (i.e. not <tt>INCORRECT</tt> or
   * <tt>IGNORE</tt>).
   */
  public boolean isAcceptable() {
    return this != INCORRECT;
  }

  /**
   * Like {@code isAcceptable}, but also accepts {@code Optional.absent()} input and returns false
   * for it.
   */
  public static boolean isAcceptable(final Optional<FieldAssessment> ann) {
    if (ann.isPresent()) {
      return ann.get().isAcceptable();
    } else {
      return false;
    }
  }
}
