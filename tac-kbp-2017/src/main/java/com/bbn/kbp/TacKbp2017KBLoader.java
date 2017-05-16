package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;

import org.immutables.value.Value;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class to load a TAC 2017 ColdStart++ knowledge-base from a file. The file must have one
 * assertion per line, and each column of that line must be separated by a tab character.
 * The first line of the file must give the run ID. Blank lines (lines that contain only white
 * spaces or nothing) are ignored. Double quotes and backslashes must be escaped with a preceding
 * backslash. Comments begin with "#" and continue to the end of the line (we are assuming that a
 * "#" within a quoted string is not considered the start of a comment). All tabs and newline
 * characters within a quoted string should be converted to a single space character and this
 * should be the only way they differ from the string extracted from the provenance span (See
 * "Column 5" on page 13 of TAC KBP 2016 ColdStart task description).
 */
@TextGroupImmutable
@Value.Immutable
public abstract class TacKbp2017KBLoader implements KnowledgeBaseLoader {

  private static final Pattern EMPTY_LINE_PATTERN = Pattern.compile("\\s*");

  public static TacKbp2017KBLoader create() {
    return ImmutableTacKbp2017KBLoader.builder().build();
  }

  @Override
  public KnowledgeBase load(final CharSource input) throws IOException {
    final KnowledgeBase.Builder kb = KnowledgeBase.builder();
    final BufferedReader reader = input.openBufferedStream();
    String currentLine = reader.readLine();
    kb.runId(Symbol.from(currentLine));

    final TacKbp2017KBLoading loading = new TacKbp2017KBLoading();
    while ((currentLine = reader.readLine()) != null) {
      if (!EMPTY_LINE_PATTERN.matcher(currentLine).matches()) {
        final AssertionConfidencePair pair = loading.parse(currentLine);
        kb.addAssertions(pair.assertion()).addAllNodes(pair.assertion().allNodes());
        if (pair.confidence().isPresent()) {
          kb.putConfidence(pair.assertion(), pair.confidence().get());
        }
      }
    }
    return kb.build();
  }

  static final class TacKbp2017KBLoading {

    private final Map<String, Node> nodesForIds = HashBiMap.create();

    private static final Pattern DOUBLE_PATTERN = Pattern.compile("\\d+\\.\\d+");
    private static final Pattern SENTIMENT_PATTERN =
        Pattern.compile("likes|dislikes|is_liked_by|is_disliked_by");
    private static final Pattern MENTION_PATTERN =
        Pattern.compile("(?:nominal_|pronominal_|canonical_|normalized_)?mention(?:\\..+)?");
    private static final Pattern PROVENANCE_PATTERN =
        Pattern.compile("(<docID>.+?):(<offsets>\\d+-\\d+(?:;\\d+-\\d+)?)");
    private static final Pattern EVENT_ARG_PREDICATE_PATTERN =
        Pattern.compile("(<event_type>.+?):(<role>.+?)\\.(<realis>.+)");

    private static final Splitter TAB_SPLITTER = Splitter.on("\t");
    private static final Splitter COLON_SPLITTER = Splitter.on(":");
    private static final Splitter COMMA_SPLITTER = Splitter.on(",");
    private static final Splitter SEMICOLON_SPLITTER = Splitter.on(";");
    private static final Splitter HYPHEN_SPLITTER = Splitter.on("-");
    private static final Splitter DOT_SPLITTER = Splitter.on(".");

    AssertionConfidencePair parse(final String line) {
      final List<String> columns = TAB_SPLITTER.splitToList(line);
      if (columns.size() == 3) {
        return toAssertionConfidencePair(columns.get(0), columns.get(1), columns.get(2));
      } else if (columns.size() == 4) {
        return toAssertionConfidencePair(
            columns.get(0), columns.get(1), columns.get(2), columns.get(3));
      } else if (columns.size() == 5) {
        return toAssertionConfidencePair(
            columns.get(0), columns.get(1), columns.get(2), columns.get(3), columns.get(4));
      } else {
        throw new IllegalArgumentException(
            String.format("\"%s\" is not a valid assertion line.", line));
      }
    }

    private AssertionConfidencePair toAssertionConfidencePair(final String subject,
        final String predicate, final String object) {
      return AssertionConfidencePair.of(toAssertion(subject, predicate, object));
    }

    private AssertionConfidencePair toAssertionConfidencePair(final String subject,
        final String predicate, final String object, final String confidenceOrProvenances) {

      if (DOUBLE_PATTERN.matcher(confidenceOrProvenances).matches()) {
        final Assertion assertion = toAssertion(subject, predicate, object);
        final double confidence = Double.parseDouble(confidenceOrProvenances);
        return AssertionConfidencePair.of(assertion, confidence);
      } else {
        final Assertion assertion = toAssertion(subject, predicate, object, confidenceOrProvenances);
        return AssertionConfidencePair.of(assertion);
      }
    }

    private AssertionConfidencePair toAssertionConfidencePair(final String subject,
        final String predicate, final String object, final String provenances,
        final String confidence) {

      final Assertion assertion = toAssertion(subject, predicate, object, provenances);
      return AssertionConfidencePair.of(assertion, Double.parseDouble(confidence));
    }

    private Assertion toAssertion(final String subject, final String predicate,
        final String object) {
      if (predicate.equals("type")) {
        return TypeAssertion.of(nodeFor(subject), Symbol.from(object));
      } else if (predicate.equals("link")) {
        final List<String> externalKBPair = COLON_SPLITTER.splitToList(dequotedString(object));
        if (externalKBPair.size() != 2) {
          throw new IllegalArgumentException(
              String.format("%s is not in a valid external KB link for assertion: \"%s\t%s\t%s\".",
                  object, subject, predicate, object));
        }
        return LinkAssertion.of((EntityNode) nodeFor(subject),
            Symbol.from(externalKBPair.get(0)),
            Symbol.from(externalKBPair.get(1)));
      } else {
        throw new IllegalArgumentException(
            String.format("Do not recognize the unprovenanced predicate: %s.", predicate));
      }
    }

    private Assertion toAssertion(final String subject, final String predicate,
        final String object, final String provenances) {
      if (SENTIMENT_PATTERN.matcher(predicate).matches()) {
        return SentimentAssertion.builder()
            .subject((EntityNode) nodeFor(subject))
            .sentiment(Symbol.from(predicate))
            .object((EntityNode) nodeFor(object))
            .provenances(toProvenances(provenances))
            .build();
      } else if (MENTION_PATTERN.matcher(predicate).matches()) {
        return toMentionAssertion(subject, predicate, object, provenances);
      } else if (nodeFor(subject) instanceof EntityNode) {
        return toSFAssertion(subject, predicate, object, provenances);
      } else if (nodeFor(subject) instanceof EventNode) {
        return toEventArgumentAssertion(subject, predicate, object, provenances);
      } else {
        throw new IllegalArgumentException(
            String.format("Could not parse assertion: \"%s\t%s\t%s\t%s\".",
                subject, predicate, object, provenances));
      }
    }

    private SFAssertion toSFAssertion(final String subject, final String predicate,
        final String object, final String provenances) {
      final List<String> entityTypeRelationPair = COLON_SPLITTER.splitToList(predicate);
      if (entityTypeRelationPair.size() != 2) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid SF predicate for assertion: \"%s\t%s\t%s\t%s\"",
                predicate, subject, predicate, object, provenances));
      }
      if (nodeFor(object) instanceof EntityNode) {
        return SFAssertion.builder()
            .subject((EntityNode) nodeFor(subject))
            .object((EntityNode) nodeFor(object))
            .subjectEntityType(Symbol.from(entityTypeRelationPair.get(0)))
            .relation(Symbol.from(entityTypeRelationPair.get(1)))
            .build();
      } else if (nodeFor(object) instanceof StringNode) {
        return SFAssertion.builder()
            .subject((EntityNode) nodeFor(subject))
            .object((StringNode) nodeFor(object))
            .subjectEntityType(Symbol.from(entityTypeRelationPair.get(0)))
            .relation(Symbol.from(entityTypeRelationPair.get(1)))
            .build();
      } else {
        throw new IllegalArgumentException(
            String.format("%s is not a valid object for SF assertion: \"%s\t%s\t%s\t%s\"",
                object, subject, predicate, object, provenances));
      }
    }

    /**
     * Note: This does not handle inverse event argument assertions.
     */
    private EventArgumentAssertion toEventArgumentAssertion(final String subject, final String predicate,
        final String object, final String provenances) {
      final Matcher matcher = EVENT_ARG_PREDICATE_PATTERN.matcher(predicate);
      if (!matcher.matches()) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid SF predicate for assertion: \"%s\t%s\t%s\t%s\"",
                predicate, subject, predicate, object, provenances));
      } else {
        if (nodeFor(object) instanceof EntityNode) {
          return EventArgumentAssertion.builder()
              .subject((EventNode) nodeFor(subject))
              .argument((EntityNode) nodeFor(object))
              .eventType(Symbol.from(matcher.group("event_type")))
              .role(Symbol.from(matcher.group("role")))
              .realis(Symbol.from(matcher.group("realis")))
              .build();
        } else if (nodeFor(object) instanceof StringNode) {
          return EventArgumentAssertion.builder()
              .subject((EventNode) nodeFor(subject))
              .argument((StringNode) nodeFor(object))
              .eventType(Symbol.from(matcher.group("event_type")))
              .role(Symbol.from(matcher.group("role")))
              .realis(Symbol.from(matcher.group("realis")))
              .build();
        } else {
          throw new IllegalArgumentException(
              String.format("%s is not a valid object for SF assertion: \"%s\t%s\t%s\t%s\"",
                  object, subject, predicate, object, provenances));
        }
      }
    }

    private Set<Provenance> toProvenances(final String string) {
      final List<String> provenanceStrings = COMMA_SPLITTER.splitToList(string);

      final ImmutableSet.Builder<Provenance> provenances = ImmutableSet.builder();
      for (final String provenanceString : provenanceStrings) {
        final Matcher matcher = PROVENANCE_PATTERN.matcher(provenanceString);
        if (matcher.matches()) {
          final Symbol docId = Symbol.from(matcher.group("docID"));
          final Set<OffsetRange<CharOffset>> offsets = toOffsets(matcher.group("offsets"));
          provenances.add(Provenance.of(docId, offsets));
        } else {
          throw new IllegalArgumentException(
              String.format("The following provenance could not be parsed: %s.", string));
        }
      }
      return provenances.build();
    }

    private MentionAssertion toMentionAssertion(final String subject, final String predicate,
        final String object, final String provenances) {
      if (predicate.startsWith("mention")) {
        if (nodeFor(subject) instanceof EntityNode) {
          return toEntityMentionAssertion(subject, predicate, object, provenances);
        } else if (nodeFor(subject) instanceof EventNode) {
          return toEventMentionAssertion(subject, predicate, object, provenances);
        } else { // if (nodeFor(subject) instanceof StringNode)
          return toStringMentionAssertion(subject, predicate, object, provenances);
        }
      } else if (predicate.startsWith("nominal_mention")) {
        return toNominalMentionAssertion(subject, predicate, object, provenances);
      } else if (predicate.startsWith("pronominal_mention")) {
        return toPronominalMentionAssertion(subject, predicate, object, provenances);
      } else if (predicate.startsWith("canonical_mention")) {
        if (nodeFor(subject) instanceof EntityNode) {
          return toEntityCanonicalMentionAssertion(subject, predicate, object, provenances);
        } else if (nodeFor(subject) instanceof EventNode) {
          return toEventCanonicalMentionAssertion(subject, predicate, object, provenances);
        } else { // if (nodeFor(subject) instanceof StringNode)
          return toStringCanonicalMentionAssertion(subject, predicate, object, provenances);
        }      } else if (predicate.startsWith("normalized_mention")) {
        return toNormalizedMentionAssertion(subject, predicate, object, provenances);
      } else {
        throw new IllegalArgumentException(
            String.format("The following mention assertion could not be parsed: \"%s\t%s\t%s\t%s\".",
                subject, predicate, object, provenances));
      }
    }

    private EntityMentionAssertion toEntityMentionAssertion(final String subject,
        final String predicate, final String object, final String provenances) {
      return EntityMentionAssertion.of(
          (EntityNode) nodeFor(subject), dequotedString(object), toProvenances(provenances));
    }

    private EventMentionAssertion toEventMentionAssertion(final String subject,
        final String predicate, final String object, final String provenances) {
      final List<String> mentionTypeRealisPair = DOT_SPLITTER.splitToList(predicate);
      if (mentionTypeRealisPair.size() != 2) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid mention predicate for assertion: \"%s\t%s\t%s\t%s\"",
                predicate, subject, predicate, object, provenances));
      }
      final Symbol realis = Symbol.from(mentionTypeRealisPair.get(1));
      return EventMentionAssertion.of(
          (EventNode) nodeFor(subject), dequotedString(object), realis, toProvenances(provenances));
    }

    private StringMentionAssertion toStringMentionAssertion(final String subject,
        final String predicate, final String object, final String provenances) {
      return StringMentionAssertion.of(
          (StringNode) nodeFor(subject), dequotedString(object), toProvenances(provenances));
    }

    private EntityCanonicalMentionAssertion toEntityCanonicalMentionAssertion(final String subject,
        final String predicate, final String object, final String provenances) {
      return EntityCanonicalMentionAssertion.of(
          (EntityNode) nodeFor(subject), dequotedString(object), toProvenances(provenances));
    }

    private EventCanonicalMentionAssertion toEventCanonicalMentionAssertion(final String subject,
        final String predicate, final String object, final String provenances) {
      final List<String> mentionTypeRealisPair = DOT_SPLITTER.splitToList(predicate);
      if (mentionTypeRealisPair.size() != 2) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid mention predicate for assertion: \"%s\t%s\t%s\t%s\"",
                predicate, subject, predicate, object, provenances));
      }
      final Symbol realis = Symbol.from(mentionTypeRealisPair.get(1));
      return EventCanonicalMentionAssertion.of(
          (EventNode) nodeFor(subject), dequotedString(object), realis, toProvenances(provenances));
    }

    private StringCanonicalMentionAssertion toStringCanonicalMentionAssertion(final String subject,
        final String predicate, final String object, final String provenances) {
      return StringCanonicalMentionAssertion.of(
          (StringNode) nodeFor(subject), dequotedString(object), toProvenances(provenances));
    }

    private NominalMentionAssertion toNominalMentionAssertion(final String subject,
        final String predicate, final String object, final String provenances) {
      return NominalMentionAssertion.of(
          (EntityNode) nodeFor(subject), dequotedString(object), toProvenances(provenances));
    }

    private PronominalMentionAssertion toPronominalMentionAssertion(final String subject,
        final String predicate, final String object, final String provenances) {
      return PronominalMentionAssertion.of(
          (EntityNode) nodeFor(subject), dequotedString(object), toProvenances(provenances));
    }

    private NormalizedMentionAssertion toNormalizedMentionAssertion(final String subject,
        final String predicate, final String object, final String provenances) {
      return NormalizedMentionAssertion.of(
          (StringNode) nodeFor(subject), dequotedString(object), toProvenances(provenances));
    }

    private Set<OffsetRange<CharOffset>> toOffsets(final String string) {
      final List<String> offsetStrings = SEMICOLON_SPLITTER.splitToList(string);

      final ImmutableSet.Builder<OffsetRange<CharOffset>> offsets = ImmutableSet.builder();
      for (final String offsetString : offsetStrings) {
        final List<String> range = HYPHEN_SPLITTER.splitToList(offsetString);
        final OffsetRange<CharOffset> offset = OffsetRange
            .charOffsetRange(Integer.parseInt(range.get(0)), Integer.parseInt(range.get(1)));
        offsets.add(offset);
      }
      return offsets.build();
    }

    Node nodeFor(final String nodeId) {
      if (nodesForIds.containsKey(nodeId)) {
        return nodesForIds.get(nodeId);
      } else {
        final Node node;
        if (nodeId.startsWith(":Event")) {
          node = EventNode.of();
        } else if (nodeId.startsWith(":Entity")) {
          node = EntityNode.of();
        } else if (nodeId.startsWith(":String")) {
          node = StringNode.of();
        } else {
          throw new IllegalArgumentException(
              String.format("\"%s\" is not a valid node ID.", nodeId));
        }
        nodesForIds.put(nodeId, node);
        return node;
      }
    }

    private String dequotedString(final String string) {
      return string.replaceAll("^\"|\"$", "").replace("\\\"", "\"").replace("\\\\", "\\");
    }
  }

  @TextGroupImmutable
  @Value.Immutable
  static abstract class AssertionConfidencePair {

    abstract Assertion assertion();

    abstract Optional<Double> confidence();

    static AssertionConfidencePair of(final Assertion assertion) {
      return ImmutableAssertionConfidencePair.builder().assertion(assertion).build();
    }

    static AssertionConfidencePair of(final Assertion assertion, final double confidence) {
      return ImmutableAssertionConfidencePair.builder()
          .assertion(assertion)
          .confidence(confidence)
          .build();
    }
  }
}
