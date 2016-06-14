package com.bbn.kbp.events;

import com.bbn.bue.common.TextGroupPackageImmutable;
import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events2014.CASType;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TACKBPEALException;
import com.bbn.nlp.corenlp.CoreNLPDocument;
import com.bbn.nlp.corenlp.CoreNLPParseNode;
import com.bbn.nlp.corenlp.CoreNLPSentence;
import com.bbn.nlp.corpora.ere.EREArgument;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.EREEntity;
import com.bbn.nlp.corpora.ere.EREEntityArgument;
import com.bbn.nlp.corpora.ere.EREEntityMention;
import com.bbn.nlp.corpora.ere.EREEvent;
import com.bbn.nlp.corpora.ere.EREEventMention;
import com.bbn.nlp.corpora.ere.EREFiller;
import com.bbn.nlp.corpora.ere.EREFillerArgument;
import com.bbn.nlp.corpora.ere.ERESpan;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import org.immutables.func.Functional;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import static com.bbn.kbp.events.CandidateAlignmentTargetFunctions.offsets;
import static com.bbn.kbp.events.CandidateAlignmentTargetFunctions.typeRolesSeen;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.compose;

/**
 * Aligns a TAC KBP {@link Response} to ERE entities or fillers by offset matching. Offset matching
 * may be relaxed in two ways: <ul> <li>By fuzzier offset matching (e.g. allowing containment or
 * overlap)</li> <li>By finding the head of the KBP {@link Response} and aligning with the ERE head
 * (if any)</li> </ul>
 */
final class EREAligner {

  private static final Logger log = LoggerFactory.getLogger(EREAligner.class);

  private final ImmutableList<ResponseToEREAlignmentRule> responseMatchingStrategy;
  private final ImmutableSet<CandidateAlignmentTarget> candidateEREObjects;

  private EREAligner(
      final Iterable<CandidateAlignmentTarget> candidateEREObjects,
      ImmutableList<ResponseToEREAlignmentRule> responseMatchingStrategy) {
    this.candidateEREObjects = ImmutableSet.copyOf(candidateEREObjects);
    this.responseMatchingStrategy = responseMatchingStrategy;
  }

  static EREAligner create(final boolean relaxUsingCORENLP,
      final EREDocument ereDoc, final Optional<CoreNLPDocument> coreNLPDocument,
      final EREToKBPEventOntologyMapper mapping) {
    checkArgument(!relaxUsingCORENLP || coreNLPDocument.isPresent(), "CoreNLP relaxation "
        + "requested but no CoreNLP document provided for " + ereDoc.getDocId());
    return new EREAligner(
        // first we need to collect the correct entities and fillers
        // A CandidateAlignmentTarget abstracts over the difference between the two
        gatherCandidateEREObjects(ereDoc, mapping),
        // build the list of rules we will use to match system responses
        createResponseMatchingStrategy(coreNLPDocument));
  }


  Optional<ScoringCorefID> argumentForResponse(final Response response) {
    final MappedEventTypeRole systemTypeRole = typeRoleForResponse(response);
    final ImmutableSet<CandidateAlignmentTarget> searchOrder =
        FluentIterable.from(candidateEREObjects)
            // alignment candidates which match the response in event type
            // and argument role will always be searched before other candidates
            // in order to be as generous as possible to systems.
            // Note ImmutableSets have a deterministic iteration order
            .filter(compose(containsElement(systemTypeRole), typeRolesSeen()))
            .append(candidateEREObjects)
            .toSet();

    // for each alignment rule in order, try to find an ERE object which aligns
    for (final ResponseToEREAlignmentRule responseChecker : responseMatchingStrategy) {
      for (final CandidateAlignmentTarget alignmentCandidate : searchOrder) {
        if (responseChecker.aligns(response, alignmentCandidate)) {
          return Optional.of(alignmentCandidate.id());
        }
      }
    }

    return Optional.absent();
  }

  private static MappedEventTypeRole typeRoleForResponse(final Response response) {
    return MappedEventTypeRole.of(
        response.type(), response.role());
  }


  // build the list of alignment rules which will be applied in order until one matches
  private static ImmutableList<ResponseToEREAlignmentRule> createResponseMatchingStrategy(
      Optional<CoreNLPDocument> coreNLPDoc) {
    final ImmutableList.Builder<ResponseToEREAlignmentRule> ret = ImmutableList.builder();

    final Function<Response, OffsetRange<CharOffset>> responseExtractor = CasExtractor.INSTANCE;
    final Function<Response, OffsetRange<CharOffset>> responseHeadExtractor;
    // if a CoreNLP analysis is provided we will use it to find the heads of response spans
    if (coreNLPDoc.isPresent()) {
      responseHeadExtractor = CoreNLPHeadExtractor.of(coreNLPDoc.get(), responseExtractor);
    } else {
      responseHeadExtractor = responseExtractor;
    }

    // these are atoms out of which more complex rules will be built
    final SpansMatchExactly responseMatchesEREExtentExactly =
        new SpansMatchExactly(responseExtractor, offsets());
    final SpansMatchExactly responseHeadMatchesEREExtentExactly =
        new SpansMatchExactly(responseHeadExtractor, offsets());
    final MappedRolesMatch mappedRolesMatch = MappedRolesMatch.builder().build();
    final SpansMatchExactly responseHeadMatchesEREHeadExactly =
        new SpansMatchExactly(responseHeadExtractor, ereHeadIfPresent);
    final SpansMatchExactly responseMatchesEREHeadExactly =
        new SpansMatchExactly(responseExtractor, ereHeadIfPresent);
    final ContainmentSpanChecker alignByContainment =
        new ContainmentSpanChecker(responseExtractor, responseHeadExtractor);

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
    return ret.build();
  }

  private static ImmutableSet<CandidateAlignmentTarget> gatherCandidateEREObjects(
      final EREDocument ereDoc,
      EREToKBPEventOntologyMapper ontologyMapper) {
    final ImmutableSet.Builder<CandidateAlignmentTarget> ret = ImmutableSet.builder();

    final ImmutableMultimap.Builder<EREEntity, MappedEventTypeRole> entitiesToRolesPlayed =
        ImmutableMultimap.builder();
    final ImmutableMultimap.Builder<EREFiller, MappedEventTypeRole> fillersToRolesPlayed =
        ImmutableMultimap.builder();

    for (final EREEvent ereEvent : ereDoc.getEvents()) {
      for (final EREEventMention ereEventMention : ereEvent.getEventMentions()) {
        final Optional<Symbol> mappedEventType = ontologyMapper.eventType(
            Symbol.from(ereEventMention.getType()));
        final Optional<Symbol> mappedEventSubType = ontologyMapper.eventSubtype(
            Symbol.from(ereEventMention.getSubtype()));
        if (mappedEventType.isPresent() && mappedEventSubType.isPresent()) {
          // there may be events in the ERE outside our ontology. This is okay.
          for (final EREArgument ereArgument : ereEventMention.getArguments()) {
            final Optional<Symbol> mappedRole =
                ontologyMapper.eventRole(Symbol.from(ereArgument.getRole()));
            if (mappedRole.isPresent()) {
              // there may be roles outside our ontology. This is also okay.
              final MappedEventTypeRole mappedTypeRole = MappedEventTypeRole.of(
                  Symbol.from(mappedEventType.get().asString()
                      + "." + mappedEventSubType.get().asString()),
                  mappedRole.get());
              if (ereArgument instanceof EREEntityArgument) {
                final Optional<EREEntity> containingEntity =
                    ereDoc.getEntityContaining(((EREEntityArgument) ereArgument).entityMention());
                if (containingEntity.isPresent()) {
                  entitiesToRolesPlayed.put(containingEntity.get(), mappedTypeRole);
                } else {
                  throw new TACKBPEALException("Entity filler without entity: " + ereArgument);
                }
              } else if (ereArgument instanceof EREFillerArgument) {
                fillersToRolesPlayed
                    .put(((EREFillerArgument) ereArgument).filler(), mappedTypeRole);
              } else {
                throw new TACKBPEALException(
                    "Unknown ERE event argument type " + ereArgument.getClass());
              }
            }
          }
        }
      }
    }

    for (final Map.Entry<EREEntity, Collection<MappedEventTypeRole>> e :
        entitiesToRolesPlayed.build().asMap().entrySet()) {
      final EREEntity entity = e.getKey();
      final Collection<MappedEventTypeRole> rolesPlayed = e.getValue();

      CASType bestCASType = CASType.PRONOUN;
      for (final EREEntityMention ereEntityMention : entity.getMentions()) {
        bestCASType = Ordering.natural().max(bestCASType,
            CASType.parseFromERE(ereEntityMention.getType()));
      }

      for (final EREEntityMention ereEntityMention : entity.getMentions()) {
        final CASType casType = CASType.parseFromERE(ereEntityMention.getType());
        // if this is e.g. a nominal when a name is available as an entity, we use
        // a special entity type to ensure this is counted wrong.
        final ScoringEntityType scoringEntityType = (casType.equals(bestCASType))
                                                    ? ScoringEntityType.Entity
                                                    : ScoringEntityType.InsufficientEntityLevel;

        final CandidateAlignmentTarget.Builder builder = CandidateAlignmentTarget.builder()
            .id(ScoringCorefID.of(scoringEntityType, entity.getID()))
            .typeRolesSeen(rolesPlayed)
            .offsets(extentForERE(ereEntityMention.getExtent()));
        if (ereEntityMention.getHead().isPresent()) {
          builder.headOffsets(extentForERE(ereEntityMention.getHead().get()));
        }
        ret.add(builder.build());
      }
    }

    for (final Map.Entry<EREFiller, Collection<MappedEventTypeRole>> e : fillersToRolesPlayed
        .build().asMap().entrySet()) {
      final EREFiller filler = e.getKey();
      final Collection<MappedEventTypeRole> rolesPlayed = e.getValue();

      ret.add(CandidateAlignmentTarget.builder()
          .id(ScoringCorefID.of(ScoringEntityType.Filler, filler.getID()))
          .typeRolesSeen(rolesPlayed)
          .offsets(extentForERE(filler.getExtent()))
          .build());
    }

    return ret.build();
  }

  private static OffsetRange<CharOffset> extentForERE(ERESpan span) {
    return OffsetRange.charOffsetRange(span.getStart(), span.getEnd());
  }

  // falling back to the extent won't harm us since we'll always run this after the ereExtentExtractor
  private static final Function<CandidateAlignmentTarget, OffsetRange<CharOffset>>
      ereHeadIfPresent =
      new Function<CandidateAlignmentTarget, OffsetRange<CharOffset>>() {
        @Override
        public OffsetRange<CharOffset> apply(final CandidateAlignmentTarget candidate) {
          checkNotNull(candidate);
          return candidate.headOffsets().or(candidate.offsets());
        }
      };

  private enum CasExtractor implements Function<Response, OffsetRange<CharOffset>> {
    INSTANCE;

    @Override
    public OffsetRange<CharOffset> apply(@Nullable final Response response) {
      return checkNotNull(response).canonicalArgument().charOffsetSpan().asCharOffsetRange();
    }
  }

  @TextGroupPackageImmutable
  @Value.Immutable
  static abstract class _CoreNLPHeadExtractor
      implements Function<Response, OffsetRange<CharOffset>> {

    @Value.Parameter
    public abstract CoreNLPDocument coreNLPDoc();

    @Value.Parameter
    public abstract Function<Response, OffsetRange<CharOffset>> rangeFinder();

    public OffsetRange<CharOffset> apply(final Response response) {
      final OffsetRange<CharOffset> off = checkNotNull(rangeFinder().apply(response));
      final Optional<CoreNLPSentence> sent = coreNLPDoc().firstSentenceContaining(off);
      if (sent.isPresent()) {
        final Optional<CoreNLPParseNode> node = sent.get().nodeForOffsets(off);
        if (node.isPresent()) {
          final Optional<CoreNLPParseNode> terminalHead = node.get().terminalHead();
          if (terminalHead.isPresent()) {
            return terminalHead.get().span();
          }
        }
      }
      return off;
    }
  }

  interface ResponseToEREAlignmentRule {

    boolean aligns(Response r, CandidateAlignmentTarget candidateAlignment);
  }

  private static abstract class SpanChecker implements ResponseToEREAlignmentRule {

    protected final Function<Response, OffsetRange<CharOffset>> responseSpanExtractor;
    protected final Function<? super CandidateAlignmentTarget, OffsetRange<CharOffset>>
        ereArgSpanExtractor;

    protected SpanChecker(final Function<Response, OffsetRange<CharOffset>> responseSpanExtractor,
        final Function<? super CandidateAlignmentTarget, OffsetRange<CharOffset>> ereArgSpanExtractor) {
      this.responseSpanExtractor = checkNotNull(responseSpanExtractor);
      this.ereArgSpanExtractor = checkNotNull(ereArgSpanExtractor);
    }
  }

  private static class SpansMatchExactly extends SpanChecker {

    protected SpansMatchExactly(
        final Function<Response, OffsetRange<CharOffset>> responseSpanExtractor,
        final Function<? super CandidateAlignmentTarget, OffsetRange<CharOffset>> ereArgSpanExtractor) {
      super(responseSpanExtractor, ereArgSpanExtractor);
    }

    @Override
    public boolean aligns(final Response r, final CandidateAlignmentTarget candidate) {
      return checkNotNull(responseSpanExtractor.apply(r))
          .equals(ereArgSpanExtractor.apply(candidate));
    }
  }

  private static final class ContainmentSpanChecker implements
      ResponseToEREAlignmentRule {

    protected final Function<Response, OffsetRange<CharOffset>> responseSpanExtractor;
    protected final Function<Response, OffsetRange<CharOffset>> responseHeadExtractor;

    private ContainmentSpanChecker(
        final Function<Response, OffsetRange<CharOffset>> responseSpanExtractor,
        final Function<Response, OffsetRange<CharOffset>> responseHeadExtractor) {
      this.responseSpanExtractor = responseSpanExtractor;
      this.responseHeadExtractor = responseHeadExtractor;
    }

    @Override
    public boolean aligns(final Response r, final CandidateAlignmentTarget ea) {
      final OffsetRange<CharOffset> responseOffsets = checkNotNull(responseSpanExtractor.apply(r));
      final OffsetRange<CharOffset> responseHead = responseHeadExtractor.apply(r);

      // fall back to using the whole ERE extent as a head if no head is specified
      final OffsetRange<CharOffset> candidateHead = ea.headOffsets().or(ea.offsets());
      // ereOffsets.encloses(ereHead) in case of annotation inconsistency
      if (ea.offsets().contains(responseOffsets)) {
        return (responseOffsets.contains(candidateHead) && responseOffsets.contains(responseHead))
            || (ea.offsets().contains(responseHead) && ea.offsets().contains(candidateHead));
      }
      return false;
    }
  }

  @TextGroupPackageImmutable
  @Value.Immutable
  static abstract class _And implements ResponseToEREAlignmentRule {

    @Value.Parameter
    public abstract ResponseToEREAlignmentRule first();

    @Value.Parameter
    public abstract ResponseToEREAlignmentRule second();

    @Override
    public final boolean aligns(final Response r, final CandidateAlignmentTarget ea) {
      return first().aligns(r, ea) && second().aligns(r, ea);
    }
  }

  @TextGroupPackageImmutable
  @Value.Immutable
  static abstract class _MappedRolesMatch implements ResponseToEREAlignmentRule {
    @Override
    public final boolean aligns(final Response r, final CandidateAlignmentTarget ea) {
      return ea.typeRolesSeen().contains(typeRoleForResponse(r));
    }
  }

  @MoveToBUECommon
  static <T> Predicate<Collection<T>> containsElement(final T element) {
    return new Predicate<Collection<T>>() {
      @Override
      public boolean apply(@Nullable final Collection<T> input) {
        return checkNotNull(input).contains(element);
      }
    };
  }
}

@Value.Immutable
@TextGroupPackageImmutable
abstract class _MappedEventTypeRole {

  @Value.Parameter
  abstract Symbol type();

  @Value.Parameter
  abstract Symbol role();

  @Value.Check
  protected void check() {
    checkArgument(!type().asString().isEmpty());
    checkArgument(!role().asString().isEmpty());
  }
}

@Value.Immutable
@TextGroupPackageImmutable
@Functional
abstract class _CandidateAlignmentTarget {

  abstract ScoringCorefID id();

  abstract ImmutableSet<MappedEventTypeRole> typeRolesSeen();

  abstract OffsetRange<CharOffset> offsets();

  abstract Optional<OffsetRange<CharOffset>> headOffsets();
}

enum AlignmentFailureCause {
  NoAlignment,
  InsufficientMentionLevel
}

@Value.Immutable
@TextGroupPackageImmutable
abstract class _EREAlignerReturn {

  abstract Optional<ScoringCorefID> scoringCorefID();

  abstract Optional<AlignmentFailureCause> failureCause();

  @Value.Check
  protected void check() {
    checkArgument(scoringCorefID().isPresent() != failureCause().isPresent());
  }
}
