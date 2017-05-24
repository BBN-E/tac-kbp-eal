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

  private static final Pattern EMPTY_OR_COMMENT_PATTERN = Pattern.compile("\\s*(#.*)?");

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
      if (!EMPTY_OR_COMMENT_PATTERN.matcher(currentLine).matches()) {
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

    private static final Splitter COMMA_SPLITTER = Splitter.on(",");
    private static final Splitter SEMICOLON_SPLITTER = Splitter.on(";");

    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern OFFSET_PATTERN = Pattern.compile("(?<start>\\d+)-(?<end>\\d+)");
    private static final Pattern OFFSETS_PATTERN = Pattern.compile("\\d+-\\d+(?:;\\d+-\\d+)*");
    private static final Pattern PROVENANCE_PATTERN = Pattern.compile(
        "(?<docID>.+?):(?<offsets>" + OFFSETS_PATTERN.pattern() + ")");
    private static final Pattern PROVENANCES_PATTERN = Pattern.compile(
        ".+?:" + OFFSETS_PATTERN.pattern() + "(?:,.+?:" + OFFSETS_PATTERN.pattern() + ")*");

    private static final Pattern TYPE_ASSERTION_PATTERN = Pattern.compile(
        "(?<subject>(?::Event|:Entity|:String).+?)"
            + "\\ttype"
            + "\\t(?<type>.+?)"
            + "(\\t(?<confidence>" + CONFIDENCE_PATTERN.pattern() + "))?"
            + EMPTY_OR_COMMENT_PATTERN.pattern());
    private static final Pattern LINK_ASSERTION_PATTERN = Pattern.compile(
        "(?<subject>:Entity.+?)"
            + "\\tlink"
            + "\\t\"(?<externalKB>.+?):(?<externalNodeID>.+?)\""
            + "(\\t(?<confidence>" + CONFIDENCE_PATTERN.pattern() + "))?"
            + EMPTY_OR_COMMENT_PATTERN.pattern());
    private static final Pattern SENTIMENT_ASSERTION_PATTERN = Pattern.compile(
        "(?<subject>:Entity.+?)"
            + "\\t(?<subjectEntityType>.+?):(?<sentiment>likes|dislikes|is_liked_by|is_disliked_by)"
            + "\\t(?<object>:Entity.+?)"
            + "\\t(?<provenances>" + PROVENANCES_PATTERN.pattern() + ")"
            + "(\\t(?<confidence>" + CONFIDENCE_PATTERN.pattern() + "))?"
            + EMPTY_OR_COMMENT_PATTERN.pattern());
    private static final Pattern SF_ASSERTION_PATTERN = Pattern.compile(
        "(?<subject>:Entity.+?)"
            + "\\t(?<subjectEntityType>.+?):(?<relation>.+?)"
            + "\\t(?<object>(?::Entity|:String).+?)"
            + "\\t(?<provenances>" + PROVENANCES_PATTERN.pattern() + ")"
            + "(\\t(?<confidence>" + CONFIDENCE_PATTERN.pattern() + "))?"
            + EMPTY_OR_COMMENT_PATTERN.pattern());
    private static final Pattern EVENT_ARGUMENT_ASSERTION_PATTERN = Pattern.compile(
        "(?<subject>:Event.+?)"
            + "\\t(?<eventType>.+?):(?<role>.+?)\\.(?<realis>.+?)"
            + "\\t(?<argument>(?::Entity|:String).+?)"
            + "\\t(?<provenances>" + PROVENANCES_PATTERN.pattern() + ")"
            + "(\\t(?<confidence>" + CONFIDENCE_PATTERN.pattern() + "))?"
            + EMPTY_OR_COMMENT_PATTERN.pattern());
    private static final Pattern INVERSE_EVENT_ARGUMENT_ASSERTION_PATTERN = Pattern.compile(
        "(?<subject>:Entity.+?)"
            + "\\t(?<subjectEntityType>.+?):(?<eventType>.+?)_(?<role>.+?)\\.(?<realis>.+?)"
            + "\\t(?<event>(?::Event).+?)"
            + "\\t(?<provenances>" + PROVENANCES_PATTERN.pattern() + ")"
            + "(\\t(?<confidence>" + CONFIDENCE_PATTERN.pattern() + "))?"
            + EMPTY_OR_COMMENT_PATTERN.pattern());
    private static final Pattern MENTION_ASSERTION_PATTERN = Pattern.compile(
        "(?<subject>(?::Event|:Entity|:String).+?)"
            + "\\t(?<mentionType>mention|canonical_mention|nominal_mention|pronominal_mention"
                + "|normalized_mention)(\\.(?<realis>.+?))?"
            + "\\t\"(?<mention>.+?)\""
            + "\\t(?<provenances>" + PROVENANCES_PATTERN.pattern() + ")"
            + "(\\t(?<confidence>" + CONFIDENCE_PATTERN.pattern() + "))?"
            + EMPTY_OR_COMMENT_PATTERN.pattern());

    AssertionConfidencePair parse(final String line) {
      final Matcher matcher;
      final Assertion assertion;

      final Matcher typeAssertionMatcher = TYPE_ASSERTION_PATTERN.matcher(line);
      final Matcher linkAssertionMatcher = LINK_ASSERTION_PATTERN.matcher(line);
      final Matcher sentimentAssertionMatcher = SENTIMENT_ASSERTION_PATTERN.matcher(line);
      final Matcher sfAssertionMatcher = SF_ASSERTION_PATTERN.matcher(line);
      final Matcher eventArgAssertionMatcher = EVENT_ARGUMENT_ASSERTION_PATTERN.matcher(line);
      final Matcher inverseEventArgAssertionMatcher = INVERSE_EVENT_ARGUMENT_ASSERTION_PATTERN.matcher(line);
      final Matcher mentionAssertionMatcher = MENTION_ASSERTION_PATTERN.matcher(line);

      if (typeAssertionMatcher.matches()) {
        matcher = typeAssertionMatcher;
        assertion = toTypeAssertion(matcher);
      } else if (linkAssertionMatcher.matches()) {
        matcher = linkAssertionMatcher;
        assertion = toLinkAssertion(matcher);
      } else if (sentimentAssertionMatcher.matches()) {
        matcher = sentimentAssertionMatcher;
        assertion = toSentimentAssertion(matcher);
      } else if (sfAssertionMatcher.matches()) {
        matcher = sfAssertionMatcher;
        assertion = toSFAssertion(matcher);
      } else if (eventArgAssertionMatcher.matches()) {
        matcher = eventArgAssertionMatcher;
        assertion = toEventArgumentAssertion(matcher);
      } else if (inverseEventArgAssertionMatcher.matches()) {
        matcher = inverseEventArgAssertionMatcher;
        assertion = toInverseEventArgumentAssertion(matcher);
      } else if (mentionAssertionMatcher.matches()) {
        matcher = mentionAssertionMatcher;
        assertion = toMentionAssertion(matcher);
      } else {
        throw new IllegalArgumentException(
            String.format("\"%s\" is not a valid assertion line.", line));
      }
      return matcher.group("confidence") == null ? AssertionConfidencePair.of(assertion) :
        AssertionConfidencePair.of(assertion, Double.parseDouble(matcher.group("confidence")));
    }

    private TypeAssertion toTypeAssertion(final Matcher matcher) {
      final Node subjectNode = nodeFor(matcher.group("subject"));
      final Symbol type = Symbol.from(matcher.group("type"));
      return TypeAssertion.of(subjectNode, type);
    }

    private LinkAssertion toLinkAssertion(final Matcher matcher) {
      final EntityNode subjectNode = (EntityNode) nodeFor(matcher.group("subject"));
      final Symbol externalKB = Symbol.from(matcher.group("externalKB"));
      final Symbol externalNodeId = Symbol.from(matcher.group("externalNodeID"));
      return LinkAssertion.of(subjectNode, externalKB, externalNodeId);
    }

    private SentimentAssertion toSentimentAssertion(final Matcher matcher) {
      return SentimentAssertion.builder()
          .subject((EntityNode) nodeFor(matcher.group("subject")))
          .object((EntityNode) nodeFor(matcher.group("object")))
          .subjectEntityType(Symbol.from(matcher.group("subjectEntityType")))
          .sentiment(Symbol.from(matcher.group("sentiment")))
          .provenances(toProvenances(matcher.group("provenances")))
          .build();
    }

    private SFAssertion toSFAssertion(final Matcher matcher) {
      final SFAssertion.Builder sfAssertion = SFAssertion.builder();
      sfAssertion.subject((EntityNode) nodeFor(matcher.group("subject")));
      sfAssertion.subjectEntityType(Symbol.from(matcher.group("subjectEntityType")));
      sfAssertion.relation(Symbol.from(matcher.group("relation")));
      sfAssertion.provenances(toProvenances(matcher.group("provenances")));
      final Node objectNode = nodeFor(matcher.group("object"));
      if (objectNode instanceof EntityNode) {
        return sfAssertion.object((EntityNode) objectNode).build();
      } else {  // if (objectNode instanceof StringNode)
        return sfAssertion.object((StringNode) objectNode).build();
      }
    }

    private EventArgumentAssertion toEventArgumentAssertion(final Matcher matcher) {
      final EventArgumentAssertion.Builder eventArgAssertion = EventArgumentAssertion.builder();
      eventArgAssertion.subject((EventNode) nodeFor(matcher.group("subject")));
      eventArgAssertion.eventType(Symbol.from(matcher.group("eventType")));
      eventArgAssertion.role(Symbol.from(matcher.group("role")));
      eventArgAssertion.realis(Symbol.from(matcher.group("realis")));
      eventArgAssertion.provenances(toProvenances(matcher.group("provenances")));
      final Node argumentNode = nodeFor(matcher.group("argument"));
      if (argumentNode instanceof EntityNode) {
        return eventArgAssertion.argument((EntityNode) argumentNode).build();
      } else {  // if (argumentNode instanceof StringNode)
        return eventArgAssertion.argument((StringNode) argumentNode).build();
      }
    }

    /**
     * Although we treat inverse event argument assertions the same as regular event argument
     * assertions (and never write the inverse format out), we still want to be able to read them.
     */
    private EventArgumentAssertion toInverseEventArgumentAssertion(final Matcher matcher) {
      return EventArgumentAssertion.builder()
          .subject((EventNode) nodeFor(matcher.group("event")))
          .argument((EntityNode) nodeFor(matcher.group("subject")))
          .eventType(Symbol.from(matcher.group("eventType")))
          .role(Symbol.from(matcher.group("role")))
          .realis(Symbol.from(matcher.group("realis")))
          .provenances(toProvenances(matcher.group("provenances")))
          .build();
    }

    private MentionAssertion toMentionAssertion(final Matcher matcher) {
      final Node subjectNode = nodeFor(matcher.group("subject"));
      final String mentionType = matcher.group("mentionType");
      final String realisString = matcher.group("realis");
      if (mentionType.equals("mention")) {
        return toRegularMentionAssertion(matcher);
      } else if (mentionType.equals("canonical_mention")) {
        return toCanonicalMentionAssertion(matcher);
      } else if (mentionType.equals("nominal_mention")
          && subjectNode instanceof EntityNode
          && realisString == null) {
        return toNominalMentionAssertion(matcher);
      } else if (mentionType.equals("pronominal_mention")
          && subjectNode instanceof EntityNode
          && realisString == null) {
        return toPronominalMentionAssertion(matcher);
      } else if (mentionType.equals("normalized_mention")
          && subjectNode instanceof StringNode
          && realisString == null) {
        return toNormalizedMentionAssertion(matcher);
      } else {
        throw new IllegalArgumentException(
            String.format("\"%s\" is not a valid mention assertion.", matcher.group()));
      }
    }

    private MentionAssertion toRegularMentionAssertion(final Matcher matcher) {
      final Node subjectNode = nodeFor(matcher.group("subject"));
      final String mention = unescapedString(matcher.group("mention"));
      final String realisString = matcher.group("realis");
      final Set<Provenance> provenances = toProvenances(matcher.group("provenances"));
      if (subjectNode instanceof EventNode && realisString != null) {
        final Symbol realis = Symbol.from(realisString);
        return EventMentionAssertion.of((EventNode) subjectNode, mention, realis, provenances);
      } else if (subjectNode instanceof EntityNode && realisString == null) {
        return EntityMentionAssertion.of((EntityNode) subjectNode, mention, provenances);
      } else if (subjectNode instanceof StringNode && realisString == null) {
        return StringMentionAssertion.of((StringNode) subjectNode, mention, provenances);
      } else {
        throw new IllegalArgumentException(
            String.format("\"%s\" is not a valid mention assertion.", matcher.group()));
      }
    }

    private MentionAssertion toCanonicalMentionAssertion(final Matcher matcher) {
      final Node subjectNode = nodeFor(matcher.group("subject"));
      final String mention = unescapedString(matcher.group("mention"));
      final String realisString = matcher.group("realis");
      final Set<Provenance> provenances = toProvenances(matcher.group("provenances"));
      if (subjectNode instanceof EventNode && realisString != null) {
        final Symbol realis = Symbol.from(realisString);
        return EventCanonicalMentionAssertion.of((EventNode) subjectNode, mention, realis, provenances);
      } else if (subjectNode instanceof EntityNode && realisString == null) {
        return EntityCanonicalMentionAssertion.of((EntityNode) subjectNode, mention, provenances);
      } else if (subjectNode instanceof StringNode && realisString == null) {
        return StringCanonicalMentionAssertion.of((StringNode) subjectNode, mention, provenances);
      } else {
        throw new IllegalArgumentException(
            String.format("\"%s\" is not a valid mention assertion.", matcher.group()));
      }
    }

    private NominalMentionAssertion toNominalMentionAssertion(final Matcher matcher) {
      final EntityNode subjectNode = (EntityNode) nodeFor(matcher.group("subject"));
      final String mention = unescapedString(matcher.group("mention"));
      final Set<Provenance> provenances = toProvenances(matcher.group("provenances"));
      return NominalMentionAssertion.of(subjectNode, mention, provenances);
    }

    private PronominalMentionAssertion toPronominalMentionAssertion(final Matcher matcher) {
      final EntityNode subjectNode = (EntityNode) nodeFor(matcher.group("subject"));
      final String mention = unescapedString(matcher.group("mention"));
      final Set<Provenance> provenances = toProvenances(matcher.group("provenances"));
      return PronominalMentionAssertion.of(subjectNode, mention, provenances);
    }

    private NormalizedMentionAssertion toNormalizedMentionAssertion(final Matcher matcher) {
      final StringNode subjectNode = (StringNode) nodeFor(matcher.group("subject"));
      final String mention = unescapedString(matcher.group("mention"));
      final Set<Provenance> provenances = toProvenances(matcher.group("provenances"));
      return NormalizedMentionAssertion.of(subjectNode, mention, provenances);
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
              String.format("The following provenance could not be parsed: %s.", provenanceString));
        }
      }
      return provenances.build();
    }

    private Set<OffsetRange<CharOffset>> toOffsets(final String string) {
      final List<String> offsetStrings = SEMICOLON_SPLITTER.splitToList(string);

      final ImmutableSet.Builder<OffsetRange<CharOffset>> offsets = ImmutableSet.builder();
      for (final String offsetString : offsetStrings) {
        final Matcher matcher = OFFSET_PATTERN.matcher(offsetString);
        if (matcher.matches()) {
          final int start = Integer.parseInt(matcher.group("start"));
          final int end = Integer.parseInt(matcher.group("end"));
          offsets.add(OffsetRange.charOffsetRange(start, end));
        } else {
          throw new IllegalArgumentException(
              String.format("The following offset could not be parsed: %s.", offsetString));
        }
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

    private String unescapedString(final String string) {
      return string.replace("\\\"", "\"").replace("\\\\", "\\");
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
