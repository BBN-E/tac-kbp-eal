package com.bbn.kbp.events;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events2014.TACKBPEALException;
import com.bbn.nlp.corpora.ere.EREArgument;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.EREEntity;
import com.bbn.nlp.corpora.ere.EREEntityArgument;
import com.bbn.nlp.corpora.ere.EREEventMention;
import com.bbn.nlp.corpora.ere.EREFiller;
import com.bbn.nlp.corpora.ere.EREFillerArgument;

import com.google.common.base.Optional;

import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Enclosing
public final class ScoringUtils {

  private ScoringUtils() {
    throw new UnsupportedOperationException();
  }

  static ScoringCorefID extractScoringEntity(EREArgument ea, EREDocument ereDoc) {
    if (ea instanceof EREEntityArgument) {
      final Optional<EREEntity> entityContainingMention = ereDoc.getEntityContaining(
          ((EREEntityArgument) ea).entityMention());
      if (entityContainingMention.isPresent()) {
        return new ScoringCorefID.Builder()
            .scoringEntityType(ScoringEntityType.fromCASType(
                EREAligner.getBestCASType(entityContainingMention.get())))
          .withinTypeID(entityContainingMention.get().getID()).build();
      } else {
        throw new TACKBPEALException("ERE mention not in any entity");
      }
    } else if (ea instanceof EREFillerArgument) {
      if (ea.getRole().equals("time")) {
        // in the 2016 guidelines we special case time to be resolved to its
        // TIME form
        final Optional<String> normalizedTime =
            ((EREFillerArgument) ea).filler().getNormalizedTime();
        if (normalizedTime.isPresent()) {
          return new ScoringCorefID.Builder().scoringEntityType(ScoringEntityType.Time)
            .withinTypeID(normalizedTime.get()).build();
        } else {
          throw new TACKBPEALException("Time argument has non-temporal filler");
        }
      } else {
        return new ScoringCorefID.Builder().scoringEntityType(ScoringEntityType.Filler)
          .withinTypeID(((EREFillerArgument) ea).filler().getID()).build();
      }
    } else {
      throw new TACKBPEALException("Unknown ERE argument type " + ea.getClass());
    }
  }

  public static Optional<ScoringIdToEreObjectReturn> globalScoringIdToEreObject(
      EREDocument ereDocument, String globalId) {
    checkArgument(globalId.contains("-") && globalId.indexOf("-") < globalId.length()-1,
        "Gloabl ID must contain and not end with a - but got %s", globalId);
    final String withoutPrefix = globalId.substring(globalId.indexOf("-")+1);

    if (globalId.startsWith(ScoringEntityType.Name.name())
        || globalId.startsWith(ScoringEntityType.Nominal.name())
    || globalId.startsWith(ScoringEntityType.Pronoun.name())) {
      for (final EREEntity ereEntity : ereDocument.getEntities()) {
        if (withoutPrefix.equals(ereEntity.getID())) {
          return Optional.<ScoringIdToEreObjectReturn>of(ImmutableScoringUtils.ScoringIdToEreObjectReturn.builder()
              .entityArgument(ereEntity).build());
        }
      }
      throw new TACKBPEALException("Could not align coref ID to ERE entity. Coref ID is " + globalId);
    } else if (globalId.startsWith(ScoringEntityType.Filler.name())) {
      for (final EREFiller ereFiller : ereDocument.getFillers()) {
        if (withoutPrefix.equals(ereFiller.getID())) {
          return Optional.<ScoringIdToEreObjectReturn>of(ImmutableScoringUtils.ScoringIdToEreObjectReturn.builder()
              .fillerArgument(ereFiller).build());
        }
      }
      throw new TACKBPEALException("Could not align coref ID to ERE filler. Coref ID is " + globalId);
    } else if (globalId.startsWith(ScoringEntityType.Time.name())) {
      return Optional.<ScoringIdToEreObjectReturn>of(ImmutableScoringUtils.ScoringIdToEreObjectReturn.builder()
          .normalizedTime(withoutPrefix).build());
    } else if (globalId.startsWith(ScoringEntityType.AlignmentFailure.name())) {
      // if something never aligned to ERE in the first place, we can't figure out
      // its corresponding ERE object
      return Optional.absent();
    } else {
      throw new TACKBPEALException("Unknown ScoringEntityType prefix for " + globalId);
    }

  }

  @TextGroupImmutable
  @Value.Immutable
  public abstract static class ScoringIdToEreObjectReturn {
    public abstract Optional<EREEntity> entityArgument();
    public abstract Optional<EREFiller> fillerArgument();
    public abstract Optional<String> normalizedTime();
  }


  public static Optional<Symbol> mapERETypesToDotSeparated(
      EREToKBPEventOntologyMapper ontologyMapper, EREEventMention em) {
    final Optional<Symbol> mappedEventType = ontologyMapper.eventType(Symbol.from(em.getType()));
    final Optional<Symbol> mappedEventSubType = ontologyMapper.eventSubtype(
        Symbol.from(em.getSubtype()));

    if (mappedEventType.isPresent() && mappedEventSubType.isPresent()) {
      return Optional.of(Symbol.from(mappedEventType.get().asString()
          + "." + mappedEventSubType.get().asString()));
    } else {
      return Optional.absent();
    }
  }
}
