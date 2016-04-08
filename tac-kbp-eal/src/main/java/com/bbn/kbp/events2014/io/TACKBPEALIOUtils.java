package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.TACKBPEALException;

final class TACKBPEALIOUtils {

  private TACKBPEALIOUtils() {
    throw new UnsupportedOperationException();
  }

  public static CharOffsetSpan parseCharOffsetSpan(final String s) {
    final String[] parts = s.split("-");
    if (parts.length != 2) {
      throw new TACKBPEALException(String.format("Invalid span %s", s));
    }
    return CharOffsetSpan.fromOffsetsOnly(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
  }

  public static OffsetRange<CharOffset> parseCharOffsetRange(final String s) {
    return parseCharOffsetSpan(s).asCharOffsetRange();
  }

}
