package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;

import com.google.common.base.Joiner;
import com.google.common.collect.HashBiMap;
import com.google.common.io.CharSink;

import org.immutables.value.Value;

import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
public class TacKbp2017KBWriter implements KnowledgeBaseWriter {

  public static TacKbp2017KBWriter create() {
    return ImmutableTacKbp2017KBWriter.builder().build();
  }

  @Override
  public void write(final KnowledgeBase kb, final CharSink sink) throws IOException {

    final Writer writer = sink.openBufferedStream();
    writer.write(kb.runId().asString() + "\n");

    final TacKbp2017KBWriting writing = new TacKbp2017KBWriting();
    for (final Assertion assertion : kb.assertions()) {
      final StringBuilder assertionOutputString = new StringBuilder();
      assertionOutputString.append(writing.assertionToString(assertion));
      // We don't want to write out confidences that are below 0.001 because that will write out
      // "0.000" after formatting and a confidence score cannot be 0.0.
      if (kb.confidence().containsKey(assertion) && kb.confidence().get(assertion) >= 0.001) {
        final double confidence = kb.confidence().get(assertion);
        assertionOutputString.append("\t").append(String.format(Locale.US, "%.3f", confidence));
      }
      assertionOutputString.append("\n");
      writer.write(assertionOutputString.toString());
    }
    writer.close();
  }

  static final class TacKbp2017KBWriting {

    private final Map<Node, String> idsForNodes = HashBiMap.create();
    private int currentStringNodeIdNumber = 0;
    private int currentEntityNodeIdNumber = 0;
    private int currentEventNodeIdNumber = 0;

    private static final Joiner TAB_JOINER = Joiner.on("\t");

    String assertionToString(final Assertion assertion) {
      if (assertion instanceof TypeAssertion) {
        return typeAssertionToString((TypeAssertion) assertion);
      } else if (assertion instanceof LinkAssertion) {
        return linkAssertionToString((LinkAssertion) assertion);
      }  else if (assertion instanceof ProvenancedAssertion) {
        return provenancedAssertionToString((ProvenancedAssertion) assertion);
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

    private String provenancedAssertionToString(final ProvenancedAssertion assertion) {
      if (assertion instanceof SentimentAssertion) {
        return sentimentAssertionToString((SentimentAssertion) assertion);
      } else if (assertion instanceof SFAssertion) {
        return sfAssertionToString((SFAssertion) assertion);
      } else if (assertion instanceof EventArgumentAssertion) {
        return eventArgumentAssertionToString((EventArgumentAssertion) assertion);
      } else if (assertion instanceof MentionAssertion) {
        return mentionAssertionToString((MentionAssertion) assertion);
      } else {
        throw new IllegalArgumentException(
            String.format("Do not recognize this type of assertion: %s. Found for assertion %s.",
                assertion.getClass(), assertion));
      }
    }

    private String provenancesToString(final Set<Provenance> provenances) {
      final StringBuilder provenancesString = new StringBuilder();
      boolean first = true;
      for (final Provenance provenance : provenances) {
        if (first) {
          first = false;
        } else {
          provenancesString.append(",");
        }
        provenancesString.append(provenanceToString(provenance));
      }
      return provenancesString.toString();
    }

    private String provenanceToString(final Provenance provenance) {
      final StringBuilder offsetsString = new StringBuilder();
      boolean first = true;
      for (final OffsetRange<CharOffset> offset : provenance.offsets()) {
        if (first) {
          first = false;
        } else {
          offsetsString.append(";");
        }
        offsetsString.append(offset.startInclusive().asInt());
        offsetsString.append("-");
        offsetsString.append(offset.endInclusive().asInt());
      }
      return provenance.documentId().asString() + ":" + offsetsString.toString();
    }

    private String sentimentAssertionToString(final SentimentAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          assertion.subjectEntityType() + ":" + assertion.sentiment().asString(),
          idOf(assertion.object()),
          provenancesToString(assertion.provenances()));
    }

    private String sfAssertionToString(final SFAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          assertion.subjectEntityType().asString() + ":" + assertion.relation().asString(),
          idOf(assertion.object().asNode()),
          provenancesToString(assertion.provenances()));
    }

    private String eventArgumentAssertionToString(final EventArgumentAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          assertion.eventType().asString() + ":" + assertion.role() + "." + assertion.realis(),
          idOf(assertion.argument().asNode()),
          provenancesToString(assertion.provenances()));
    }

    private String mentionAssertionToString(final MentionAssertion assertion) {
      if (assertion instanceof StringMentionAssertion) {
        return stringMentionAssertionToString((StringMentionAssertion) assertion);
      } else if (assertion instanceof EntityMentionAssertion) {
        return entityMentionAssertionToString((EntityMentionAssertion) assertion);
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
          provenancesToString(assertion.provenances()));
    }

    private String entityMentionAssertionToString(final EntityMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "mention",
          quotedString(assertion.mention()),
          provenancesToString(assertion.provenances()));
    }

    private String eventMentionAssertionToString(final EventMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "mention." + assertion.realis().asString(),
          quotedString(assertion.mention()),
          provenancesToString(assertion.provenances()));
    }

    private String stringCanonicalMentionAssertionToString(
        final StringCanonicalMentionAssertion assertion) {

      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "canonical_mention",
          quotedString(assertion.mention()),
          provenancesToString(assertion.provenances()));
    }

    private String entityCanonicalMentionAssertionToString(
        final EntityCanonicalMentionAssertion assertion) {

      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "canonical_mention",
          quotedString(assertion.mention()),
          provenancesToString(assertion.provenances()));
    }

    private String eventCanonicalMentionAssertionToString(
        final EventCanonicalMentionAssertion assertion) {

      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "canonical_mention." + assertion.realis().asString(),
          quotedString(assertion.mention()),
          provenancesToString(assertion.provenances()));
    }

    private String normalizedMentionAssertionToString(final NormalizedMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "normalized_mention",
          quotedString(assertion.mention()),
          provenancesToString(assertion.provenances()));
    }

    private String nominalMentionAssertionToString(final NominalMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "nominal_mention",
          quotedString(assertion.mention()),
          provenancesToString(assertion.provenances()));
    }

    private String pronominalMentionAssertionToString(final PronominalMentionAssertion assertion) {
      return TAB_JOINER.join(
          idOf(assertion.subject()),
          "pronominal_mention",
          quotedString(assertion.mention()),
          provenancesToString(assertion.provenances()));
    }

    String idOf(final Node node) {
      if (idsForNodes.containsKey(node)) {
        return idsForNodes.get(node);
      } else if (node instanceof StringNode) {
        idsForNodes.put(node, String.format(":String_%06d", currentStringNodeIdNumber));
        currentStringNodeIdNumber++;
        return idsForNodes.get(node);
      } else if (node instanceof EntityNode) {
        idsForNodes.put(node, String.format(":Entity_%06d", currentEntityNodeIdNumber));
        currentEntityNodeIdNumber++;
        return idsForNodes.get(node);
      } else if (node instanceof EventNode) {
        idsForNodes.put(node, String.format(":Event_%06d", currentEventNodeIdNumber));
        currentEventNodeIdNumber++;
        return idsForNodes.get(node);
      } else {
        throw new IllegalArgumentException(
            String.format("Do not recognize this type of node: %s. Found for node %s.",
                node.getClass(), node));
      }
    }

    private String quotedString(final String string) {
      return String.format("\"%s\"",
          string.replace("\n", " ").replace("\t", " ").replace("\\", "\\\\").replace("\"", "\\\""));
    }
  }
}
