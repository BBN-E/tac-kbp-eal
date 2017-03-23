package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CorpusEventFrame;
import com.bbn.kbp.events2014.CorpusEventFrameFunctions;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocEventFrameReference;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import static com.bbn.bue.common.collections.IterableUtils.noneEqualForHashable;
import static com.bbn.kbp.events2014.CorpusEventFrameFunctions.id;
import static com.bbn.kbp.events2014.DocEventFrameReference.canonicalStringFunction;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Writes a collection of corpus-level event frames in the format used for the TAC KBP 2016 Event
 * Argument and Linking Evaluation.
 *
 * From the tasks description: <blockquote> Within this file, each line will correspond to one
 * corpus-level event hopper. The line for an event hopper shall contain two tab separated-fields.
 * The first field shall be an arbitrary unique corpus-level event hopper ID. The second field shall
 * contain a space-separated list of document-level event hoppers. Each event hopper shall be
 * represented as the concatenation of three strings: the document ID, a "-" character, and the
 * document-level hopper ID. Blank lines and lines with ‘#’ as the first character (comments) are
 * allowable and will be ignored. </blockquote>
 */
final class CorpusEventFrameWriter2016 implements CorpusEventFrameWriter {

  private static final Ordering<CorpusEventFrame> BY_ID = Ordering.natural()
      .onResultOf(CorpusEventFrameFunctions.id());
  private static final Joiner SPACE_JOINER = Joiner.on(" ");

  @Override
  public void writeCorpusEventFrames(final CorpusEventLinking corpusEventLinking,
      final CharSink sink) throws IOException {
    checkArgument(noneEqualForHashable(FluentIterable.from(corpusEventLinking.corpusEventFrames())
        .transform(id())));

    try (Writer writer = sink.openBufferedStream()) {
      for (final CorpusEventFrame corpusEventFrame : BY_ID
          .sortedCopy(corpusEventLinking.corpusEventFrames())) {
        final List<DocEventFrameReference> sortedDocEventFrames =
            DocEventFrameReference.canonicalOrdering()
                .sortedCopy(corpusEventFrame.docEventFrames());
        writer.write(
            corpusEventFrame.id()
                + "\t"
                + FluentIterable.from(sortedDocEventFrames)
                .transform(canonicalStringFunction())
                .join(SPACE_JOINER)
                + "\n");
      }
    }
  }
}

final class CorpusEventFrameLoader2016 implements CorpusEventFrameLoader {

  @Override
  public CorpusEventLinking loadCorpusEventFrames(final CharSource source)
      throws IOException {
    int lineNo = 1;
    try (final BufferedReader in = source.openBufferedStream()) {
      final ImmutableSet.Builder<CorpusEventFrame> ret = ImmutableSet.builder();
      String line;
      while ((line = in.readLine()) != null) {
        if (!line.isEmpty() && !line.startsWith("#")) {
          // check for blank or comment lines
          ret.add(parseLine(line));
        }
      }
      return CorpusEventLinking.of(ret.build());
    } catch (Exception e) {
      throw new IOException("Error on line " + lineNo + " of " + source, e);
    }
  }

  @Nullable
  private CorpusEventFrame parseLine(final String line) {
    final List<String> fields = StringUtils.onTabs().splitToList(line);
    if (fields.size() != 2) {
      throw new RuntimeException("Expected two fields, but got " + fields.size());
    }

    final String corpusEventFrameID = fields.get(0);
    final List<DocEventFrameReference> docEventFrameReferences = new ArrayList<>();

    for (final String docEventFrameString : StringUtils.onSpaces().splitToList(fields.get(1))) {
      docEventFrameReferences.add(from2016IOForm(docEventFrameString));
    }

    return CorpusEventFrame.of(corpusEventFrameID, docEventFrameReferences);
  }

  private static DocEventFrameReference from2016IOForm(final String IOForm) {
    // NOTE: docids are allowed to contain strings in them. Particular instances include documents
    // with "-kbp", which we understand to be special tracking markers within the LDC's system.
    // additionally, some documents
    final int lastDash = IOForm.lastIndexOf("-");
    final Symbol docId = Symbol.from(IOForm.substring(0, lastDash));
    final String eventFrameID = IOForm.substring(lastDash + 1);
    return DocEventFrameReference.of(docId, eventFrameID);
  }
}
