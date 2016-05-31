package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.bbn.kbp.events2014.ResponseFunctions.role;
import static com.bbn.kbp.events2014.ResponseFunctions.type;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;

/**
 * Given system output and a 2016 corpus-level query, will locate all matching events corpus-wide.
 */
public interface CorpusQueryExecutor2016 {

  ImmutableSet<DocEventFrameReference> queryEventFrames(SystemOutputStore2016 systemOutput2016,
      CorpusQuery2016 query) throws IOException;
}

/**
 * Determines whether a system response canonical argument string is close enough to a query
 * canonical argument string to "match"
 */
interface CASMatchCriterion {

  boolean matches(KBPString response, OffsetRange<CharOffset> query);

  String humanFriendlyName();
}

/**
 * Determines whether a system predicate justification is close enough to query predicate
 * justification to "match"
 */
interface PJMatchCriterion {

  boolean matches(ImmutableSet<CharOffsetSpan> responsePJ, OffsetRange<CharOffset> queryPJ);

  String humanFriendlyName();
}

/**
 * Executes the following strategy for matching queries against a corpus linking: <ul> <li>For each
 * entry point, locate the document it belongs to and find all responses which match in event type
 * and role.</li> <li>For each of a number of "match" criteria based on CAS and PJ overlap, gather
 * all matching responses.  Only proceed to applying later match criteria if the earlier ones found
 * no matches.</li> <li>Collect all document-level events containing the matching response.</li>
 * <li>Collect all document-level events which occur in the same corpus-level event as one of these
 * responses</li> </ul>
 */
class DefaultCorpusQueryExecutor implements CorpusQueryExecutor2016 {

  private static final Logger log = LoggerFactory.getLogger(DefaultCorpusQueryExecutor.class);

  private final ImmutableList<AlignmentConfiguration> alignmentConfigurations;

  DefaultCorpusQueryExecutor(final Iterable<AlignmentConfiguration> alignmentConfigurations) {
    this.alignmentConfigurations = ImmutableList.copyOf(alignmentConfigurations);
  }

  /**
   * The default query matching strategy for the 2016 evaluation.
   */
  public static DefaultCorpusQueryExecutor createDefaultFor2016() {
    return new DefaultCorpusQueryExecutor(ImmutableList.of(
        AlignmentConfiguration.of(ExactCASMatch.INSTANCE, EntryPointPJContainsResponsePJ.INSTANCE),
        AlignmentConfiguration.of(QueryCASContainsSystemCAS.INSTANCE,
            EntryPointPJContainsResponsePJ.INSTANCE)
    ));
  }

  @Value.Immutable
  @TextGroupPublicImmutable
  abstract static class _AlignmentConfiguration {

    @Value.Parameter
    public abstract CASMatchCriterion casMatchCriterion();

    @Value.Parameter
    public abstract PJMatchCriterion pjMatchCriterion();
  }

  @Override
  public ImmutableSet<DocEventFrameReference> queryEventFrames(SystemOutputStore2016 systemOutput,
      final CorpusQuery2016 query) throws IOException {
    final StringBuilder msg = new StringBuilder();
    final CorpusEventLinking corpusEventLinking = systemOutput.readCorpusEventFrames();

    msg.append("Applying query ").append(query).append(" to ").append(systemOutput.systemID())
        .append("\n");

    // first we find which document-level event frames match one or more of the query entry points
    final ImmutableSet<DocEventFrameReference> docEventsMatchingEntryPoints =
        documentEventsMatchingAnyQueryEntryPoint(query, systemOutput, msg);

    // next we find which corpus-level events contain those document-level event frames
    final ImmutableSet<CorpusEventFrame> corpusEventsMatchingQuery =
        corpusEventsMatchingQuery(docEventsMatchingEntryPoints, corpusEventLinking, msg);

    // then we return to the document level by taking all document-level events
    // in the corpus-level events we just found. These document-level events are those
    // which are "coreferent" across the corpus with the ones matched by query entry points
    final ImmutableSet<DocEventFrameReference> docEventsInMatchedCorpusEvents =
        documentEventsInCorpusEvents(corpusEventsMatchingQuery, msg);

    log.info(msg.toString());
    return docEventsInMatchedCorpusEvents;
  }

  private ImmutableSet<CorpusEventFrame> corpusEventsMatchingQuery(
      final ImmutableSet<DocEventFrameReference> docEventsMatchingEntryPoints,
      final CorpusEventLinking corpusEventLinking, final StringBuilder msg) {
    final ImmutableSet.Builder<CorpusEventFrame> corpusEventsMatchingQueryB =
        ImmutableSet.builder();
    for (final DocEventFrameReference docEventMatchingEntryPoint : docEventsMatchingEntryPoints) {
      corpusEventsMatchingQueryB
          .addAll(corpusEventLinking.docEventsToCorpusEvents().get(docEventMatchingEntryPoint));
    }
    final ImmutableSet<CorpusEventFrame> corpusEventsMatchingQuery =
        corpusEventsMatchingQueryB.build();
    msg.append(corpusEventsMatchingQuery.size())
        .append(" corpus events found matching query\n");
    return corpusEventsMatchingQuery;
  }

  private ImmutableSet<DocEventFrameReference> documentEventsMatchingAnyQueryEntryPoint(
      final CorpusQuery2016 query, final SystemOutputStore2016 systemOutput,
      final StringBuilder msg) throws IOException {
    final List<Response> matchingResponses = new ArrayList<>();

    final ImmutableSet.Builder<DocEventFrameReference> docEventsMatchingEntryPointsB =
        ImmutableSet.builder();
    for (final CorpusQueryEntryPoint queryEntryPoint : query.entryPoints()) {
      if (systemOutput.docIDs().contains(queryEntryPoint.docID())) {
        final DocumentSystemOutput2015 docSystemOutput = systemOutput.read(queryEntryPoint.docID());
        gatherResponsesMatchingEntryPoints(queryEntryPoint, docSystemOutput, matchingResponses,
            msg);
        gatherDocumentEventsForResponses(matchingResponses, docSystemOutput,
            docEventsMatchingEntryPointsB);
      } else {
        throw new TACKBPEALException("Query entry point is in a document not in system output: "
            + queryEntryPoint.docID());
      }
    }

    final ImmutableSet<DocEventFrameReference> docEventsMatchingEntryPoints =
        docEventsMatchingEntryPointsB.build();
    msg.append(matchingResponses.size() + " responses match entry points\n");
    msg.append(docEventsMatchingEntryPoints.size() + " document events match entry points\n");
    return docEventsMatchingEntryPoints;
  }

  private ImmutableSet<DocEventFrameReference> documentEventsInCorpusEvents(
      final ImmutableSet<CorpusEventFrame> corpusEventsMatchingQuery, final StringBuilder msg) {
    final ImmutableSet.Builder<DocEventFrameReference> docEventsInMatchedCorpusEventsB =
        ImmutableSet.builder();
    for (final CorpusEventFrame corpusEventFrame : corpusEventsMatchingQuery) {
      docEventsInMatchedCorpusEventsB.addAll(corpusEventFrame.docEventFrames());
    }
    final ImmutableSet<DocEventFrameReference> docEventsInMatchedCorpusEvents =
        docEventsInMatchedCorpusEventsB.build();

    msg.append(docEventsInMatchedCorpusEvents.size())
        .append(" document events found matching query\n");
    return docEventsInMatchedCorpusEvents;
  }

  /**
   * Only tries lower ranked alignment strategies if no matches have been found yet, hence the array
   * of matchingResponses.
   */
  private void gatherResponsesMatchingEntryPoints(final CorpusQueryEntryPoint queryEntryPoint,
      final DocumentSystemOutput2015 docSystemOutput, final List<Response> matchingResponses,
      final StringBuilder msg) {
    final ImmutableList<Response> argumentsMatchingInTypeAndEventType =
        FluentIterable.from(docSystemOutput.arguments().responses())
            .filter(compose(equalTo(queryEntryPoint.eventType()), type()))
            .filter(compose(equalTo(queryEntryPoint.role()), role())).toList();
    msg.append(argumentsMatchingInTypeAndEventType.size())
        .append(" arguments matched in type and role\n");
    for (AlignmentConfiguration alignConfig : alignmentConfigurations) {
      if (matchingResponses.isEmpty()) {
        addMatchingResponses(queryEntryPoint, matchingResponses, alignConfig,
            argumentsMatchingInTypeAndEventType, msg);
      }
    }
  }

  private void gatherDocumentEventsForResponses(final List<Response> matchingResponses,
      final DocumentSystemOutput2015 docSystemOutput,
      final ImmutableSet.Builder<DocEventFrameReference> retB) {
    final ImmutableSet.Builder<ResponseSet> matchingResponseSetsB = ImmutableSet.builder();
    for (final Response matchingResponse : matchingResponses) {
      matchingResponseSetsB.addAll(docSystemOutput.linking()
          .responsesToContainingResponseSets().get(matchingResponse));
    }
    final ImmutableSet<ResponseSet> matchingResponseSets = matchingResponseSetsB.build();

    for (final ResponseSet matchingResponseSet : matchingResponseSets) {
      retB.add(docSystemOutput.linking().asEventFrameReference(matchingResponseSet));
    }
  }

  private void addMatchingResponses(final CorpusQueryEntryPoint queryEntryPoint,
      final List<Response> matchingResponses,
      final AlignmentConfiguration alignConfig,
      final ImmutableList<Response> argumentsMatchingInTypeAndEventType, final StringBuilder msg) {
    for (final Response response : filter(argumentsMatchingInTypeAndEventType,
        not(in(matchingResponses)))) {
      final boolean casMatches = alignConfig.casMatchCriterion()
          .matches(response.canonicalArgument(), queryEntryPoint.casOffsets());
      if (casMatches) {
        msg.append("\t").append(alignConfig.casMatchCriterion().humanFriendlyName()).append(" to ")
            .append(response.canonicalArgument()).append("\n");
        final boolean pjMatches = alignConfig.pjMatchCriterion().matches(
            response.predicateJustifications(), queryEntryPoint.predicateJustification());
        if (pjMatches) {
          msg.append("\t\tResponse ").append(response).append(" accepted as match\n");
          matchingResponses.add(response);
        } else {
          msg.append("\t\tResponse rejected due to insufficient PJ overlap\n");
        }
      }
    }
  }
}

enum ExactCASMatch implements CASMatchCriterion {
  INSTANCE;

  @Override
  public boolean matches(final KBPString response, final OffsetRange<CharOffset> query) {
    return response.charOffsetSpan().asCharOffsetRange().equals(query);
  }

  @Override
  public String humanFriendlyName() {
    return "Exact CAS match";
  }
}

enum QueryCASContainsSystemCAS implements CASMatchCriterion {
  INSTANCE;

  @Override
  public boolean matches(final KBPString response, final OffsetRange<CharOffset> query) {
    return query.contains(response.charOffsetSpan().asCharOffsetRange());
  }

  @Override
  public String humanFriendlyName() {
    return "Query-CAS-contains-system-CAS";
  }
}

enum EntryPointPJContainsResponsePJ implements PJMatchCriterion {
  INSTANCE;

  @Override
  public boolean matches(final ImmutableSet<CharOffsetSpan> responsePJ,
      final OffsetRange<CharOffset> queryPJ) {
    for (final CharOffsetSpan pj : responsePJ) {
      if (queryPJ.contains(pj.asCharOffsetRange())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String humanFriendlyName() {
    return "Entry-point-PJ-contains-response-PJ";
  }
}
