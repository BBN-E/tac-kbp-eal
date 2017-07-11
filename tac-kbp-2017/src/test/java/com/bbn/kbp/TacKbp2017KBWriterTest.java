package com.bbn.kbp;

import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertEquals;

// the deprecated methods used here are ok for test only
@SuppressWarnings("deprecation")
public class TacKbp2017KBWriterTest {

  private final StringNode stringNode0 = StringNode.of();
  private final EventNode eventNode0 = EventNode.of();
  private final EntityNode entityNode0 = EntityNode.of();
  private final EventNode eventNode1 = EventNode.of();
  private final EntityNode entityNode1 = EntityNode.of();

  private final ImmutableMap<Node, String> nodeNames = ImmutableMap.of(
      stringNode0, ":String_000000",
      eventNode0, ":Event_000000",
      eventNode1, ":Event_000001",
      entityNode0, ":Entity_000000",
      entityNode1, ":Entity_000001");

  @Test
  public void testNodeIds() {
    final TacKbp2017KBWriter.TacKbp2017KBWriting writing =
        new TacKbp2017KBWriter.TacKbp2017KBWriting(nodeNames, new Random(0));

    assertEquals(":String_000000", writing.idOf(stringNode0));
    assertEquals(":Event_000000", writing.idOf(eventNode0));
    assertEquals(":Entity_000000", writing.idOf(entityNode0));
    assertEquals(":Event_000001", writing.idOf(eventNode1));
    assertEquals(":Entity_000001", writing.idOf(entityNode1));
  }

  @Test
  public void testTypeAssertion() {
    final TacKbp2017KBWriter.TacKbp2017KBWriting writing =
        new TacKbp2017KBWriter.TacKbp2017KBWriting(nodeNames, new Random(0));

    final TypeAssertion assertion = TypeAssertion.of(stringNode0, Symbol.from("STRING"));
    assertEquals(":String_000000\ttype\tSTRING", writing.assertionToString(assertion));
  }

  @Test
  public void testLinkAssertion() {
    final TacKbp2017KBWriter.TacKbp2017KBWriting writing =
        new TacKbp2017KBWriter.TacKbp2017KBWriting(nodeNames, new Random(0));

    final LinkAssertion assertion = LinkAssertion.of(
        entityNode0, Symbol.from("ExternalKB"), Symbol.from("ExternalNodeID"));
    assertEquals(
        ":Entity_000000\tlink\t\"ExternalKB:ExternalNodeID\"",
        writing.assertionToString(assertion));
  }

  private final JustificationSpan DUMMY =
      JustificationSpan.of(Symbol.from("docID"), OffsetRange.charOffsetRange(5, 12));

  @Test
  public void testSentimentAssertion() {
    final TacKbp2017KBWriter.TacKbp2017KBWriting writing =
        new TacKbp2017KBWriter.TacKbp2017KBWriting(nodeNames, new Random(0));

    final SentimentAssertion assertion = SentimentAssertion.builder()
        .subject(entityNode0)
        .object(entityNode1)
        .subjectEntityType(Symbol.from("per"))
        .sentiment(Symbol.from("dislikes"))
        .predicateAssertion(DUMMY)
        .build();

    assertEquals(
        ":Entity_000000\tper:dislikes\t:Entity_000001\tdocID:5-12",
        writing.assertionToString(assertion));
  }

  @Test
  public void testSFAssertion() {
    final TacKbp2017KBWriter.TacKbp2017KBWriting writing =
        new TacKbp2017KBWriter.TacKbp2017KBWriting(nodeNames, new Random(0));

    final SFAssertion assertion = SFAssertion.builder()
        .subject(entityNode0)
        .object(entityNode1)
        .subjectEntityType(Symbol.from("per"))
        .relation(Symbol.from("city_of_birth"))
        .predicateJustification(ImmutableSet.of(DUMMY))
        .build();

    assertEquals(
        ":Entity_000000\tper:city_of_birth\t:Entity_000001\tdocID:5-12",
        writing.assertionToString(assertion));
  }

  @Test
  public void testEventArgumentAssertion() {
    final TacKbp2017KBWriter.TacKbp2017KBWriting writing =
        new TacKbp2017KBWriter.TacKbp2017KBWriting(nodeNames, new Random(0));

    final EventArgumentAssertion assertion = EventArgumentAssertion.builder()
        .subject(eventNode0)
        .argument(entityNode0)
        .eventType(Symbol.from("conflict.attack"))
        .role(Symbol.from("attacker"))
        .realis(Symbol.from("actual"))
        .baseFiller(DUMMY)
        .predicateJustification(ImmutableSet.of(DUMMY))
        .build();

    assertEquals(
        ":Event_000000\tconflict.attack:attacker.actual\t:Entity_000000\tdocID:5-12;docID:5-12",
        writing.assertionToString(assertion));
  }

  @Test
  public void testMentionAssertion() {
    final TacKbp2017KBWriter.TacKbp2017KBWriting writing =
        new TacKbp2017KBWriter.TacKbp2017KBWriting(nodeNames, new Random(0));

    final MentionAssertion assertion = EventMentionAssertion.of(
        eventNode0, "dummy\tmention", Symbol.from("actual"), DUMMY);
    assertEquals(
        ":Event_000000\tmention.actual\t\"dummy mention\"\tdocID:5-12",
        writing.assertionToString(assertion));
  }

  @Test
  public void testWrite() throws IOException {
    final KnowledgeBaseWriter writer = TacKbp2017KBWriter.create();

    final Assertion assertion1 = TypeAssertion.of(eventNode0, Symbol.from("CONFLICT.ATTACK"));
    final Assertion assertion2 = EventMentionAssertion.of(
        eventNode0, "dummy\tmention", Symbol.from("actual"), DUMMY);
    final Assertion assertion3 = TypeAssertion.of(stringNode0, Symbol.from("STRING"));
    final Assertion assertion4 = NormalizedMentionAssertion.of(
        stringNode0, "XXXX-XX-XX", DUMMY);

    final KnowledgeBase kb = KnowledgeBase.builder()
        .runId(Symbol.from("dummy_runID"))
        .addNodes(eventNode0, stringNode0)
        .nameNode(eventNode0, nodeNames.get(eventNode0))
        .nameNode(stringNode0, nodeNames.get(stringNode0))
        .addAssertions(assertion1, assertion2, assertion3, assertion4)
        .putConfidence(assertion1, 0.9)
        .putConfidence(assertion3, 0.0000000000000000000000000000000001)
        .build();

    final String expectedOutput = "dummy_runID\n"
        + ":Event_000000\ttype\tCONFLICT.ATTACK\t0.900000\n"
        + ":Event_000000\tmention.actual\t\"dummy mention\"\tdocID:5-12;docID:5-12\n"
        + ":String_000000\ttype\tSTRING\t0.000001\n"
        + ":String_000000\tnormalized_mention\t\"XXXX-XX-XX\"\tdocID:5-12";

    final File outputFile = File.createTempFile("kb-writer-test", ".tmp");
    writer.write(kb, new Random(0), Files.asCharSink(outputFile, Charsets.UTF_8));
    final String actualOutput = Joiner.on("\n").join(Files.readLines(outputFile, Charsets.UTF_8));
    outputFile.deleteOnExit();

    assertEquals(expectedOutput, actualOutput);
  }
}
