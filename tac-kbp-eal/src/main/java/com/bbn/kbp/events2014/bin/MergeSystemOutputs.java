package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CorpusEventFrame;
import com.bbn.kbp.events2014.CorpusEventFrameFunctions;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.KBPEA2016OutputLayout;
import com.bbn.kbp.events2014.KBPEA2017OutputLayout;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.TACKBPEALException;
import com.bbn.kbp.events2014.io.CrossDocSystemOutputStore;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.bbn.kbp.events2014.CorpusEventFrameFunctions.id;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.concat;

/**
 * Merge together system output stores over disjoint documents sets.
 */
public final class MergeSystemOutputs {

  private static final Logger log = LoggerFactory.getLogger(MergeSystemOutputs.class);

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
    final boolean changeDuplicatedIDs = params.getOptionalBoolean("changeDuplicatedIDs").or(false);
    final SystemOutputLayout layout = SystemOutputLayout.ParamParser.fromParamVal(
        params.getString("outputLayout"));
    checkArgument(
        (layout instanceof KBPEA2017OutputLayout) || (layout instanceof KBPEA2016OutputLayout),
        "Not a compatible output layout: expected KBP_EAL_2017 or KBP_EAL_2016");

    final CrossDocSystemOutputStore input1 = (CrossDocSystemOutputStore) layout.open(inputStore);
    final CrossDocSystemOutputStore input2 = (CrossDocSystemOutputStore) layout.open(inputStore2);
    final CrossDocSystemOutputStore output =
        (CrossDocSystemOutputStore) layout.openOrCreate(outputStore);

    final Set<Symbol> commonDocIDs = Sets.intersection(input1.docIDs(), input2.docIDs());
    if (!commonDocIDs.isEmpty()) {
      if (changeDuplicatedIDs) {
        log.warn("Duplicated document IDs found! {}", commonDocIDs);
      } else {
        throw new TACKBPEALException(
            "Can only merge disjoint system output stores, but the following"
                + "docIDs are shared: " + commonDocIDs);
      }
    }

    // merge document-level output
    for (final CrossDocSystemOutputStore input : ImmutableList.of(input1, input2)) {
      for (final Symbol docID : input.docIDs()) {
        output.write(input.read(docID));
      }
    }

    // merge corpus-event frames
    output.writeCorpusEventFrames(
        merge(input1.readCorpusEventFrames(), input2.readCorpusEventFrames(), changeDuplicatedIDs));

    input1.close();
    input2.close();
    output.close();
  }

  private static CorpusEventLinking merge(final CorpusEventLinking corpusLinking1,
      final CorpusEventLinking corpusLinking2, final boolean changeDuplicatedIDs) {
    final Set<String> commonIDs = Sets.intersection(
        FluentIterable.from(corpusLinking1.corpusEventFrames())
            .transform(id())
            .toSet(),
        FluentIterable.from(corpusLinking2.corpusEventFrames())
            .transform(id())
            .toSet()).immutableCopy();

    if (!commonIDs.isEmpty()) {
      return handledIntersectingEventFrameIdCase(corpusLinking1, corpusLinking2,
          changeDuplicatedIDs, commonIDs);
    }
    return CorpusEventLinking.builder()
        .addAllCorpusEventFrames(corpusLinking1.corpusEventFrames())
        .addAllCorpusEventFrames(corpusLinking2.corpusEventFrames())
        .build();
  }

  private static CorpusEventLinking handledIntersectingEventFrameIdCase(
      final CorpusEventLinking corpusLinking1, final CorpusEventLinking corpusLinking2,
      final boolean changeDuplicatedIDs, final Set<String> commonIDs) {
    if (changeDuplicatedIDs) {
      final Set<String> allIds = Sets.newHashSet(FluentIterable
          .from(concat(corpusLinking1.corpusEventFrames(), corpusLinking2.corpusEventFrames()))
          .transform(CorpusEventFrameFunctions.id()).toSet());
      // some systems may produce IDs in order, this isn't strictly necessary
      int newId = allIds.size();
      final CorpusEventLinking.Builder ret = CorpusEventLinking.builder()
          .addAllCorpusEventFrames(corpusLinking1.corpusEventFrames());
      for (final CorpusEventFrame cef : corpusLinking2.corpusEventFrames()) {
        if (commonIDs.contains(cef.id())) {
          while (allIds.contains(Integer.toHexString(newId))) {
            newId++;
          }
          final String id = Integer.toHexString(newId);
          ret.addCorpusEventFrames(cef.withId(id));
          allIds.add(id);
          log.info("Frame id {} remapped to {}", cef.id(), id);
        } else {
          ret.addCorpusEventFrames(cef);
        }
      }
      return ret.build();
    } else {
      throw new TACKBPEALException("Expected no common corpus event frame IDs, but got "
          + commonIDs);
    }
  }
}
