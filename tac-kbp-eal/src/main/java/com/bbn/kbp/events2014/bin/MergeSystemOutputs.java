package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.TACKBPEALException;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.bbn.kbp.events2014.CorpusEventFrameFunctions.id;

/**
 * Merge together system output stores over disjoint documents sets.
 */
public final class MergeSystemOutputs {

  private MergeSystemOutputs() {
    throw new UnsupportedOperationException();
  }

  public static void main(String[] argv) {
    // we wrap the main method in this way to
    // ensure a non-zero return value on failure
    try {
      trueMain(argv);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void trueMain(String[] argv) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final File inputStore = params.getExistingDirectory("input1");
    final File inputStore2 = params.getExistingDirectory("input2");
    final File outputStore = params.getCreatableDirectory("outputStore");

    final SystemOutputStore2016 input1 = SystemOutputStore2016.open(inputStore);
    final SystemOutputStore2016 input2 = SystemOutputStore2016.open(inputStore2);
    final SystemOutputStore2016 output = SystemOutputStore2016.openOrCreate(outputStore);

    final Set<Symbol> commonDocIDs = Sets.intersection(input1.docIDs(), input2.docIDs());
    if (!commonDocIDs.isEmpty()) {
      throw new TACKBPEALException("Can only merge disjoint system output stores, but the following"
          + "docIDs are shared: " + commonDocIDs);
    }

    // merge document-level output
    for (final SystemOutputStore2016 input : ImmutableList.of(input1, input2)) {
      for (final Symbol docID : input.docIDs()) {
        output.write(input.read(docID));
      }
    }

    // merge corpus-event frames
    output.writeCorpusEventFrames(
        merge(input1.readCorpusEventFrames(), input2.readCorpusEventFrames()));

    input1.close();
    input2.close();
    output.close();
  }

  private static CorpusEventLinking merge(final CorpusEventLinking corpusLinking1,
      final CorpusEventLinking corpusLinking2) {
    final Set<String> commonIDs = Sets.intersection(
        FluentIterable.from(corpusLinking1.corpusEventFrames())
            .transform(id())
            .toSet(),
        FluentIterable.from(corpusLinking2.corpusEventFrames())
            .transform(id())
            .toSet());

    if (!commonIDs.isEmpty()) {
      throw new TACKBPEALException("Expected no common corpus event frame IDs, but got "
          + commonIDs);
    }
    return CorpusEventLinking.builder()
        .addAllCorpusEventFrames(corpusLinking1.corpusEventFrames())
        .addAllCorpusEventFrames(corpusLinking2.corpusEventFrames())
        .build();
  }
}
