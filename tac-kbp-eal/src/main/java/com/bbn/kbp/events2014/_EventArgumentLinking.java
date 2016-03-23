package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.collections.CollectionUtils;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import org.immutables.func.Functional;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a grouping of document-level event arguments into event frames/hopper. Optionally
 * these event frames can be mapped to IDs.  This mapping must be present for the 2016 evaluation
 * but should not be present for 2014 and 2015.
 */
@Value.Immutable(prehash = true)
@TextGroupPublicImmutable
@Functional
abstract class _EventArgumentLinking {

  private static Logger log = LoggerFactory.getLogger(_EventArgumentLinking.class);

  public abstract Symbol docID();

  public abstract ImmutableSet<TypeRoleFillerRealisSet> eventFrames();

  public abstract ImmutableSet<TypeRoleFillerRealis> incomplete();

  public abstract Optional<ImmutableMap<String, TypeRoleFillerRealisSet>> idsToEventFrames();

  @Value.Check
  protected void check() {
    for (final TypeRoleFillerRealisSet eventFrame : eventFrames()) {
      final Set<TypeRoleFillerRealis> intersection =
          Sets.intersection(eventFrame.asSet(), incomplete());
      checkArgument(intersection.isEmpty(), "A TRFR cannot be both incomplete and linked: %s",
          intersection);
    }
    if (idsToEventFrames().isPresent()) {
      for (final String id : idsToEventFrames().get().keySet()) {
        checkArgument(!id.contains("-"), "Event frame IDs may not contain -s");
        checkArgument(!id.contains("\t"), "Event frame IDs may not contain tabs");
      }
      CollectionUtils.assertSameElementsOrIllegalArgument(eventFrames(),
          idsToEventFrames().get().values(), "Event frames did not match IDs",
          "Event frames in list", "Event frames in ID map");
    }
  }

  public static EventArgumentLinking createMinimalLinkingFrom(AnswerKey answerKey) {
    final Function<Response, TypeRoleFillerRealis> ToEquivalenceClass =
        TypeRoleFillerRealis.extractFromSystemResponse(
            answerKey.corefAnnotation().strictCASNormalizerFunction());

    log.info("creating minimal linking for {} responses", answerKey.annotatedResponses().size());

    return EventArgumentLinking.builder().docID(answerKey.docId())
        .incomplete(FluentIterable.from(answerKey.annotatedResponses())
            .transform(AssessedResponseFunctions.response())
            .transform(ToEquivalenceClass)).build();
  }

  public EventArgumentLinking addNewResponsesAsIncompletesFrom(AnswerKey answerKey) {
    EventArgumentLinking minimalLinking = createMinimalLinkingFrom(answerKey);
    ImmutableSet<TypeRoleFillerRealis> allLinked =
        ImmutableSet.copyOf(Iterables.concat(eventFrames()));
    ImmutableSet<TypeRoleFillerRealis> minimalUnlinked = Sets.difference(minimalLinking.incomplete(), allLinked).immutableCopy();
    ImmutableSet<TypeRoleFillerRealis> allUnlinked =
        Sets.union(minimalUnlinked, incomplete()).immutableCopy();
    return EventArgumentLinking.builder().docID(docID()).eventFrames(eventFrames())
        .incomplete(allUnlinked).build();
  }

  public EventArgumentLinking filteredCopy(final Predicate<TypeRoleFillerRealis> toKeepPredicate) {
    final ImmutableSet.Builder<TypeRoleFillerRealisSet> newEventFrames = ImmutableSet.builder();

    for (final TypeRoleFillerRealisSet eventFrame : eventFrames()) {
      final Set<TypeRoleFillerRealis> filteredElements = FluentIterable.from(eventFrame.asSet())
          .filter(toKeepPredicate).toSet();
      if (!filteredElements.isEmpty()) {
        newEventFrames.add(TypeRoleFillerRealisSet.create(filteredElements));
      }
    }

    return EventArgumentLinking.builder().docID(docID()).eventFrames(newEventFrames.build())
        .incomplete(Iterables.filter(incomplete(), toKeepPredicate)).build();
  }

  public ImmutableSet<Set<TypeRoleFillerRealis>> linkedAsSetOfSets() {
    final ImmutableSet.Builder<Set<TypeRoleFillerRealis>> ret = ImmutableSet.builder();
    for (final TypeRoleFillerRealisSet eventFrame : eventFrames()) {
      ret.add(eventFrame.asSet());
    }
    return ret.build();
  }

  public Set<TypeRoleFillerRealis> allLinkedEquivalenceClasses() {
    return ImmutableSet.copyOf(Iterables.concat(eventFrames()));
  }
}
