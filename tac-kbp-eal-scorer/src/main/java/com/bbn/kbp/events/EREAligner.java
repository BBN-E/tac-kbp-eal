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

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Aligns a TAC KBP {@link Response} to appropriate ERE types by offset matching. Offset matching
 * may be relaxed in two ways: <ul> <li>By fuzzier offset matching (e.g. allowing containment or
 * overlap)</li> <li>By finding the head of the KBP {@link Response} and aligning with the ERE
 * head (if any)</li> </ul>
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
        buildRangeToFillerMap(ereDoc, spanExtractor);
    this.exactRangeToEntityMentionHead =
        buildRangeToFillerMap(ereDoc, headExtractor);
    this.exactHeadRange =
        ImmutableOverlappingRangeSet.create(exactRangeToEntityMentionHead.keySet());
    this.coreNLPDoc = coreNLPDocument;
    this.rangeToFiller = buildRangeToFillerMap(ereDoc);
    checkState(!relaxUsingCORENLP || coreNLPDoc.isPresent(),
        "Either we have our CoreNLPDocument or we are not relaxing using it");
  }

  static EREAligner create(final boolean relaxUsingCORENLP,
      final boolean useExactMatchForCoreNLPRelaxation,
      final EREDocument ereDoc, final Optional<CoreNLPDocument> coreNLPDocument) {
    return new EREAligner(relaxUsingCORENLP, useExactMatchForCoreNLPRelaxation, ereDoc,
        coreNLPDocument);
  }


  ImmutableSet<EREEntity> entitiesForResponse(final Response response) {
    return getCandidateEntitiesFromMentions(ereDoc, mentionsForResponse(response));
  }

  ImmutableSet<EREEntityMention> mentionsForResponse(final Response response) {
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
          candidateMentionsB.addAll(MultimapUtils.getAllAsSet(exactRangeToEntityMentionHead,
              exactHeadRange.rangesContaining(coreNLPHeadRange.get())));
          // add the candidate entity mentions whose heads are contained in the CoreNLPHead
          candidateMentionsB.addAll(MultimapUtils.getAllAsSet(exactRangeToEntityMentionHead,
              exactHeadRange.rangesContainedBy(coreNLPHeadRange.get())));
        }
      }
    }

    return candidateMentionsB.build();
  }

  ImmutableSet<EREArgument> eventMentionArgsForResponse(final Response response) {
    final ImmutableSet.Builder<EREArgument> ret = ImmutableSet.builder();
    for (final EREEvent e : ereDoc.getEvents()) {
      for (final EREEventMention em : e.getEventMentions()) {
        // TODO throwaway generics here?
        // TODO check event type, subtype here
        for (final EREArgument ea : em.getArguments()) {
          // TODO check event argument role here
          Optional<ERESpan> head = Optional.absent();
          ERESpan extent;
          if (ea instanceof EREFillerArgument) {
            extent = ((EREFillerArgument) ea).filler().getExtent();
          } else if (ea instanceof EREEntityArgument) {
            head = ((EREEntityArgument) ea).entityMention().getHead();
            extent = ((EREEntityArgument) ea).entityMention().getExtent();
          } else {
            throw new RuntimeException("Unknown EREArgument type " + ea.getClass());
          }

          if (spanMatches(extent, response.baseFiller()) || (head.isPresent() && spanMatches(
              head.get(), response.baseFiller()))) {
            ret.add(ea);
          }
        }
      }
    }
    return ret.build();
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

  private static ImmutableMultimap<Range<CharOffset>, EREEntityMention> buildRangeToFillerMap(
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

  private static ImmutableMultimap<Range<CharOffset>, EREFiller> buildRangeToFillerMap(
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

}
