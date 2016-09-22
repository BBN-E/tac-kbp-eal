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
import com.google.common.base.Functions;
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
 * (if any)</li>. See {@link #createResponseMatchingStrategy(Optional)} for a full
 * description of the match criteria</ul>
 */
public final class EREAligner {

  private static final Logger log = LoggerFactory.getLogger(EREAligner.class);

  // the set of rules used to determine if a system response matches an ERE object
  private final ImmutableList<ResponseToEREAlignmentRule> responseMatchingStrategy;
  // the ERE objects which could be aligned to.
  private final ImmutableSet<CandidateAlignmentTarget> candidateEREObjects;

  private EREAligner(
      final Iterable<CandidateAlignmentTarget> candidateEREObjects,
      ImmutableList<ResponseToEREAlignmentRule> responseMatchingStrategy) {
    this.candidateEREObjects = ImmutableSet.copyOf(candidateEREObjects);
    this.responseMatchingStrategy = responseMatchingStrategy;
  }

  public static EREAligner create(final EREDocument ereDoc,
      final Optional<CoreNLPDocument> coreNLPDocument,
      final EREToKBPEventOntologyMapper mapping) {
    return new EREAligner(
        // first we need to collect the correct entities and fillers
        // A CandidateAlignmentTarget abstracts over the difference between the two
        gatherCandidateEREObjects(ereDoc, mapping),
        // build the list of rules we will use to match system responses
        createResponseMatchingStrategy(coreNLPDocument));
  }


  private static final Symbol TIME = Symbol.from("Time");
  /**
   * Gets an identifier corresponding to the ERE object the response aligns to, if any.
   */
  public Optional<ScoringCorefID> argumentForResponse(final Response response) {
    if (response.role().equalTo(TIME)) {
      // time is a special case; its CAS is always its TIMEX form
      return Optional.of(new ScoringCorefID.Builder().scoringEntityType(ScoringEntityType.Time)
          .withinTypeID(response.canonicalArgument().string()).build());
    }

    final MappedEventTypeRole systemTypeRole = typeRoleForResponse(response);
    final ImmutableSet<CandidateAlignmentTarget> searchOrder =
        FluentIterable.from(candidateEREObjects)
            // alignment candidates which match the response in event type
            // and argument role will always be searched before other candidates
            // in order to be as generous as possible to systems.
            // Note ImmutableSets iterate deterministically in the order of insertion
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

  // build the list of alignment rules which will be applied in order until one matches
  private static ImmutableList<ResponseToEREAlignmentRule> createResponseMatchingStrategy(
      Optional<CoreNLPDocument> coreNLPDoc) {
    final ImmutableList.Builder<ResponseToEREAlignmentRule> ret = ImmutableList.builder();

    // if a CoreNLP analysis is available, we can use it to compute heads for both system and ERE
    // objects.
    final Function<Response, OffsetRange<CharOffset>> responseComputedHead;
    final Function<CandidateAlignmentTarget, OffsetRange<CharOffset>> ereComputedHead;
    if (coreNLPDoc.isPresent()) {
      final CoreNLPHeadExtractor coreNLPHeadExtractor = CoreNLPHeadExtractor.of(coreNLPDoc.get());
      responseComputedHead = Functions.compose(coreNLPHeadExtractor, CasExtractor.INSTANCE);
      ereComputedHead = Functions.compose(coreNLPHeadExtractor, ERE_HEAD_IF_DECLARED);
    } else {
      responseComputedHead = CasExtractor.INSTANCE;
      ereComputedHead = ERE_HEAD_IF_DECLARED;
    }

    ret.addAll(ImmutableList.of(
        // response CAS matches ERE extent exactly
        new SpansMatchExactly(CasExtractor.INSTANCE, offsets()),
        // response CAS matches ERE declared head exactly
        new SpansMatchExactly(CasExtractor.INSTANCE, ERE_HEAD_IF_DECLARED),
        // response CAS matches ERE computed head exactly
        new SpansMatchExactly(CasExtractor.INSTANCE, ereComputedHead),
        // response CAS head matches ERE extent exactly
        new SpansMatchExactly(responseComputedHead, offsets()),
        // response CAS head matches ERE declared head exactly
        new SpansMatchExactly(responseComputedHead, ERE_HEAD_IF_DECLARED),
        // response CAS head matches ERE computed head exactly
        new SpansMatchExactly(responseComputedHead, ereComputedHead),
        // finally we do a more aggressive alignment attempt by containment
        new ContainmentSpanChecker(CasExtractor.INSTANCE, responseComputedHead)
    ));
    return ret.build();
  }

  private static ImmutableSet<CandidateAlignmentTarget> gatherCandidateEREObjects(
      final EREDocument ereDoc,
      EREToKBPEventOntologyMapper ontologyMapper) {
    final ImmutableSet.Builder<CandidateAlignmentTarget> ret = ImmutableSet.builder();

    // because we prioritize matches against entities which appear with "desirable" type
    // and role, we need to find out what events all entities and fillers are involved with
    final ImmutableMultimap.Builder<EREEntity, MappedEventTypeRole> entitiesToRolesPlayedB =
        ImmutableMultimap.builder();
    final ImmutableMultimap.Builder<EREFiller, MappedEventTypeRole> fillersToRolesPlayedB =
        ImmutableMultimap.builder();

    for (final EREEvent ereEvent : ereDoc.getEvents()) {
      for (final EREEventMention ereEventMention : ereEvent.getEventMentions()) {
        final Optional<Symbol> mappedFullEventType = ScoringUtils.mapERETypesToDotSeparated(
            ontologyMapper, ereEventMention);

        if (!mappedFullEventType.isPresent()) {
          continue;
        }

        // there may be events in the ERE outside our ontology. This is okay.
        for (final EREArgument ereArgument : ereEventMention.getArguments()) {
          final Optional<Symbol> mappedRole =
              ontologyMapper.eventRole(Symbol.from(ereArgument.getRole()));
          if (!mappedRole.isPresent()) {
            continue;
          }

          // there may be roles outside our ontology. This is also okay.
          final MappedEventTypeRole mappedTypeRole = MappedEventTypeRole.of(
              mappedFullEventType.get(), mappedRole.get());
          if (ereArgument instanceof EREEntityArgument) {
            final Optional<EREEntity> containingEntity =
                ereDoc.getEntityContaining(((EREEntityArgument) ereArgument).entityMention());
            if (containingEntity.isPresent()) {
              entitiesToRolesPlayedB.put(containingEntity.get(), mappedTypeRole);
            } else {
              throw new TACKBPEALException("Entity filler without entity: " + ereArgument);
            }
          } else if (ereArgument instanceof EREFillerArgument) {
            fillersToRolesPlayedB
                .put(((EREFillerArgument) ereArgument).filler(), mappedTypeRole);
          } else {
            throw new TACKBPEALException(
                "Unknown ERE event argument type " + ereArgument.getClass());
          }
        }
      }
    }

    final ImmutableMultimap<EREEntity, MappedEventTypeRole> entitiesToRolesPlayed =
        entitiesToRolesPlayedB.build();

    // crucially, any entity and filler can be aligned with, not just those
    // involved in events
    for (final EREEntity entity : ereDoc.getEntities()) {
      final Collection<MappedEventTypeRole> rolesPlayed = entitiesToRolesPlayed.get(entity);

      final CASType bestCASType = getBestCASType(entity);

      for (final EREEntityMention ereEntityMention : entity.getMentions()) {
        final CASType casType = CASType.parseFromERE(ereEntityMention.getType());
        // if this is e.g. a nominal when a name is available as an entity, we use
        // a special entity type to ensure this is always counted wrong.
        final ScoringEntityType scoringEntityType = (casType.equals(bestCASType))
                                                    ? ScoringEntityType.Entity
                                                    : ScoringEntityType.InsufficientEntityLevel;

        final CandidateAlignmentTarget.Builder builder = CandidateAlignmentTarget.builder()
            .id(new ScoringCorefID.Builder().scoringEntityType(scoringEntityType)
                .withinTypeID(entity.getID()).build())
            .typeRolesSeen(rolesPlayed)
            .offsets(extentForERE(ereEntityMention.getExtent()));
        if (ereEntityMention.getHead().isPresent()) {
          builder.headOffsets(extentForERE(ereEntityMention.getHead().get()));
        }
        ret.add(builder.build());
      }
    }

    final ImmutableMultimap<EREFiller, MappedEventTypeRole> fillersToRolesPlayed =
        fillersToRolesPlayedB.build();
    for (final EREFiller filler : ereDoc.getFillers()) {
      final Collection<MappedEventTypeRole> rolesPlayed = fillersToRolesPlayed.get(filler);

      ret.add(CandidateAlignmentTarget.builder()
          .id(new ScoringCorefID.Builder().scoringEntityType(ScoringEntityType.Filler)
              .withinTypeID(filler.getID()).build())
          .typeRolesSeen(rolesPlayed)
          .offsets(extentForERE(filler.getExtent()))
          .build());
    }

    return ret.build();
  }

  public static CASType getBestCASType(final EREEntity entity) {
    CASType bestCASType = CASType.PRONOUN;
    for (final EREEntityMention ereEntityMention : entity.getMentions()) {
      bestCASType = Ordering.natural().max(bestCASType,
          CASType.parseFromERE(ereEntityMention.getType()));
    }
    return bestCASType;
  }

  private static OffsetRange<CharOffset> extentForERE(ERESpan span) {
    return OffsetRange.charOffsetRange(span.getStart(), span.getEnd());
  }

  private static MappedEventTypeRole typeRoleForResponse(final Response response) {
    return MappedEventTypeRole.of(
        response.type(), response.role());
  }

  // falling back to the extent won't harm us since we'll always run this after the ereExtentExtractor
  private static final Function<CandidateAlignmentTarget, OffsetRange<CharOffset>>
      ERE_HEAD_IF_DECLARED =
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
      implements Function<OffsetRange<CharOffset>, OffsetRange<CharOffset>> {

    @Value.Parameter
    public abstract CoreNLPDocument coreNLPDoc();

    public OffsetRange<CharOffset> apply(final OffsetRange<CharOffset> off) {
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

    @Override
    public String toString() {
      return "SpansMatchExactly(" + responseSpanExtractor + ", " + ereArgSpanExtractor + ")";
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

    @Override
    public String toString() {
      return "ContainmentChecker(" + responseSpanExtractor + "," + responseHeadExtractor + ")";
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

  @Override
  public String toString() {
    return type().asString() + "/" + role().asString();
  }
}

@Value.Immutable
@TextGroupPackageImmutable
@Functional
abstract class _CandidateAlignmentTarget {

  abstract ImmutableSet<MappedEventTypeRole> typeRolesSeen();
  abstract ScoringCorefID id();
  abstract OffsetRange<CharOffset> offsets();
  abstract Optional<OffsetRange<CharOffset>> headOffsets();
}
