package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CorpusEventFrame;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocEventFrameReference;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

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
