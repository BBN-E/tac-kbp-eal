package com.bbn.kbp.events2014.io;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CorpusEventFrame;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocEventFrameReference;
import com.bbn.kbp.events2014.DocumentSystemOutput;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;
import com.bbn.kbp.events2014.KBPEA2016OutputLayout;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;

public final class SystemOutputLayoutTest {

  private static final Logger log = LoggerFactory.getLogger(SystemOutputLayoutTest.class);

  // ignored until @jdeyoung updates it for event frame IDs
  // issue kbp#153
  @Ignore
  @Test
  public void test2016LayoutReading() throws IOException {
    final SystemOutputStore2016 outputStore =
        (SystemOutputStore2016) KBPEA2016OutputLayout.get()
            .open(new File(this.getClass().getResource("/com/bbn/kbp/events2014/io/CorpusEventTest")
                .getFile()));
    for (final Symbol docID : outputStore.docIDs()) {
      final DocumentSystemOutput2015 store2015 = outputStore.read(docID);
      checkNotNull(store2015.arguments());
      checkState(store2015.arguments().responses().size() > 0);
      checkNotNull(store2015.linking());
      checkState(store2015.linking().responseSets().size() > 0);
    }
    checkState(outputStore.readCorpusEventFrames().corpusEventFrames().size() > 0);
  }

  public static void main(String... args) throws IOException {
    // this must point to a directory via your filesystem, not a resource, unless you want to debug.
    // what are you doing here anyway?
    final File dir = new File(args[0]);

    final SystemOutputStore sourceLayout = KBPEA2016OutputLayout.get().open(dir);
    final ImmutableMultimap.Builder<Symbol, DocEventFrameReference>
        eventTypeToEventFrameReferenceB = ImmutableMultimap.builder();

    int responseIDCount = 0;
    for (final Symbol docID : sourceLayout.docIDs()) {
      log.info("Processing {}", docID);
      final DocumentSystemOutput answerKey = sourceLayout.read(docID);

      // coref everything non-generic of the same event type in the same document
      final ImmutableSet<Response> responses =
          FluentIterable.from(answerKey.arguments().responses())
              .filter(compose(not(in(ImmutableSet.of(
                  KBPRealis.Generic))), ResponseFunctions.realis())).toSet();
      final ImmutableMultimap<Symbol, Response> typeToResponse =
          FluentIterable.from(responses).index(
              ResponseFunctions.type());
      final ImmutableBiMap.Builder<String, ResponseSet> responseSetIDs = ImmutableBiMap.builder();
      final ResponseLinking.Builder responseLinkingB = ResponseLinking.builder().docID(docID);
      for (final Symbol type : typeToResponse.keySet()) {
        checkState(typeToResponse.get(type).size() > 0);
        final ResponseSet r = ResponseSet.from(typeToResponse.get(type));
        responseLinkingB.addResponseSets(r);
        final String id = Integer.toString(responseIDCount++);
        responseSetIDs.put(id, r);
        eventTypeToEventFrameReferenceB.put(type, DocEventFrameReference.of(docID, id));
      }
      responseLinkingB.responseSetIds(responseSetIDs.build());
      final ResponseLinking responseLinking = responseLinkingB.build();
      log.info("ResponseLinking size is {}", responseLinking.responseSets().size());

      final DocumentSystemOutput2015 documentSystemOutput2015 = DocumentSystemOutput2015
          .from(answerKey.arguments(), responseLinking);
      sourceLayout.write(documentSystemOutput2015);
    }
    sourceLayout.close();

    // this should really point to the same place as used above in the event of regenerating the example output.
    final SystemOutputStore2016 outputStore = KBPEA2016OutputLayout.get().openOrCreate(dir);

    final CorpusEventLinking.Builder corpusEventLinkingB = CorpusEventLinking.builder();
    final ImmutableMultimap<Symbol, DocEventFrameReference> eventTypeToHoppers =
        eventTypeToEventFrameReferenceB.build();
    for (final Symbol ET : eventTypeToHoppers.keySet()) {
      corpusEventLinkingB.addCorpusEventFrames(CorpusEventFrame
          .of(Integer.toString(eventTypeToHoppers.get(ET).hashCode()), eventTypeToHoppers.get(ET)));
    }
    outputStore.writeCorpusEventFrames(corpusEventLinkingB.build());

    outputStore.close();
  }
}
