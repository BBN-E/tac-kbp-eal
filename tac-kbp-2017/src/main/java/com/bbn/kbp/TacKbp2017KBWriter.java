package com.bbn.kbp;

import com.bbn.bue.common.ClassUtils;
import com.bbn.bue.common.OrderingUtils;
import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Ordering;
import com.google.common.io.CharSink;
import com.google.common.primitives.Longs;

import org.immutables.value.Value;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A class to write a TAC 2017 ColdStart++ knowledge-base to a file. The file must have one
 * assertion per line, and each column of that line must be separated by a tab character.
 * The first line of the file must give the run ID. Double quotes and backslashes must be escaped
 * with a preceding backslash. All tabs and newline characters within a quoted string should be
 * converted to a single space character and this should be the only way they differ from the
 * string extracted from the provenance span (See "Column 5" on page 13 of TAC KBP 2016 ColdStart
 * task description).
 */
@Value.Immutable
@TextGroupImmutable
public abstract class TacKbp2017KBWriter implements KnowledgeBaseWriter {
  public static TacKbp2017KBWriter create() {
    return ImmutableTacKbp2017KBWriter.builder().build();
  }


  private static final Ordering<Object> ASSERTION_TYPE_ORDERING =
      OrderingUtils.<Class<?>>explicitOrderingUnrankedLast(
          // these need to be the normally hidden immutable implementation classes
          // because those are the actual types of the assertions returned by classFunction
          ImmutableTypeAssertion.class,
          ImmutableEntityCanonicalMentionAssertion.class,
          ImmutableEventCanonicalMentionAssertion.class,
          ImmutableNonCanonicalEntityMentionAssertion.class,
          ImmutableLinkAssertion.class)
          .onResultOf(ClassUtils.classFunction());

  /**
   * If the KB does not specify the name for a node, we generate it randomly.
   * For determinism, we take the random number sequence to use for this as input.
   */
  @Override
  public void write(final KnowledgeBase kb, final Random random, final CharSink sink)
      throws IOException {

    // we order the assertions first by subject (for easy of reading) then by assertion type
    // because the validator requires that e.g. type assertions precede mention assertions
    final Ordering<Node> arbitraryOrderOfNodes = Ordering.explicit(kb.nodes().asList());
    final Ordering<Assertion> orderBySubject =
        arbitraryOrderOfNodes.onResultOf(Assertion.SubjectFunction.INSTANCE);
    final Ordering<Assertion> orderBySubjectThenAssertionType =
        orderBySubject.compound(ASSERTION_TYPE_ORDERING);

    try (final Writer writer = sink.openBufferedStream()) {
      writer.write(kb.runId().asString() + "\n");

      final TacKbp2017KBWriting writing = new TacKbp2017KBWriting(kb.nodesToNames(), random);
      for (final Assertion assertion : orderBySubjectThenAssertionType
          .immutableSortedCopy(kb.assertions())) {
        final StringBuilder assertionOutputString = new StringBuilder();
        assertionOutputString.append(writing.assertionToString(Optional.of(kb), assertion));
        if (kb.confidence().containsKey(assertion)) {
          final double confidence = kb.confidence().get(assertion);
          if (confidence >= 0.000001) {
            assertionOutputString.append("\t").append(String.format(Locale.US, "%.6f", confidence));
          } else {
            assertionOutputString.append("\t").append(String.format(Locale.US, "%.6f", 0.000001));
          }
        }
        assertionOutputString.append("\n");
        writer.write(assertionOutputString.toString());
      }
    }
  }

  static final class TacKbp2017KBWriting {

    private final Random rng;

    private final Map<Node, String> idsForNodes;

    private static final Joiner TAB_JOINER = Joiner.on("\t");

    TacKbp2017KBWriting(
        // these are names specified in the KB we wish to preserve. Any entities without
        // names in this map will received random IDs.  Passing in this map lets us preserve
        // IDs from the input, which can make debugging much easier
        final Map<Node, String> namesToPreserveForKb,
        final Random random) {
      this.rng = checkNotNull(random);
      this.idsForNodes = HashBiMap.create();
      this.idsForNodes.putAll(namesToPreserveForKb);
    }

    /**
     * For unit tests only.
     */
    @Deprecated
    String assertionToString(final Assertion assertion) {
      return assertionToString(Optional.<KnowledgeBase>absent(), assertion);
    }

    String assertionToString(final Optional<KnowledgeBase> kb, final Assertion assertion) {
      if (assertion instanceof TypeAssertion) {
        return typeAssertionToString((TypeAssertion) assertion);
      } else if (assertion instanceof LinkAssertion) {
        return linkAssertionToString((LinkAssertion) assertion);
      } else if (assertion instanceof SentimentAssertion) {
        return sentimentAssertionToString((SentimentAssertion) assertion);
      } else if (assertion instanceof SFAssertion) {
        return sfAssertionToString((SFAssertion) assertion);
      } else if (assertion instanceof EventArgumentAssertion) {
        return eventArgumentAssertionToString((EventArgumentAssertion) assertion);
      } else if (assertion instanceof EntityInverseEventArgumentAssertion) {
        checkState(kb.isPresent());
        return inverseEntityEventArgumentAssertionToString(kb.get(),
            (EntityInverseEventArgumentAssertion) assertion);
      } else if (assertion instanceof MentionAssertion) {
        return mentionAssertionToString((MentionAssertion) assertion);
      } else if (assertion instanceof RelationAssertion) {
        return relationAssertionToString((RelationAssertion) assertion);
      } else {
        throw new IllegalArgumentException(
            String.format("Do not recognize this type of assertion: %s. Found for assertion %s.",
                assertion.getClass(), assertion));
      }
    }


    private String typeAssertionToString(final TypeAssertion assertion) {
      return TAB_JOINER.join(idOf(assertion.subject()), "type", assertion.type().asString());
    }

    private String linkAssertionToString(final LinkAssertion assertion) {
      final String object =
          assertion.externalKB().asString() + ":" + assertion.externalNodeID().asString();
      return TAB_JOINER.join(idOf(assertion.subject()), "link", quotedString(object));
    }

    private String spanToString(final JustificationSpan span) {
      return span.documentId() + ":"
          + span.offsets().startInclusive().asInt()
          + "-"
          + span.offsets().endInclusive().asInt();
    }

    private String spansToString(Iterable<JustificationSpan> spans) {
      final List<String> parts = new ArrayList<>();

      for (final JustificationSpan span : spans) {
        parts.add(spanToString(span));
      }

      return StringUtils.commaJoiner().join(parts);
    }

    private String sfProvenancesToString(final SFAssertion assertion) {
      final List<String> parts = new ArrayList<>();

      if (assertion.fillerString().isPresent()) {
        parts.add(spanToString(assertion.fillerString().get()));
      }
      parts.add(spansToString(assertion.predicateJustification()));

      return Joiner.on(";").join(parts);
    }

    private String relationProvenancesToString(final RelationAssertion assertion) {
      final List<String> parts = new ArrayList<>();

      if (assertion.fillerString().isPresent()) {
        parts.add(spanToString(assertion.fillerString().get()));
      }
      parts.add(spansToString(assertion.predicateJustification()));

      return Joiner.on(";").join(parts);
    }

    private String sentimentAssertionToString(final SentimentAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          assertion.subjectEntityType() + ":" + assertion.sentiment().asString(),
          idOf(assertion.object()),
          spanToString(assertion.predicateJustification()));
    }

    private String sfAssertionToString(final SFAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          assertion.subjectEntityType().asString() + ":" + assertion.relation().asString(),
          idOf(assertion.object().asNode()),
          sfProvenancesToString(assertion));
    }


    private String eventArgumentAssertionToString(final EventArgumentAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          assertion.eventType().asString() + ":" + assertion.role() + "." + assertion.realis(),
          idOf(assertion.argument().asNode()),
          eventProvenancesToString(assertion));
    }

    private String inverseEntityEventArgumentAssertionToString(
        final KnowledgeBase kb, final EntityInverseEventArgumentAssertion assertion) {
      final Optional<Symbol> typeForNode = kb.typeForNode(assertion.subject());
      checkState(typeForNode.isPresent(), "%s is missing a node type",
          kb.nameForNode(assertion.subject()).or("unnamed node"));
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          typeForNode.get().asString().toLowerCase(Locale.ENGLISH) + ":" +
              assertion.eventType().asString() + "_" + assertion.role() + "." + assertion.realis(),
          idOf(assertion.eventNode()),
          inverseEventProvenancesToString(assertion));
    }

    private String eventProvenancesToString(final EventArgumentAssertion assertion) {
      final List<String> parts = new ArrayList<>();

      if (assertion.fillerString().isPresent()) {
        parts.add(spanToString(assertion.fillerString().get()));
      }
      parts.add(spansToString(assertion.predicateJustification()));
      parts.add(spanToString(assertion.baseFiller()));
      if (!assertion.additionalJustifications().isEmpty()) {
        parts.add(spansToString(assertion.additionalJustifications()));
      }

      return Joiner.on(";").join(parts);
    }

    private String inverseEventProvenancesToString(
        final EntityInverseEventArgumentAssertion assertion) {
      final List<String> parts = new ArrayList<>();

      parts.add(spansToString(assertion.predicateJustification()));
      parts.add(spanToString(assertion.baseFiller()));
      parts.add(spansToString(assertion.predicateJustification()));

      return Joiner.on(";").join(parts);
    }

    private String relationAssertionToString(final RelationAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          assertion.relationType(),
          idOf(assertion.object().asNode()),
          relationProvenancesToString(assertion));
    }

    private String mentionAssertionToString(final MentionAssertion assertion) {
      if (assertion instanceof StringMentionAssertion) {
        return stringMentionAssertionToString((StringMentionAssertion) assertion);
      } else if (assertion instanceof NonCanonicalEntityMentionAssertion) {
        return entityMentionAssertionToString((NonCanonicalEntityMentionAssertion) assertion);
      } else if (assertion instanceof EventMentionAssertion) {
        return eventMentionAssertionToString((EventMentionAssertion) assertion);
      } else if (assertion instanceof StringCanonicalMentionAssertion) {
        return stringCanonicalMentionAssertionToString((StringCanonicalMentionAssertion) assertion);
      } else if (assertion instanceof EntityCanonicalMentionAssertion) {
        return entityCanonicalMentionAssertionToString((EntityCanonicalMentionAssertion) assertion);
      } else if (assertion instanceof EventCanonicalMentionAssertion) {
        return eventCanonicalMentionAssertionToString((EventCanonicalMentionAssertion) assertion);
      } else if (assertion instanceof NormalizedMentionAssertion) {
        return normalizedMentionAssertionToString((NormalizedMentionAssertion) assertion);
      } else if (assertion instanceof NominalMentionAssertion) {
        return nominalMentionAssertionToString((NominalMentionAssertion) assertion);
      } else if (assertion instanceof PronominalMentionAssertion) {
        return pronominalMentionAssertionToString((PronominalMentionAssertion) assertion);
      } else {
        throw new IllegalArgumentException(
            String.format("Do not recognize this type of assertion: %s. Found for assertion %s.",
                assertion.getClass(), assertion));
      }
    }

    private String stringMentionAssertionToString(final StringMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "mention",
          quotedString(assertion.mention()),
          spanToString(assertion.predicateJustification()));
    }

    private String entityMentionAssertionToString(
        final NonCanonicalEntityMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "mention",
          quotedString(assertion.mention()),
          spanToString(assertion.predicateJustification()));
    }

    private String eventMentionAssertionToString(final EventMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "mention." + assertion.realis().asString(),
          quotedString(assertion.mention()),
          spanToString(assertion.predicateJustification()));
    }

    private String stringCanonicalMentionAssertionToString(
        final StringCanonicalMentionAssertion assertion) {

      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "canonical_mention",
          quotedString(assertion.mention()),
          spanToString(assertion.predicateJustification()));
    }

    private String entityCanonicalMentionAssertionToString(
        final EntityCanonicalMentionAssertion assertion) {

      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "canonical_mention",
          quotedString(assertion.mention()),
          spanToString(assertion.predicateJustification()));
    }

    private String eventCanonicalMentionAssertionToString(
        final EventCanonicalMentionAssertion assertion) {

      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "canonical_mention." + assertion.realis().asString(),
          quotedString(assertion.mention()),
          spanToString(assertion.predicateJustification()));
    }

    private String normalizedMentionAssertionToString(final NormalizedMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "normalized_mention",
          quotedString(assertion.mention()),
          spanToString(assertion.predicateJustification()));
    }

    private String nominalMentionAssertionToString(final NominalMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "nominal_mention",
          quotedString(assertion.mention()),
          spanToString(assertion.predicateJustification()));
    }

    private String pronominalMentionAssertionToString(final PronominalMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "pronominal_mention",
          quotedString(assertion.mention()),
          spanToString(assertion.predicateJustification()));
    }

    String idOf(final Node node) {
      if (!idsForNodes.containsKey(node)) {
        final String baseId = uuidFromRandom(rng).toString().replaceAll("-", "_");
        final String idWithType;
        if (node instanceof StringNode) {
          idWithType = String.format(":String_%s", baseId);
        } else if (node instanceof EntityNode) {
          idWithType = String.format(":Entity_%s", baseId);
        } else if (node instanceof EventNode) {
          idWithType = String.format(":Event_%s", baseId);
        } else {
          throw new IllegalArgumentException(
              String.format("Do not recognize this type of node: %s. Found for node %s.",
                  node.getClass(), node));
        }
        idsForNodes.put(node, idWithType);
      }
      return idsForNodes.get(node);
    }

    private String quotedString(final String string) {
      return String.format("\"%s\"",
          string.replace("\n", " ").replace("\t", " ").replace("\\", "\\\\").replace("\"", "\\\""));
    }

    @MoveToBUECommon
    private static UUID uuidFromRandom(Random rng) {
      final byte[] lowBits = Longs.toByteArray(rng.nextLong());
      final byte[] highBits = Longs.toByteArray(rng.nextLong());
      final byte[] allBits = new byte[16];
      System.arraycopy(lowBits, 0, allBits, 0, 8);
      System.arraycopy(highBits, 0, allBits, 8, 8);
      return UUID.nameUUIDFromBytes(allBits);
    }
  }
}
