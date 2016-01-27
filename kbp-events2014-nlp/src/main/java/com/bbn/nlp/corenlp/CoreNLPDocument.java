package com.bbn.nlp.corenlp;

import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkNotNull;

@Beta
public final class CoreNLPDocument {

  private final ImmutableList<CoreNLPSentence> sentences;

  private CoreNLPDocument(final ImmutableList<CoreNLPSentence> sentences) {
    this.sentences = checkNotNull(sentences);
  }

  public ImmutableList<CoreNLPSentence> sentences() {
    return sentences;
  }

  /**
   * Returns the first {@code CoreNLPSentence} that contains these offsets, if any.
   * @param offsets
   * @return
   */
  public Optional<CoreNLPSentence> sentenceForCharOffsets(final OffsetRange<CharOffset> offsets) {
    for(final CoreNLPSentence sentence: sentences) {
      if(sentence.offsets().contains(offsets)) {
        return Optional.of(sentence);
      }
    }
    return Optional.absent();
  }

  public static StanfordDocumentBuilder builder() {
    return new StanfordDocumentBuilder();
  }

  public static class StanfordDocumentBuilder {

    private ImmutableList<CoreNLPSentence> sentences;

    private StanfordDocumentBuilder() {
    }

    public StanfordDocumentBuilder withSentences(Iterable<CoreNLPSentence> sentences) {
      this.sentences = ImmutableList.copyOf(sentences);
      return this;
    }

    public CoreNLPDocument build() {
      CoreNLPDocument document = new CoreNLPDocument(sentences);
      return document;
    }
  }
}
