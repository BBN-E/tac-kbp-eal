package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.EREEntity;
import com.bbn.nlp.corpora.ere.EREEntityMention;
import com.bbn.nlp.corpora.ere.EREEvent;
import com.bbn.nlp.corpora.ere.EREEventMention;
import com.bbn.nlp.corpora.ere.ERELoader;

import com.google.common.base.Predicates;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.bbn.bue.common.symbols.SymbolUtils.concat;
import static com.bbn.kbp.events2014.ResponseFunctions.role;
import static com.bbn.kbp.events2014.ResponseFunctions.type;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
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
 * Determines whether a system response canonical argument string is close enough to one of
 * acceptable CASes for a query.
 */
interface CASMatchCriterion {

  boolean matches(KBPString response, Set<OffsetRange<CharOffset>> queryValidCASes);

  String humanFriendlyName();
}

/**
 * Determines whether a system predicate justification is close enough to query predicate
 * justification to "match"
 */
interface PJMatchCriterion {

  boolean matches(Set<CharOffsetSpan> responsePJ, Set<OffsetRange<CharOffset>> queryPJ);

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
class EREBasedCorpusQueryExecutor implements CorpusQueryExecutor2016 {

  private static final Logger log = LoggerFactory.getLogger(EREBasedCorpusQueryExecutor.class);

  private final ImmutableList<AlignmentConfiguration> alignmentConfigurations;
  private final LoadingCache<Symbol, EREDocument> ereDocCache;
  private final EREToKBPEventOntologyMapper ontologyMapper;

  EREBasedCorpusQueryExecutor(final Iterable<AlignmentConfiguration> alignmentConfigurations,
      final LoadingCache<Symbol, EREDocument> ereDocCache,
      final EREToKBPEventOntologyMapper ontologyMapper) {
    this.ereDocCache = checkNotNull(ereDocCache);
    this.ontologyMapper = checkNotNull(ontologyMapper);
    this.alignmentConfigurations = ImmutableList.copyOf(alignmentConfigurations);
  }

  /**
   * The default query matching strategy for the 2016 evaluation.
   */
  public static EREBasedCorpusQueryExecutor createDefaultFor2016(
      final Map<Symbol, File> docIdToEREMap,
      final ERELoader ereLoader, final EREToKBPEventOntologyMapper ontologyMapper,
      int slack) {
    final LoadingCache<Symbol, EREDocument> ereDocCache = CacheBuilder.newBuilder()
        .maximumSize(50)
        .build(new CacheLoader<Symbol, EREDocument>() {
          @Override
          public EREDocument load(final Symbol docID) throws Exception {
            final File ereFileName = docIdToEREMap.get(docID);
            if (ereFileName != null) {
              return ereLoader.loadFrom(ereFileName);
            } else {
              throw new TACKBPEALException("Cannot find ERE file for " + docID);
            }
          }
        });

    return new EREBasedCorpusQueryExecutor(ImmutableList.of(
        AlignmentConfiguration
            .of(ExactCASMatch.INSTANCE, new ResponsePJContainsEntryPJWithSlack(slack)),
        AlignmentConfiguration.of(QueryCASContainsSystemCAS.INSTANCE,
            new ResponsePJContainsEntryPJWithSlack(slack))), ereDocCache, ontologyMapper);
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
    msg.append(matchingResponses.size()).append(" responses match entry points\n");
    msg.append(docEventsMatchingEntryPoints.size()).append(" document events match entry points\n");
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
    final EREEvent ereEventForEntryPoint =
        ereEventMentionForEntryPoint(queryEntryPoint);
    final ImmutableSet<Symbol> entryPointEventTypes = gatherTypes(ereEventForEntryPoint);

    final Symbol mappedRole = ontologyMapper.eventRole(queryEntryPoint.role()).get();
    final ImmutableList<Response> argumentsMatchingInTypeAndEventType =
        FluentIterable.from(docSystemOutput.arguments().responses())
            .filter(compose(in(entryPointEventTypes), type()))
            .filter(compose(equalTo(mappedRole), role())).toList();
    msg.append(argumentsMatchingInTypeAndEventType.size())
        .append(" arguments matched in type and role\n");

    final ImmutableSet<OffsetRange<CharOffset>> validCASOffsets =
        gatherValidCASOffsets(queryEntryPoint);
    checkState(!validCASOffsets.isEmpty());
    final ImmutableSet<OffsetRange<CharOffset>> eventPJs =
        gatherEventPJs(ereEventForEntryPoint);
    checkState(!eventPJs.isEmpty());

    msg.append("Query valid CASes are ").append(validCASOffsets).append("\n");
    for (AlignmentConfiguration alignConfig : alignmentConfigurations) {
      if (matchingResponses.isEmpty()) {
        addMatchingResponses(validCASOffsets, eventPJs, matchingResponses, alignConfig,
            argumentsMatchingInTypeAndEventType, msg);
      }
    }
  }

  private ImmutableSet<Symbol> gatherTypes(final EREEvent ereEvent) {
    final ImmutableSet.Builder<Symbol> ret = ImmutableSet.builder();
    for (final EREEventMention ereEventMention : ereEvent.getEventMentions()) {
      final Symbol mappedMainType = ontologyMapper.eventType(
          Symbol.from(ereEventMention.getType())).get();
      final Symbol mappedSubType = ontologyMapper.eventSubtype(
          Symbol.from(ereEventMention.getSubtype())).get();

      ret.add(concat(concat(mappedMainType, "."), mappedSubType));
    }
    return ret.build();
  }

  private EREEvent ereEventMentionForEntryPoint(
      final CorpusQueryEntryPoint queryEntryPoint) {
    final EREDocument ereDoc;
    try {
      ereDoc = ereDocCache.get(queryEntryPoint.docID());
    } catch (ExecutionException e) {
      throw new TACKBPEALException(e);
    }
    for (final EREEvent ereEvent : ereDoc.getEvents()) {
      if (ereEvent.getID().equals(queryEntryPoint.hopperID().asString())) {
        return ereEvent;
      }
    }
    throw new TACKBPEALException("Could not find ERE event hopper with ID "
        + queryEntryPoint.hopperID() + " for doc ID " + queryEntryPoint.docID());
  }

  private EREEntity ereEntityForQuery(final CorpusQueryEntryPoint queryEntryPoint) {
    final EREDocument ereDoc;
    try {
      ereDoc = ereDocCache.get(queryEntryPoint.docID());
    } catch (ExecutionException e) {
      throw new TACKBPEALException(e);
    }

    for (final EREEntity ereEntity : ereDoc.getEntities()) {
      if (ereEntity.getID().equals(queryEntryPoint.entity().asString())) {
        return ereEntity;
      }
    }
    throw new TACKBPEALException("Could not find entity " + queryEntryPoint.entity()
        + " in document " + queryEntryPoint.docID());
  }

  private ImmutableSet<OffsetRange<CharOffset>> gatherEventPJs(final EREEvent ereEvent) {
    final ImmutableSet.Builder<OffsetRange<CharOffset>> ret = ImmutableSet.builder();
    for (final EREEventMention ereEventMention : ereEvent) {
      ret.add(OffsetRange.charOffsetRange(ereEventMention.getTrigger().getStart(),
          ereEventMention.getTrigger().getEnd()));
    }
    return ret.build();
  }

  private ImmutableSet<OffsetRange<CharOffset>> gatherValidCASOffsets(
      final CorpusQueryEntryPoint entryPoint) {
    final EREEntity entityForQuery = ereEntityForQuery(entryPoint);

    final ImmutableSet.Builder<OffsetRange<CharOffset>> ret = ImmutableSet.builder();
    for (final EREEntityMention ereEntityMention : entityForQuery.getMentions()) {
      ret.add(OffsetRange.charOffsetRange(ereEntityMention.getExtent().getStart(),
          ereEntityMention.getExtent().getEnd()));
    }
    return ret.build();
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

  private void addMatchingResponses(final Set<OffsetRange<CharOffset>> queryValidCASOffsets,
      final Set<OffsetRange<CharOffset>> queryPJOffsets,
      final List<Response> matchingResponses,
      final AlignmentConfiguration alignConfig,
      final ImmutableList<Response> argumentsMatchingInTypeAndEventType, final StringBuilder msg) {
    for (final Response response : filter(argumentsMatchingInTypeAndEventType,
        not(in(matchingResponses)))) {
      final boolean casMatches = alignConfig.casMatchCriterion()
          .matches(response.canonicalArgument(), queryValidCASOffsets);
      if (casMatches) {
        msg.append("\t").append(alignConfig.casMatchCriterion().humanFriendlyName()).append(" to ")
            .append(response.canonicalArgument()).append("\n");
        final boolean pjMatches = alignConfig.pjMatchCriterion().matches(
            response.predicateJustifications(), queryPJOffsets);
        if (pjMatches) {
          msg.append("\t\tResponse ").append(response).append(" accepted as match\n");
          matchingResponses.add(response);
        } else {
          msg.append("\t\tResponse rejected due to insufficient PJ overlap. Response PJs:")
              .append(response.predicateJustifications()).append("; query PJs: ")
              .append(queryPJOffsets).append("\n");
        }
      } else {
        msg.append("\t").append(alignConfig.casMatchCriterion().humanFriendlyName())
            .append(" failed on ")
            .append(response.canonicalArgument()).append("\n");
      }
    }
  }
}

enum ExactCASMatch implements CASMatchCriterion {
  INSTANCE;

  @Override
  public boolean matches(final KBPString response,
      final Set<OffsetRange<CharOffset>> queryValidCASes) {
    return !FluentIterable.from(queryValidCASes)
        .filter(Predicates.equalTo(response.charOffsetSpan().asCharOffsetRange()))
        .isEmpty();
  }

  @Override
  public String humanFriendlyName() {
    return "Exact CAS match";
  }
}

enum QueryCASContainsSystemCAS implements CASMatchCriterion {
  INSTANCE;

  @Override
  public boolean matches(final KBPString response,
      final Set<OffsetRange<CharOffset>> queryValidCASes) {
    for (final OffsetRange<CharOffset> queryValidCAS : queryValidCASes) {
      if (queryValidCAS.contains(response.charOffsetSpan().asCharOffsetRange())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String humanFriendlyName() {
    return "Query-CAS-contains-system-CAS";
  }
}

enum EntryPointPJContainsResponsePJ implements PJMatchCriterion {
  INSTANCE;

  @Override
  public boolean matches(final Set<CharOffsetSpan> responsePJ,
      final Set<OffsetRange<CharOffset>> queryPJs) {
    for (final CharOffsetSpan pj : responsePJ) {
      for (final OffsetRange<CharOffset> queryPJ : queryPJs) {
        if (queryPJ.contains(pj.asCharOffsetRange())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String humanFriendlyName() {
    return "Entry-point-PJ-contains-response-PJ";
  }
}

final class ResponsePJContainsEntryPJWithSlack implements PJMatchCriterion {

  private final int slack;

  public ResponsePJContainsEntryPJWithSlack(final int slack) {
    this.slack = slack;
    checkArgument(slack >= 0);
  }

  @Override
  public boolean matches(final Set<CharOffsetSpan> responsePJs,
      final Set<OffsetRange<CharOffset>> queryPJs) {
    for (final OffsetRange<CharOffset> queryPJ : queryPJs) {
      for (final CharOffsetSpan responsePJ : responsePJs) {
        if (withinSlack(responsePJ.asCharOffsetRange(), queryPJ)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean withinSlack(final OffsetRange<CharOffset> container,
      final OffsetRange<CharOffset> containee) {
    return OffsetRange.charOffsetRange(Math.max(0, container.startInclusive().asInt() - slack),
        container.endInclusive().asInt() + slack).contains(containee);
  }

  private OffsetRange<CharOffset> extendRight(final OffsetRange<CharOffset> source,
      final int rightShift) {
    return OffsetRange.charOffsetRange(source.startInclusive().asInt(),
        source.endInclusive().asInt() + rightShift);
  }

  @Override
  public String humanFriendlyName() {
    return "Response-PJ-contains-entryPoint-PJ-with-slack-" + slack;
  }
}
