package com.bbn.kbp;

import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public final class TacKbp2017KBLoaderTest {
  // we use an empty string because we are testing internal methods, not the load method itself
  private static TacKbp2017KBLoader.TacKbp2017KBLoading getDummyLoading() {
    return new TacKbp2017KBLoader.TacKbp2017KBLoading(CharSource.wrap(""));
  }


  @Test
  public void testNodes() {
    final TacKbp2017KBLoader.TacKbp2017KBLoading loading = getDummyLoading();

    final Node node1 = loading.nodeFor(":Event1");
    final Node node2 = loading.nodeFor(":Event1");
    final Node node3 = loading.nodeFor(":Event2");

    assertEquals(node1, node2);
    assertNotEquals(node1, node3);
  }

  @Test
  public void testTypeAssertion() {
    final TacKbp2017KBLoader.TacKbp2017KBLoading loading = getDummyLoading();

    final Assertion actualAssertion = loading.parse(":String_01f\ttype\tSTRING\t0.5").assertion();
    final Assertion expectedAssertion = TypeAssertion.of(
        loading.nodeFor(":String_01f"), Symbol.from("STRING"));

    assertEquals(expectedAssertion, actualAssertion);
  }

  @Test
  public void testLinkAssertion() {
    final TacKbp2017KBLoader.TacKbp2017KBLoading loading = getDummyLoading();

    final String line = ":Entity_aq3\tlink\t\"ExternalKB:ExternalNodeID\"   # Not a real KB ";
    final Assertion actualAssertion = loading.parse(line).assertion();

    final EntityNode subjectNode = (EntityNode) loading.nodeFor(":Entity_aq3");
    final Assertion expectedAssertion = LinkAssertion.of(
        subjectNode, Symbol.from("ExternalKB"), Symbol.from("ExternalNodeID"));

    assertEquals(expectedAssertion, actualAssertion);
  }

  @Test
  public void testSentimentAssertion() {
    final TacKbp2017KBLoader.TacKbp2017KBLoading loading = getDummyLoading();

    final String line = ":Entity1\tper:dislikes\t:Entity2\tdocID:5-12\t\t";
    final Assertion actualAssertion = loading.parse(line).assertion();
    final Assertion expectedAssertion = SentimentAssertion.builder()
        .subject((EntityNode) loading.nodeFor(":Entity1"))
        .object((EntityNode) loading.nodeFor(":Entity2"))
        .subjectEntityType(Symbol.from("per"))
        .sentiment(Symbol.from("dislikes"))
        .predicateJustification(
            JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(5, 12)))
        .build();

    assertEquals(expectedAssertion, actualAssertion);
  }

  @Test
  public void testSFAssertion() {
    final TacKbp2017KBLoader.TacKbp2017KBLoading loading = getDummyLoading();

    final String line = ":Entity1\tper:age\t:String1\tdocID:5-12;docID:5-12\t#t\t";
    final Assertion actualAssertion = loading.parse(line).assertion();
    final Assertion expectedAssertion = SFAssertion.builder()
        .subject((EntityNode) loading.nodeFor(":Entity1"))
        .object((StringNode) loading.nodeFor(":String1"))
        .subjectEntityType(Symbol.from("per"))
        .relation(Symbol.from("age"))
        .fillerString(
            JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(5, 12)))
        .predicateJustification(ImmutableSet
            .of(JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(5, 12))))
        .build();

    assertEquals(expectedAssertion, actualAssertion);
  }

  @Test
  public void testEventArgumentAssertion() {
    final TacKbp2017KBLoader.TacKbp2017KBLoading loading = getDummyLoading();

    final String line =
        ":Event_0\tlife.die:victim.actual\t:Entity_0\tdocID:5-12;docID:10-15;docID:5-12";
    final Assertion actualAssertion = loading.parse(line).assertion();
    final Assertion expectedAssertion = EventArgumentAssertion.builder()
        .subject((EventNode) loading.nodeFor(":Event_0"))
        .argument((EntityNode) loading.nodeFor(":Entity_0"))
        .eventType(Symbol.from("life.die"))
        .role(Symbol.from("victim"))
        .realis(Symbol.from("actual"))
        .baseFiller(JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(5, 12)))
        .predicateJustification(ImmutableSet.of(
            JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(10, 15))))
        .additionalJustifications(ImmutableSet.of(
            JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(5, 12))))
        .build();

    assertEquals(expectedAssertion, actualAssertion);
  }

  @Test
  public void testInverseEventArgumentAssertion() {
    final TacKbp2017KBLoader.TacKbp2017KBLoading loading = getDummyLoading();

    final String line =
        ":Entity_0\tper:life.die_victim.actual\t:Event_0\tdocID:5-12;docID:5-12,docID:10-15;docID:10-15";
    final Assertion actualAssertion = loading.parse(line).assertion();
    final Assertion expectedAssertion = EntityInverseEventArgumentAssertion.builder()
        .eventNode((EventNode) loading.nodeFor(":Event_0"))
        .subjectEntityType(Symbol.from("per"))
        .subject((EntityNode) loading.nodeFor(":Entity_0"))
        .eventType(Symbol.from("life.die"))
        .role(Symbol.from("victim"))
        .realis(Symbol.from("actual"))
        .baseFiller(JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(5, 12)))
        .predicateJustification(ImmutableSet.of(
            JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(5, 12)),
            JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(10, 15))))
        .additionalJustifications(ImmutableSet
            .of(JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(10, 15))))
        .build();

    assertEquals(expectedAssertion, actualAssertion);
  }

  @Test
  public void testMentionAssertion() {
    final TacKbp2017KBLoader.TacKbp2017KBLoading loading = getDummyLoading();

    final String line =
        ":Event_0\tcanonical_mention.actual\t\"dummy\\\"mention\\\"\"\tdocID:5-12";
    final Assertion actualAssertion = loading.parse(line).assertion();
    final Assertion expectedAssertion = EventCanonicalMentionAssertion.of(
        (EventNode) loading.nodeFor(":Event_0"),
        "dummy\"mention\"",
        Symbol.from("actual"),
        JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(5, 12)));

    assertEquals(expectedAssertion, actualAssertion);
  }

  @Test
  public void testConfidence() {
    final TacKbp2017KBLoader.TacKbp2017KBLoading loading = getDummyLoading();

    final double confidence = loading.parse(":String_01f\ttype\tSTRING\t0.5  ").confidence().get();
    assertEquals(0.5, confidence, 0.0);
  }

  @Test
  public void testLoad() throws IOException {
    final KnowledgeBaseLoader loader = TacKbp2017KBLoader.create();

    final String inputString = "dummy_runID\n"
        + "\n# This is a dummy comment. \n \n"
        + ":Event_0\ttype\tCONFLICT.ATTACK\t0.900  \n"
        + ":Event_0\tmention.actual\t\"dummy\\\"mention\\\"\"\tdocID:5-12";

    final File inputFile = File.createTempFile("kb-loader-test", ".tmp");
    Files.write(inputString, inputFile, Charsets.UTF_8);
    final KnowledgeBase actualKB = loader.load(Files.asCharSource(inputFile, Charsets.UTF_8));
    inputFile.deleteOnExit();

    final EventNode node = (EventNode) actualKB.nodes().asList().get(0);
    final Assertion assertion1 = TypeAssertion.of(node, Symbol.from("CONFLICT.ATTACK"));
    final Assertion assertion2 = EventMentionAssertion.of(
        node, "dummy\"mention\"", Symbol.from("actual"),
        JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(5, 12)));

    final KnowledgeBase expectedKB = KnowledgeBase.builder()
        .runId(Symbol.from("dummy_runID"))
        .addNodes(node)
        .nameNode(node, ":Event_0")
        .registerAssertion(assertion1, 0.9)
        .addAssertions(assertion2)
        .build();

    assertEquals(expectedKB, actualKB);
  }

}
