package com.bbn.kbp.events2014.bin;


import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CorpusEventFrame;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocEventFrameReference;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;
import com.bbn.kbp.events2014.KBPEA2016OutputLayout;
import com.bbn.kbp.events2014.KBPEA2017OutputLayout;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.CrossDocSystemOutputStore;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;

public final class FilterLinkingStore {

  private FilterLinkingStore() {
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
    final File inputStore = params.getExistingDirectory("inputStore");
    final File outputStore = params.getCreatableDirectory("outputStore");
    final SystemOutputLayout layout = SystemOutputLayout.ParamParser.fromParamVal(
        params.getString("outputLayout"));

    checkArgument(
        (layout instanceof KBPEA2017OutputLayout) || (layout instanceof KBPEA2016OutputLayout),
        "Not a compatible output layout: expected KBP_EAL_2017 or KBP_EAL_2016");

    final CrossDocSystemOutputStore input = (CrossDocSystemOutputStore) layout.open(inputStore);
    final CrossDocSystemOutputStore output =
        (CrossDocSystemOutputStore) layout.openOrCreate(outputStore);
    final ImmutableSet<KBPRealis> linkableRealises =
        ImmutableSet.of(KBPRealis.Actual, KBPRealis.Other);

    final Predicate<Response> realisIsLinkable =
        compose(in(linkableRealises), ResponseFunctions.realis());

    // collect all the surviving filtering
    final ImmutableSet.Builder<DocEventFrameReference> survivingIDsB = ImmutableSet.builder();
    for (final Symbol docID : input.docIDs()) {
      final DocumentSystemOutput2015 oldOutput = input.read(docID);
      final ResponseLinking filtered =
          oldOutput.linking().copyWithFilteredResponses(realisIsLinkable);
      for (final String surviving : filtered.responseSetIds().get().keySet()) {
        survivingIDsB.add(DocEventFrameReference.of(docID, surviving));
      }
      final DocumentSystemOutput2015 newOutput =
          DocumentSystemOutput2015.from(oldOutput.arguments(), filtered);
      output.write(newOutput);
    }
    final ImmutableSet<DocEventFrameReference> survivingFrames = survivingIDsB.build();

    // remove those from the CorpusEventLinking
    final CorpusEventLinking.Builder newCorpusLinkingB = CorpusEventLinking.builder();
    final ImmutableMultimap<CorpusEventFrame, DocEventFrameReference> corpusEventToDocEvent =
        input.readCorpusEventFrames().docEventsToCorpusEvents().inverse();
    for (final CorpusEventFrame cef : corpusEventToDocEvent.keySet()) {
      final ImmutableSet<DocEventFrameReference> survivingDocEvents =
          FluentIterable.from(corpusEventToDocEvent.get(cef)).filter(in(survivingFrames)).toSet();
      if (survivingDocEvents.size() > 0) {
        final CorpusEventFrame res = CorpusEventFrame.of(cef.id(), survivingDocEvents);
        newCorpusLinkingB.addCorpusEventFrames(res);
      }
    }

    output.writeCorpusEventFrames(newCorpusLinkingB.build());
  }
}
