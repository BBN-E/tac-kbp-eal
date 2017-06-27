package com.bbn.kbp.events2014.io;

import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.CharSink;

import java.io.IOException;
import java.util.List;

import static com.google.common.collect.Iterables.transform;

/**
 * Created by rgabbard on 6/26/17.
 */
abstract class AbstractKBPSpecLinkingWriter implements LinkingFileWriter {

  @Override
  public void write(ResponseLinking responseLinking, CharSink sink) throws IOException {
    final List<String> lines = Lists.newArrayList();
    for (final ResponseSet responseSet : responseLinking.responseSets()) {
      lines.add(renderLine(responseSet, responseLinking));
    }

    // incompletes last
    lines.add("INCOMPLETE\t" + Joiner.on("\t").join(
        transform(responseLinking.incompleteResponses(), ResponseFunctions.uniqueIdentifier())));

    sink.writeLines(lines, "\n");
  }

  abstract String renderLine(ResponseSet responseSet, ResponseLinking responseLinking)
      throws IOException;
}
