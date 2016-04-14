package com.bbn.kbp.events;

import com.bbn.bue.common.collections.ImmutableOverlappingRangeSet;
import com.bbn.bue.common.collections.MultimapUtils;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.Response;
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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

import java.util.Objects;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jdeyoung on 4/14/16.
 */
final class EREAligner {

  private final boolean relaxUsingCORENLP;
  private final boolean useExactMatchForCoreNLPRelaxation;
  private final EREDocument ereDoc;
  private final ImmutableMultimap<Range<CharOffset>, EREEntityMention> exactRangeToEntityMention;
  private final ImmutableMultimap<Range<CharOffset>, EREEntityMention>
      exactRangeToEntityMentionHead;
  private final ImmutableOverlappingRangeSet<CharOffset> exactHeadRange;
  private final ImmutableMultimap<Range<CharOffset>, EREFiller> rangeToFiller;
  private final Optional<CoreNLPDocument> coreNLPDoc;

  private EREAligner(final boolean relaxUsingCORENLP,
      final boolean useExactMatchForCoreNLPRelaxation,
      final EREDocument ereDoc, final Optional<CoreNLPDocument> coreNLPDocument) {
    this.relaxUsingCORENLP = relaxUsingCORENLP;
    this.useExactMatchForCoreNLPRelaxation = useExactMatchForCoreNLPRelaxation;
    this.ereDoc = ereDoc;
    this.exactRangeToEntityMention =
        rangeToEntity(ereDoc, spanExtractor);
    this.exactRangeToEntityMentionHead =
        rangeToEntity(ereDoc, headExtractor);
    this.exactHeadRange =
        ImmutableOverlappingRangeSet.create(exactRangeToEntityMentionHead.keySet());
    this.coreNLPDoc = coreNLPDocument;
    this.rangeToFiller = rangeToFiller(ereDoc);
  }

  static EREAligner create(final boolean relaxUsingCORENLP,
      final boolean useExactMatchForCoreNLPRelaxation,
      final EREDocument ereDoc, final Optional<CoreNLPDocument> coreNLPDocument) {
    return new EREAligner(relaxUsingCORENLP, useExactMatchForCoreNLPRelaxation, ereDoc,
        coreNLPDocument);
  }


  ImmutableSet<EREEntity> entitiesForResponse(final Response response) {
    if (!coreNLPDoc.isPresent()) {
      return ImmutableSet.of();
    }
    // we try to align a system response to an ERE entity by exact offset match of the
    // basefiller against one of an entity's mentions
    // this search could be faster but is probably good enough

    final OffsetRange<CharOffset> baseFillerOffsets = response.baseFiller().asCharOffsetRange();
    // collect all the candidate mentions
    final ImmutableSet.Builder<EREEntityMention> candidateMentionsB = ImmutableSet.builder();
    // add the candidate mentions that align exactly to base filler offsets
    candidateMentionsB.addAll(exactRangeToEntityMention.get(baseFillerOffsets.asRange()));
    // add the candidate mentions whose head aligns exactly with the base filler offsets
    candidateMentionsB.addAll(exactRangeToEntityMentionHead.get(baseFillerOffsets.asRange()));
    if (relaxUsingCORENLP) {
      final Optional<Range<CharOffset>> coreNLPHeadRange = getCoreNLPHead(baseFillerOffsets);
      if (coreNLPHeadRange.isPresent()) {
        if (useExactMatchForCoreNLPRelaxation) {
          // add the candidate entity mentions whose heads exactly match the CoreNLPHead
          candidateMentionsB.addAll(exactRangeToEntityMentionHead.get(coreNLPHeadRange.get()));
        } else {
          // add the candidate entity mentions whose heads contain the CoreNLPHead
          candidateMentionsB.addAll(MultimapUtils.getAll(exactRangeToEntityMentionHead,
              exactHeadRange.rangesContaining(coreNLPHeadRange.get())));
          // add the candidate entity mentions whose heads are contained in the CoreNLPHead
          candidateMentionsB.addAll(MultimapUtils.getAll(exactRangeToEntityMentionHead,
              exactHeadRange.rangesContained(coreNLPHeadRange.get())));
        }
      }
    }

    final ImmutableSet<EREEntityMention> candidateMentions = candidateMentionsB.build();
    return getCandidateEntitiesFromMentions(ereDoc, candidateMentions);
  }

  private Optional<Range<CharOffset>> getCoreNLPHead(final OffsetRange<CharOffset> offsets) {
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

  private static ImmutableSet<EREEntity> getCandidateEntitiesFromMentions(final EREDocument doc,
      final ImmutableSet<EREEntityMention> candidateMentions) {
    final ImmutableSet.Builder<EREEntity> ret = ImmutableSet.builder();
    for (final EREEntityMention em : candidateMentions) {
      final Optional<EREEntity> e = doc.getEntityContaining(em);
      if (e.isPresent()) {
        ret.add(e.get());
      }
    }
    return ret.build();
  }

  private static ImmutableMultimap<Range<CharOffset>, EREEntityMention> rangeToEntity(
      final EREDocument ereDocument,
      Function<EREEntityMention, Optional<Range<CharOffset>>> extractor) {
    final ImmutableMultimap.Builder<Range<CharOffset>, EREEntityMention> ret =
        ImmutableMultimap.builder();
    for (final EREEntity e : ereDocument.getEntities()) {
      for (final EREEntityMention em : e.getMentions()) {
        final Optional<Range<CharOffset>> range = checkNotNull(extractor.apply(em));
        if (range.isPresent()) {
          ret.put(range.get(), em);
        }
      }
    }
    return ret.build();
  }

  private static ImmutableMultimap<Range<CharOffset>, EREFiller> rangeToFiller(
      final EREDocument ereDoc) {
    final ImmutableMultimap.Builder<Range<CharOffset>, EREFiller> ret =
        ImmutableMultimap.builder();
    for (final EREFiller f : ereDoc.getFillers()) {
      // TODO check for off-by-one error since we use [closed,closed] offsets
      final Range<CharOffset> r =
          CharOffsetSpan.fromOffsetsOnly(f.getExtent().getStart(), f.getExtent().getEnd())
              .asCharOffsetRange().asRange();
      ret.put(r, f);
    }
    return ret.build();
  }

  private static final Function<EREEntityMention, Optional<Range<CharOffset>>> spanExtractor =
      new Function<EREEntityMention, Optional<Range<CharOffset>>>() {
        @Nullable
        @Override
        public Optional<Range<CharOffset>> apply(
            @Nullable final EREEntityMention ereEntityMention) {
          checkNotNull(ereEntityMention);
          // TODO check for off-by-one error since we use [closed,closed] offsets
          return Optional
              .of(CharOffsetSpan.fromOffsetsOnly(ereEntityMention.getExtent().getStart(),
                  ereEntityMention.getExtent().getEnd()).asCharOffsetRange().asRange());
        }
      };

  private static final Function<EREEntityMention, Optional<Range<CharOffset>>> headExtractor =
      new Function<EREEntityMention, Optional<Range<CharOffset>>>() {
        @Nullable
        @Override
        public Optional<Range<CharOffset>> apply(
            @Nullable final EREEntityMention ereEntityMention) {
          checkNotNull(ereEntityMention);
          if (ereEntityMention.getHead().isPresent()) {
            // TODO check for off-by-one error since we use [closed,closed] offsets
            return Optional.of(CharOffsetSpan
                .fromOffsetsOnly(ereEntityMention.getHead().get().getStart(),
                    ereEntityMention.getHead().get().getEnd()).asCharOffsetRange().asRange());
          } else {
            return Optional.absent();
          }
        }
      };

  private boolean spanMatches(final ERESpan es, final CharOffsetSpan cs) {
    // TODO check for off-by-one error since we use [closed,closed] offsets
    final Range<CharOffset> esRange =
        CharOffsetSpan.fromOffsetsOnly(es.getStart(), es.getEnd()).asCharOffsetRange().asRange();
    final Range<CharOffset> csRange = cs.asCharOffsetRange().asRange();
    if (esRange.equals(csRange)) {
      return true;
    }
    if (!useExactMatchForCoreNLPRelaxation && esRange.encloses(csRange)) {
      return true;
    }
    if (relaxUsingCORENLP) {
      final Optional<Range<CharOffset>> headRange = getCoreNLPHead(cs.asCharOffsetRange());
      if (headRange.isPresent()) {
        if (esRange.equals(headRange.get())) {
          return true;
        }
        // TODO do we want this second .encloses?
        if (!useExactMatchForCoreNLPRelaxation && (esRange.encloses(headRange.get()) || headRange
            .get().encloses(esRange))) {
          return true;
        }
      }
    }
    return false;
  }

  ImmutableSet<ResolvedEREEventMentionArg> argsForResponse(final Response r) {
    final ImmutableSet.Builder<ResolvedEREEventMentionArg> ret = ImmutableSet.builder();
    // TODO what to do about generic events? This level handling probably doesn't belong here...
    // TODO what to do about mismatched response and EREArgs?
    for (final EREEvent ev : ereDoc.getEvents()) {
      for (final EREEventMention evm : ev.getEventMentions()) {
        // TODO check type
        for (final EREArgument arg : evm.getArguments()) {
          EREFiller f = null;
          EREEntityMention em = null;
          final Optional<ERESpan> head;
          final ERESpan extent;
          if (arg instanceof EREFillerArgument) {
            f = ((EREFillerArgument) arg).filler();
            head = Optional.of(f.getExtent());
            extent = f.getExtent();
          } else if (arg instanceof EREEntityArgument) {
            em = ((EREEntityArgument) arg).entityMention();
            head = em.getHead();
            extent = em.getExtent();
          } else {
            throw new RuntimeException("Unknown ERE arg type " + arg.getClass());
          }

          if (spanMatches(extent, r.baseFiller()) || (head.isPresent() && spanMatches(head.get(),
              r.baseFiller()))) {
            ret.add(ResolvedEREEventMentionArg
                .create(evm, Optional.fromNullable(f), Optional.fromNullable(em)));
          }
        }
      }
    }
    return ret.build();
  }

}

final class ResolvedEREEventMentionArg {

  final EREEventMention ereEventMention;
  final Optional<EREFiller> ereFiller;
  final Optional<EREEntityMention> entityMention;

  private ResolvedEREEventMentionArg(final EREEventMention ereEventMention,
      final Optional<EREFiller> ereFiller, final Optional<EREEntityMention> entityMention) {
    this.ereEventMention = checkNotNull(ereEventMention);
    this.ereFiller = checkNotNull(ereFiller);
    this.entityMention = checkNotNull(entityMention);
    checkArgument(ereFiller.isPresent() || entityMention.isPresent(),
        "Either a filler or an entity mention must be present!");
  }

  static ResolvedEREEventMentionArg create(final EREEventMention ereEventMention,
      final Optional<EREFiller> ereFiller, final Optional<EREEntityMention> entityMention) {
    return new ResolvedEREEventMentionArg(ereEventMention, ereFiller, entityMention);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ResolvedEREEventMentionArg that = (ResolvedEREEventMentionArg) o;
    return Objects.equals(ereEventMention, that.ereEventMention) &&
        Objects.equals(ereFiller, that.ereFiller) &&
        Objects.equals(entityMention, that.entityMention);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ereEventMention, ereFiller, entityMention);
  }
}
