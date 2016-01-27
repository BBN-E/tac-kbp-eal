package com.bbn.nlp.corenlp;

import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkNotNull;

@Beta
public final class CoreNLPSentence {

  private final ImmutableList<CoreNLPToken> tokens;
  private final Optional<CoreNLPConstituencyParse> parse;

  private CoreNLPSentence(final Iterable<CoreNLPToken> tokens,
      final Optional<CoreNLPConstituencyParse> parse) {
    this.tokens = ImmutableList.copyOf(tokens);
    this.parse = checkNotNull(parse);
  }

  public ImmutableList<CoreNLPToken> tokens() {
    return tokens;
  }

  public Optional<CoreNLPConstituencyParse> parse() {
    return parse;
  }

  /**
   * Span from the start of the first token to the end of the last.
   * @return
   */
  public OffsetRange<CharOffset> offsets() {
    final CharOffset start = tokens.get(0).offsets().startInclusive();
    final CharOffset end = tokens.reverse().get(0).offsets().endInclusive();
    return OffsetRange.charOffsetRange(start.asInt(), end.asInt());
  }

  /**
   * Finds the smallest {@code ConstituentNode} containing these offsets. {@code CoreNLPParseNode}s
   * will not generally contain whitespace.
   */
  public Optional<CoreNLPParseNode> nodeForOffsets(
      final OffsetRange<CharOffset> offsets) {
    for (final CoreNLPParseNode n : parse.get().root()
        .postorderTraversal()) {
      if (n.span().contains(offsets)) {
        return Optional.of(n);
      }
    }
    return Optional.absent();
  }

  public static StanfordSentenceBuilder builder() {
    return new StanfordSentenceBuilder();
  }

  public static class StanfordSentenceBuilder {

    private Iterable<CoreNLPToken> tokens;
    private Optional<CoreNLPConstituencyParse> parse;

    private StanfordSentenceBuilder() {
    }

    public StanfordSentenceBuilder withTokens(Iterable<CoreNLPToken> tokens) {
      this.tokens = tokens;
      return this;
    }

    public StanfordSentenceBuilder withParse(Optional<CoreNLPConstituencyParse> parse) {
      this.parse = parse;
      return this;
    }

    public CoreNLPSentence build() {
      CoreNLPSentence sentence = new CoreNLPSentence(tokens, parse);
      return sentence;
    }
  }
}
