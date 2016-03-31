package com.bbn.kbp.events2014.io;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.AssessedResponseFunctions;
import com.bbn.kbp.events2014.CorpusEventFrame;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocEventFrameReference;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.TypeRoleFillerRealisSet;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class SystemOutputLayoutTest {

  final Symbol testDocID = Symbol.from("AFP_ENG_20091024.0206.fake");

  @Test
  public void test2016LayoutReading() throws IOException {
    final SystemOutputStore2016 outputStore =
        (SystemOutputStore2016) SystemOutputLayout.KBP_EA_2016
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

  private void create2016LayoutTest() throws IOException {
    final SystemOutputStore2016 outputStore =
        (SystemOutputStore2016) SystemOutputLayout.KBP_EA_2016
            .openOrCreate(new File(
                this.getClass().getResource("/com/bbn/kbp/events2014/io/CorpusEventTest")
                    .getFile()));

    final File dir = new File(
        SystemOutputLayoutTest.class.getResource("/com/bbn/kbp/events2014/io/linkingTest")
            .getFile());

    final AnswerKey answerKey =
        AssessmentSpecFormats.openAnnotationStore(dir, AssessmentSpecFormats.Format.KBP2015)
            .read(testDocID);

    final ImmutableSet<Response> responses =
        FluentIterable.from(answerKey.annotatedResponses())
            .transform(AssessedResponseFunctions.response())
            .toSet(); //output.arguments().responses();
    final ImmutableMultimap<Symbol, Response> typeToResponse = FluentIterable.from(responses).index(
        ResponseFunctions.type());
    final ResponseLinking.Builder responseLinkingB = ResponseLinking.builder().docID(testDocID);
    for (final Symbol type : typeToResponse.keySet()) {
      responseLinkingB.addResponseSets(ResponseSet.of(typeToResponse.get(type)));
    }
    final ResponseLinking responseLinking = responseLinkingB.build();

    final ImmutableMultimap<TypeRoleFillerRealis, Response> trfrs = FluentIterable.from(responses)
        .index(TypeRoleFillerRealis
            .extractFromSystemResponse(answerKey.corefAnnotation().laxCASNormalizerFunction()));
    final ImmutableSet.Builder<TypeRoleFillerRealisSet> trfrSetsB = ImmutableSet.builder();
    final ImmutableMap.Builder<String, TypeRoleFillerRealisSet> idMapB = ImmutableMap.builder();
    for (final TypeRoleFillerRealis trfr : trfrs.keySet()) {
      final TypeRoleFillerRealisSet trfrSet = TypeRoleFillerRealisSet.create(ImmutableSet.of(trfr));
      idMapB.put(trfr.uniqueIdentifier(), trfrSet);
      trfrSetsB.add(trfrSet);
    }

    final EventArgumentLinking eventArgumentLinking =
        EventArgumentLinking.builder().addAllEventFrames(trfrSetsB.build())
            .idsToEventFrames(idMapB.build()).docID(testDocID).build();
    checkState(eventArgumentLinking.eventFrames().size() > 0);

    final CorpusEventLinking.Builder corpusEventLinkingB;
    if (outputStore.readCorpusEventFrames().corpusEventFrames().size() == 0) {
      corpusEventLinkingB = CorpusEventLinking.builder();
    } else {
      corpusEventLinkingB = CorpusEventLinking.builder().from(outputStore.readCorpusEventFrames());
    }
    final ImmutableMap<String, TypeRoleFillerRealisSet> hopperIDs =
        eventArgumentLinking.idsToEventFrames().get();
    for (final String id : hopperIDs.keySet()) {
      corpusEventLinkingB.addCorpusEventFrames(CorpusEventFrame.of(id,
          ImmutableSet.of(DocEventFrameReference.of(testDocID, id))));
    }

    final DocumentSystemOutput2015 documentSystemOutput2015 = DocumentSystemOutput2015
        .from(ArgumentOutput.createWithConstantScore(testDocID, answerKey.allResponses(), 1.0),
            responseLinking);
    outputStore.write(documentSystemOutput2015);
    outputStore.writeCorpusEventFrames(corpusEventLinkingB.build());
    outputStore.close();

  }

}
