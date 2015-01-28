package com.bbn.kbp.events2014;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Newlines are not allowed in KBPStrings and all the constructing static methods will replace each
 * newlines in provided strings with a single space.
 */
public final class KBPString implements Comparable<KBPString> {

  private KBPString(final String string, final CharOffsetSpan offsetSpan) {
    this.string = checkNotNull(string);
    this.offsetSpan = checkNotNull(offsetSpan);
    checkArgument(!string.contains("\n"), "KBP strings may not contain \\n");
  }

  public static KBPString from(@JsonProperty("string") final String string,
      @JsonProperty("offsets") final CharOffsetSpan offsetSpan) {
    final String sEscaped = replaceIllegalCharacters(string);
    return new KBPString(sEscaped, offsetSpan);
  }

  public static KBPString from(final String s, final int startInclusive, final int endInclusive) {
    final String sEscaped = replaceIllegalCharacters(s);
    return KBPString.from(sEscaped, CharOffsetSpan.fromOffsetsAndDebugString(
        startInclusive, endInclusive, sEscaped));
  }


  @JsonProperty("string")
  public String string() {
    return string;
  }

  public CharOffsetSpan charOffsetSpan() {
    return offsetSpan;
  }

  public KBPString copyWithString(String s) {
    return from(s, offsetSpan);
  }

  @Override
  public String toString() {
    return String
        .format("[%d:%d]=%s", offsetSpan.startInclusive(), offsetSpan.endInclusive(), string);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(string, offsetSpan);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final KBPString other = (KBPString) obj;
    return Objects.equal(offsetSpan, other.charOffsetSpan())
        && Objects.equal(string, other.string);
  }

  @Override
  public int compareTo(KBPString o) {
    return ComparisonChain.start()
        .compare(offsetSpan, o.charOffsetSpan())
        .compare(string, o.string).result();
  }

  private final String string;
  private final CharOffsetSpan offsetSpan;

  public static final Function<KBPString, String> Text = new Function<KBPString, String>() {
    @Override
    public String apply(final KBPString input) {
      return input.string();
    }
  };


  private static String replaceIllegalCharacters(String string) {
    return string.replace("\r\n", " ").replace("\n", " ").replace("\t", " ");
  }


}
