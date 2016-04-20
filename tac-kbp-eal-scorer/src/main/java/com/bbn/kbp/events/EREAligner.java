package com.bbn.kbp.events;

import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;
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
  private final EREToKBPEventOntologyMapper mapping;

  private EREAligner(final boolean relaxUsingCORENLP,
      final boolean useExactMatchForCoreNLPRelaxation,
      final EREDocument ereDoc, final Optional<CoreNLPDocument> coreNLPDocument,
      final EREToKBPEventOntologyMapper mapping) {
    this.relaxUsingCORENLP = relaxUsingCORENLP;
    this.useExactMatchForCoreNLPRelaxation = useExactMatchForCoreNLPRelaxation;
    this.ereDoc = ereDoc;
    this.coreNLPDoc = coreNLPDocument;
    this.mapping = mapping;
    checkState(!relaxUsingCORENLP || coreNLPDoc.isPresent(),
        "Either we have our CoreNLPDocument or we are not relaxing using it");
  }

  static EREAligner create(final boolean relaxUsingCORENLP,
      final boolean useExactMatchForCoreNLPRelaxation,
      final EREDocument ereDoc, final Optional<CoreNLPDocument> coreNLPDocument,
      final EREToKBPEventOntologyMapper mapping) {
    return new EREAligner(relaxUsingCORENLP, useExactMatchForCoreNLPRelaxation, ereDoc,
        coreNLPDocument, mapping);
  }


  Optional<ScoringCorefID> argumentForResponse(final Response response) {
    // first with CAS, then with BF
    final ImmutableList<Function<Response, CharOffsetSpan>> responseSpanFunctions =
        ImmutableList.of(casExtractor, baseFillerExtractor);
    final EREMatcher matcher = new EREMatcher();
    for (final Function<Response, CharOffsetSpan> responseExtractor : responseSpanFunctions) {
      final Function<Response, CharOffsetSpan> responseHeadExtractor =
          coreNLPHeadExtractorFromResultOrFallback(responseExtractor);

      final ImmutableList<MentionResponseChecker> responseCheckers = ImmutableList.of(
          new ComposingRoleBasedChecker(
              new ExactSpanChecker(responseExtractor, ereExtentExtractor),
              mapping),
          new ComposingRoleBasedChecker(
              new ExactSpanChecker(responseHeadExtractor, ereHeadExtractorFallingBackToExtent),
              mapping),
          new ComposingRoleBasedChecker(
              new ExactSpanChecker(responseHeadExtractor, ereExtentExtractor), mapping),
          new ComposingRoleBasedChecker(
              new ExactSpanChecker(responseExtractor, ereHeadExtractorFallingBackToExtent),
              mapping),
          new ExactSpanChecker(responseExtractor, ereExtentExtractor),
          new ExactSpanChecker(responseHeadExtractor, ereHeadExtractorFallingBackToExtent),
          new ExactSpanChecker(responseHeadExtractor, ereExtentExtractor),
          new ExactSpanChecker(responseExtractor, ereHeadExtractorFallingBackToExtent),
          new ComposingRoleBasedChecker(
              new ContainmentSpanChecker(responseExtractor, responseHeadExtractor,
                  ereExtentExtractor,
                  ereHeadExtractorFallingBackToExtent), mapping),
          new ContainmentSpanChecker(responseExtractor, responseHeadExtractor, ereExtentExtractor,
              ereHeadExtractorFallingBackToExtent)
      );

      for (final MentionResponseChecker responseChecker : responseCheckers) {
        final Optional<ScoringCorefID> found = matcher.aligns(response, responseChecker);
        if (found.isPresent()) {
          return found;
        }
      }
    }

    return Optional.absent();
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
  private static final Function<EREArgument, CharOffsetSpan> ereHeadExtractorFallingBackToExtent =
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

  private static final Function<Response, CharOffsetSpan> casExtractor =
      new Function<Response, CharOffsetSpan>() {
        @Nullable
        @Override
        public CharOffsetSpan apply(@Nullable final Response response) {
          return checkNotNull(response).canonicalArgument().charOffsetSpan();
        }
      };

  private static final Function<Response, CharOffsetSpan> baseFillerExtractor =
      new Function<Response, CharOffsetSpan>() {
        @Nullable
        @Override
        public CharOffsetSpan apply(@Nullable final Response response) {
          return checkNotNull(response).baseFiller();
        }
      };

  private Function<Response, CharOffsetSpan> coreNLPHeadExtractorFromResultOrFallback(
      final Function<Response, CharOffsetSpan> rangeFinder) {
    return new Function<Response, CharOffsetSpan>() {
      @Nullable
      @Override
      public CharOffsetSpan apply(@Nullable final Response response) {
        final CharOffsetSpan off = checkNotNull(rangeFinder.apply(response));
        if (!coreNLPDoc.isPresent()) {
          return off;
        }
        final Optional<CoreNLPSentence> sent =
            coreNLPDoc.get().firstSentenceContaining(off.asCharOffsetRange());
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
    };
  }

  private interface MentionResponseChecker {

    boolean aligns(Response r, EREArgument ea);
  }

  private abstract class SpanChecker implements MentionResponseChecker {

    protected final Function<Response, CharOffsetSpan> responseSpanExtractor;
    protected final Function<EREArgument, CharOffsetSpan> ereArgSpanExtractor;

    protected SpanChecker(final Function<Response, CharOffsetSpan> responseSpanExtractor,
        final Function<EREArgument, CharOffsetSpan> ereArgSpanExtractor) {
      this.responseSpanExtractor = responseSpanExtractor;
      this.ereArgSpanExtractor = ereArgSpanExtractor;
    }
  }

  private class ExactSpanChecker extends SpanChecker {

    protected ExactSpanChecker(
        final Function<Response, CharOffsetSpan> responseSpanExtractor,
        final Function<EREArgument, CharOffsetSpan> ereArgSpanExtractor) {
      super(responseSpanExtractor, ereArgSpanExtractor);
    }

    @Override
    public boolean aligns(final Response r, final EREArgument ea) {
      return responseSpanExtractor.apply(r).equals(ereArgSpanExtractor.apply(ea));
    }
  }

  private final class ContainmentSpanChecker implements MentionResponseChecker {

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

      if (responseOffsets.encloses(ereOffsets) || ereOffsets.encloses(responseOffsets)) {
        return
            (responseOffsets.encloses(responseHead) && responseOffsets.encloses(ereHead))
                || (ereOffsets.encloses(responseHead) && ereOffsets.encloses(ereHead));
      }
      return false;
    }
  }

  private class ComposingRoleBasedChecker implements MentionResponseChecker {

    private final MentionResponseChecker checker;
    private final EREToKBPEventOntologyMapper ontologyMapper;

    protected ComposingRoleBasedChecker(
        final MentionResponseChecker checker,
        final EREToKBPEventOntologyMapper ontologyMapper) {
      this.checker = checker;
      this.ontologyMapper = ontologyMapper;
    }

    @Override
    public boolean aligns(final Response r, final EREArgument ea) {
      if (checker.aligns(r, ea)) {
        final Optional<Symbol> role = ontologyMapper.eventRole(Symbol.from(ea.getRole()));
        if (role.isPresent() && role.get().equals(r.role())) {
          return true;
        }
      }
      return false;
    }
  }

  private class EREMatcher {

    public Optional<ScoringCorefID> aligns(final Response r,
        final MentionResponseChecker checker) {
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
  }
}
