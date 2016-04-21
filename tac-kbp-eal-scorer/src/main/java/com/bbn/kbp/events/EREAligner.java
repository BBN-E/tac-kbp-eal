package com.bbn.kbp.events;

import com.bbn.bue.common.TextGroupPackageImmutable;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.TACException;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.Response;
import com.bbn.nlp.corenlp.CoreNLPDocument;
import com.bbn.nlp.corenlp.CoreNLPParseNode;
import com.bbn.nlp.corenlp.CoreNLPSentence;
import com.bbn.nlp.corpora.ere.EREArgument;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.EREEntityArgument;
import com.bbn.nlp.corpora.ere.EREEvent;
import com.bbn.nlp.corpora.ere.EREEventMention;
import com.bbn.nlp.corpora.ere.EREFillerArgument;
import com.bbn.nlp.corpora.ere.ERESpan;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Aligns a TAC KBP {@link Response} to ERE entities or fillers by offset matching. Offset matching
 * may be relaxed in two ways: <ul> <li>By fuzzier offset matching (e.g. allowing containment or
 * overlap)</li> <li>By finding the head of the KBP {@link Response} and aligning with the ERE head
 * (if any)</li> </ul>
 */
final class EREAligner {

  private static final Logger log = LoggerFactory.getLogger(EREAligner.class);

  private final boolean relaxUsingCORENLP;
  private final boolean useExactMatchForCoreNLPRelaxation;
  private final EREDocument ereDoc;
  private final Optional<CoreNLPDocument> coreNLPDoc;
  private final ImmutableList<ResponseToEREEArgAlignmentRule> responseMatchingStrategy;

  private EREAligner(final boolean relaxUsingCORENLP,
      final boolean useExactMatchForCoreNLPRelaxation,
      final EREDocument ereDoc, final Optional<CoreNLPDocument> coreNLPDocument,
      ImmutableList<ResponseToEREEArgAlignmentRule> responseMatchingStrategy) {
    this.relaxUsingCORENLP = relaxUsingCORENLP;
    this.useExactMatchForCoreNLPRelaxation = useExactMatchForCoreNLPRelaxation;
    this.ereDoc = ereDoc;
    this.coreNLPDoc = coreNLPDocument;
    checkState(!relaxUsingCORENLP || coreNLPDoc.isPresent(),
        "Either we have our CoreNLPDocument or we are not relaxing using it");
    this.responseMatchingStrategy = responseMatchingStrategy;
  }

  static EREAligner create(final boolean relaxUsingCORENLP,
      final boolean useExactMatchForCoreNLPRelaxation,
      final EREDocument ereDoc, final Optional<CoreNLPDocument> coreNLPDocument,
      final EREToKBPEventOntologyMapper mapping) {
    return new EREAligner(relaxUsingCORENLP, useExactMatchForCoreNLPRelaxation, ereDoc,
        coreNLPDocument,
        createResponseMatchingStrategy(relaxUsingCORENLP, useExactMatchForCoreNLPRelaxation,
            coreNLPDocument, mapping));
  }

  private static final ImmutableList<Function<Response, CharOffsetSpan>> responseSpanFunctions =
      ImmutableList.<Function<Response, CharOffsetSpan>>of(CasExtractor.INSTANCE,
          BaseFillerExtractor.INSTANCE);

  // build the list of alignment rules which will be applied in order until one matches
  private static ImmutableList<ResponseToEREEArgAlignmentRule> createResponseMatchingStrategy(
      final boolean relaxUsingCORENLP,
      final boolean useExactMatchForCoreNLPRelaxation,
      Optional<CoreNLPDocument> coreNLPDoc, EREToKBPEventOntologyMapper mapping) {
    final ImmutableList.Builder<ResponseToEREEArgAlignmentRule> ret = ImmutableList.builder();

    // first we try all our alignment rules on the CAS; if none succeed, we fall back to the BF
    for (final Function<Response, CharOffsetSpan> responseExtractor : responseSpanFunctions) {
      final Function<Response, CharOffsetSpan> responseHeadExtractor;
      // if a CoreNLP analysis is provided we will use it to find the heads of response spans
      if (relaxUsingCORENLP) {
        if(coreNLPDoc.isPresent()) {
          responseHeadExtractor = CoreNLPHeadExtractor.of(coreNLPDoc.get(), responseExtractor);
        } else  {
          throw new TACException("Attempting to relax using CoreNLP when no doc is present!");
        }
      } else {
        responseHeadExtractor = responseExtractor;
      }

      // these are atoms out of which more complex rules will be built
      final SpansMatchExactly responseMatchesEREExtentExactly =
          new SpansMatchExactly(responseExtractor, ereExtentExtractor);
      final SpansMatchExactly responseHeadMatchesEREExtentExactly =
          new SpansMatchExactly(responseHeadExtractor, ereExtentExtractor);
      final MappedRolesMatch mappedRolesMatch = MappedRolesMatch.of(mapping);
      final SpansMatchExactly responseHeadMatchesEREHeadExactly =
          new SpansMatchExactly(responseHeadExtractor, ereHeadIfPresent);
      final SpansMatchExactly responseMatchesEREHeadExactly =
          new SpansMatchExactly(responseExtractor, ereHeadIfPresent);
      final ContainmentSpanChecker alignByContainment =
          new ContainmentSpanChecker(responseExtractor, responseHeadExtractor,
              ereExtentExtractor, ereHeadIfPresent);

      ret.addAll(ImmutableList.of(
          // first search for matches subject to a requirement that the response role
          // match the ERE argument role, to be generous
          And.of(responseMatchesEREExtentExactly, mappedRolesMatch),
          And.of(responseHeadMatchesEREHeadExactly, mappedRolesMatch),
          And.of(responseHeadMatchesEREExtentExactly, mappedRolesMatch),
          And.of(responseMatchesEREHeadExactly, mappedRolesMatch),
          // then do the same search without that restriction
          responseMatchesEREExtentExactly,
          responseHeadMatchesEREHeadExactly,
          responseHeadMatchesEREExtentExactly,
          responseMatchesEREHeadExactly,
          // finally we do a more aggressive alignment attempt by containment
          And.of(alignByContainment, mappedRolesMatch),
          alignByContainment
      ));
    }
    return ret.build();
  }


  Optional<ScoringCorefID> argumentForResponse(final Response response) {
    // for each alignment rule in order, try to find an ERE object which aligns
    for (final ResponseToEREEArgAlignmentRule responseChecker : responseMatchingStrategy) {
      final Optional<ScoringCorefID> found = findEREObjectMatchingRule(response, responseChecker);
      if (found.isPresent()) {
        return found;
      }
    }

    return Optional.absent();
  }

  private Optional<ScoringCorefID> findEREObjectMatchingRule(final Response r,
      final ResponseToEREEArgAlignmentRule checker) {
    final ImmutableSet.Builder<ScoringCorefID> retB = ImmutableSet.builder();
    for (final EREEvent e : ereDoc.getEvents()) {
      for (final EREEventMention em : e.getEventMentions()) {
        for (final EREArgument ea : em.getArguments()) {
          if (checker.aligns(r, ea)) {
            retB.add(ScoringUtils.extractScoringEntity(ea, ereDoc));
          }
        }
      }
    }

    final ImmutableSet<ScoringCorefID> ret = retB.build();
    if (ret.size() > 1) {
      log.warn("Multiple matches for response {}: {}", r, ret);
    }
    return Optional.fromNullable(Iterables.getFirst(ret, null));
  }

  private Optional<Range<CharOffset>> getCoreNLPHead(final OffsetRange<CharOffset> offsets) {
    if (!coreNLPDoc.isPresent()) {
      return Optional.absent();
    }
    final Optional<CoreNLPSentence> sent =
        coreNLPDoc.get().firstSentenceContaining(offsets);
    if (sent.isPresent()) {
      final Optional<CoreNLPParseNode> node =
          sent.get().nodeForOffsets(offsets);
      if (node.isPresent()) {
        final Optional<CoreNLPParseNode> terminalHead = node.get().terminalHead();
        if (terminalHead.isPresent()) {
          final Range<CharOffset> coreNLPHeadRange = terminalHead.get().span().asRange();
          return Optional.of(coreNLPHeadRange);
        }
      }
    }
    return Optional.absent();
  }

  private static final Function<EREArgument, CharOffsetSpan> ereExtentExtractor =
      new Function<EREArgument, CharOffsetSpan>() {
        @Nullable
        @Override
        public CharOffsetSpan apply(@Nullable final EREArgument ereArgument) {
          checkNotNull(ereArgument);
          final ERESpan ereSpan;
          if (ereArgument instanceof EREEntityArgument) {
            ereSpan = ((EREEntityArgument) ereArgument).entityMention().getExtent();

          } else if (ereArgument instanceof EREFillerArgument) {
            ereSpan = ((EREFillerArgument) ereArgument).filler().getExtent();
          } else {
            throw new RuntimeException("Unknown EREArgument type: " + ereArgument.getClass());
          }
          return CharOffsetSpan
              .fromOffsetsAndDebugString(ereSpan.getStart(), ereSpan.getEnd(), ereSpan.getText());
        }
      };

  // falling back to the extent won't harm us since we'll always run this after the ereExtentExtractor
  private static final Function<EREArgument, CharOffsetSpan> ereHeadIfPresent =
      new Function<EREArgument, CharOffsetSpan>() {
        @Nullable
        @Override
        public CharOffsetSpan apply(@Nullable final EREArgument ereArgument) {
          checkNotNull(ereArgument);
          final ERESpan ereSpan;
          if (ereArgument instanceof EREEntityArgument) {
            final Optional<ERESpan> head =
                ((EREEntityArgument) ereArgument).entityMention().getHead();
            if (head.isPresent()) {
              ereSpan = head.get();
            } else {
              ereSpan = ((EREEntityArgument) ereArgument).entityMention().getExtent();
            }
          } else if (ereArgument instanceof EREFillerArgument) {
            ereSpan = ((EREFillerArgument) ereArgument).filler().getExtent();
          } else {
            throw new RuntimeException("Unknown EREArgument type: " + ereArgument.getClass());
          }
          return CharOffsetSpan
              .fromOffsetsAndDebugString(ereSpan.getStart(), ereSpan.getEnd(), ereSpan.getText());
        }
      };

  private enum CasExtractor implements Function<Response, CharOffsetSpan> {
    INSTANCE;

    @Override
    public CharOffsetSpan apply(@Nullable final Response response) {
      return checkNotNull(response).canonicalArgument().charOffsetSpan();
    }
  }

  private enum BaseFillerExtractor implements Function<Response, CharOffsetSpan> {
    INSTANCE;

    @Override
    public CharOffsetSpan

    apply(@Nullable final Response response) {
      return checkNotNull(response).baseFiller();
    }
  }

  @TextGroupPackageImmutable
  @Value.Immutable
  static abstract class _CoreNLPHeadExtractor implements Function<Response, CharOffsetSpan> {

    @Value.Parameter
    public abstract CoreNLPDocument coreNLPDoc();

    @Value.Parameter
    public abstract Function<Response, CharOffsetSpan> rangeFinder();

    public CharOffsetSpan apply(final Response response) {
      final CharOffsetSpan off = checkNotNull(rangeFinder().apply(response));
      final Optional<CoreNLPSentence> sent =
          coreNLPDoc().firstSentenceContaining(off.asCharOffsetRange());
      if (sent.isPresent()) {
        final Optional<CoreNLPParseNode> node =
            sent.get().nodeForOffsets(off.asCharOffsetRange());
        if (node.isPresent()) {
          final Optional<CoreNLPParseNode> terminalHead = node.get().terminalHead();
          if (terminalHead.isPresent()) {
            final Range<CharOffset> coreNLPHeadRange = terminalHead.get().span().asRange();
            return CharOffsetSpan
                .fromOffsetsAndDebugString(coreNLPHeadRange.lowerEndpoint().asInt(),
                    coreNLPHeadRange.upperEndpoint().asInt(),
                    terminalHead.get().token().get().content());
          }
        }
      }
      return off;
    }
  }

  interface ResponseToEREEArgAlignmentRule {

    boolean aligns(Response r, EREArgument ea);
  }

  private static abstract class SpanChecker implements ResponseToEREEArgAlignmentRule {

    protected final Function<Response, CharOffsetSpan> responseSpanExtractor;
    protected final Function<EREArgument, CharOffsetSpan> ereArgSpanExtractor;

    protected SpanChecker(final Function<Response, CharOffsetSpan> responseSpanExtractor,
        final Function<EREArgument, CharOffsetSpan> ereArgSpanExtractor) {
      this.responseSpanExtractor = responseSpanExtractor;
      this.ereArgSpanExtractor = ereArgSpanExtractor;
    }
  }

  private static class SpansMatchExactly extends SpanChecker {

    protected SpansMatchExactly(
        final Function<Response, CharOffsetSpan> responseSpanExtractor,
        final Function<EREArgument, CharOffsetSpan> ereArgSpanExtractor) {
      super(responseSpanExtractor, ereArgSpanExtractor);
    }

    @Override
    public boolean aligns(final Response r, final EREArgument ea) {
      return responseSpanExtractor.apply(r).equals(ereArgSpanExtractor.apply(ea));
    }
  }

  private static final class ContainmentSpanChecker implements
      ResponseToEREEArgAlignmentRule {

    protected final Function<Response, CharOffsetSpan> responseSpanExtractor;
    protected final Function<Response, CharOffsetSpan> responseHeadExtractor;

    protected final Function<EREArgument, CharOffsetSpan> ereArgSpanExtractor;
    protected final Function<EREArgument, CharOffsetSpan> ereHeadExtractor;

    private ContainmentSpanChecker(final Function<Response, CharOffsetSpan> responseSpanExtractor,
        final Function<Response, CharOffsetSpan> responseHeadExtractor,
        final Function<EREArgument, CharOffsetSpan> ereArgSpanExtractor,
        final Function<EREArgument, CharOffsetSpan> ereHeadExtractor) {
      this.responseSpanExtractor = responseSpanExtractor;
      this.responseHeadExtractor = responseHeadExtractor;
      this.ereArgSpanExtractor = ereArgSpanExtractor;
      this.ereHeadExtractor = ereHeadExtractor;
    }


    @Override
    public boolean aligns(final Response r, final EREArgument ea) {
      final Range<CharOffset> responseOffsets =
          responseSpanExtractor.apply(r).asCharOffsetRange().asRange();
      final Range<CharOffset> responseHead =
          responseHeadExtractor.apply(r).asCharOffsetRange().asRange();

      final Range<CharOffset> ereOffsets =
          ereArgSpanExtractor.apply(ea).asCharOffsetRange().asRange();
      final Range<CharOffset> ereHead = ereHeadExtractor.apply(ea).asCharOffsetRange().asRange();

      // ereOffsets.encloses(ereHead) in case of annotation inconsistency
      if (ereOffsets.encloses(responseOffsets)) {
        return
            (responseOffsets.encloses(ereHead) && responseOffsets.encloses(responseHead))
                || (ereOffsets.encloses(responseHead) && ereOffsets.encloses(ereHead));
      }
      return false;
    }
  }

  @TextGroupPackageImmutable
  @Value.Immutable
  static abstract class _And implements ResponseToEREEArgAlignmentRule {

    @Value.Parameter
    public abstract ResponseToEREEArgAlignmentRule first();

    @Value.Parameter
    public abstract ResponseToEREEArgAlignmentRule second();

    @Override
    public final boolean aligns(final Response r, final EREArgument ea) {
      return first().aligns(r, ea) && second().aligns(r, ea);
    }
  }

  @TextGroupPackageImmutable
  @Value.Immutable
  static abstract class _MappedRolesMatch implements ResponseToEREEArgAlignmentRule {

    @Value.Parameter
    public abstract EREToKBPEventOntologyMapper ontologyMapper();

    @Override
    public final boolean aligns(final Response r, final EREArgument ea) {
      final Optional<Symbol> role = ontologyMapper().eventRole(Symbol.from(ea.getRole()));
      return role.isPresent() && role.get().equals(r.role());
    }
  }
}
