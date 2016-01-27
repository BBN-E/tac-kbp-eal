package com.bbn.nlp.corenlp;

import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Beta
public final class CoreNLPToken {

  private final Symbol POS;
  private final String text;
  private final OffsetRange<CharOffset> offsets;

  private CoreNLPToken(final Symbol pos, final String text, final OffsetRange<CharOffset> offsets) {
    this.offsets = checkNotNull(offsets);
    this.POS = checkNotNull(pos);
    this.text = checkNotNull(text);
    checkArgument(offsets.startInclusive().asInt() >= 0, "Must have a positive start offset");
    checkArgument(offsets.endInclusive().asInt() >= 0, "Must have a positive end offset");
  }

  public static CoreNLPToken create(final Symbol pos, final String text,
      final OffsetRange<CharOffset> offsets) {
    return new CoreNLPToken(pos, text, offsets);
  }

  public String content() {
    return text;
  }

  public Symbol POS() {
    return POS;
  }

  public OffsetRange<CharOffset> offsets() {
    return offsets;
  }

  public static Function<CoreNLPToken, String> contentFunction() {
    return new Function<CoreNLPToken, String>() {
      @Override
      public String apply(final CoreNLPToken input) {
        return input.content();
      }
    };
  }
}
