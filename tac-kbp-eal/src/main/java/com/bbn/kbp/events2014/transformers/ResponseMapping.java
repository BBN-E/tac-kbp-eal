package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.CorpusEventFrame;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocEventFrameReference;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;

/**
 * Represents a mapping and/or deletion of response, with the capability to apply it to answer keys,
 * system outputs, and response linkings.
 */
public final class ResponseMapping {

  private static final Logger log = LoggerFactory.getLogger(ResponseMapping.class);

  private final ImmutableMap<Response, Response> replacedResponses;
  private final ImmutableSet<Response> deletedResponses;

  private ResponseMapping(
      final Map<Response, Response> replacedResponses,
      final Iterable<Response> deletedResponses) {
    this.replacedResponses = ImmutableMap.copyOf(replacedResponses);
    this.deletedResponses = ImmutableSet.copyOf(deletedResponses);
    checkArgument(
        Sets.intersection(this.replacedResponses.keySet(), this.deletedResponses).isEmpty(),
        "A response cannot have a replacement and be deleted");
    checkArgument(Sets.intersection(ImmutableSet.copyOf(this.replacedResponses.values()),
        this.deletedResponses).isEmpty(),
        "A response cannot be a replacement and be deleted");
  }

  public static ResponseMapping create(final Map<Response, Response> replacedResponses,
      final Iterable<Response> deletedResponses) {
    return new ResponseMapping(replacedResponses, deletedResponses);
  }

  public static ResponseMapping delete(final Iterable<Response> toDelete) {
    return create(ImmutableMap.<Response, Response>of(), toDelete);
  }

  public boolean isIdentity() {
    if (!deletedResponses.isEmpty()) {
      return false;
    }

    for (final Map.Entry<Response, Response> entry : replacedResponses.entrySet()) {
      if (!entry.getKey().equals(entry.getValue())) {
        return false;
      }
    }

    return true;
  }

  public AnswerKey apply(AnswerKey input) {
    final AnswerKey.Builder ret = input.modifiedCopyBuilder();
    final Random rng = new Random(0);

    for (final Map.Entry<Response, Response> replacement : replacedResponses.entrySet()) {
      ret.replaceAssessedResponseMaintainingAssessment(replacement.getKey(),
          replacement.getValue(), rng);
    }
    for (final Response toDelete : deletedResponses) {
      ret.remove(toDelete);
    }
    return ret.build();
  }

  public ArgumentOutput apply(ArgumentOutput argumentOutput) {
    final ArgumentOutput.Builder ret = argumentOutput.modifiedCopyBuilder();
    for (final Response response : argumentOutput.responses()) {
      if (deletedResponses.contains(response)) {
        ret.remove(response);
      } else if (replacedResponses.containsKey(response)) {
        ret.replaceResponseKeepingBestScoreAndMetadata(response, replacedResponses.get(response));
      }
    }
    return ret.build();
  }

  public ResponseLinking apply(ResponseLinking responseLinking) {
    final Function<Response, Response> responseMapping = MapUtils.asFunction(replacedResponses,
        Functions.<Response>identity());
    final Predicate<Response> notDeleted = not(in(deletedResponses));

    final ImmutableSet.Builder<ResponseSet> newResponseSetsB = ImmutableSet.builder();
    final ImmutableMap.Builder<ResponseSet, ResponseSet> oldResponseSetToNewB = ImmutableMap.builder();
    for (final ResponseSet responseSet : responseLinking.responseSets()) {
      final ImmutableSet<Response> filteredResponses = FluentIterable.from(responseSet)
          .filter(notDeleted)
          .transform(responseMapping).toSet();
      if (!filteredResponses.isEmpty()) {
        final ResponseSet derived = ResponseSet.from(filteredResponses);
        newResponseSetsB.add(derived);
        oldResponseSetToNewB.put(responseSet, derived);
      }
    }

    final ImmutableSet<ResponseSet> newResponseSets = newResponseSetsB.build();
    final Predicate<Response> notLinked = not(in(ImmutableSet.copyOf(
        Iterables.concat(newResponseSets))));
    final ImmutableSet<Response> newIncompletes =
        FluentIterable.from(responseLinking.incompleteResponses())
            .filter(notDeleted)
            .transform(responseMapping)
            .filter(notLinked)
            .toSet();

    final Optional<ImmutableBiMap<String, ResponseSet>> newResponseSetIDMap;
    if (responseLinking.responseSetIds().isPresent()) {
      final BiMap<String, ResponseSet> responseSetIDs = responseLinking.responseSetIds().get();
      final ImmutableMap<ResponseSet, ResponseSet> oldResponseSetToNew = oldResponseSetToNewB.build();
      final Multimap<ResponseSet, ResponseSet> newResponseToOld = oldResponseSetToNew.asMultimap().inverse();
      final ImmutableBiMap.Builder<String, ResponseSet> newResponseSetIDMapB =
          ImmutableBiMap.builder();
      // since response sets may lose responses and no longer be unique, we choose the earliest alphabetical ID
      for(final ResponseSet nu: newResponseToOld.keySet()) {
        final ImmutableSet.Builder<String> candidateIDsB = ImmutableSet.builder();
        for(final ResponseSet old: newResponseToOld.get(nu)) {
          candidateIDsB.add(responseSetIDs.inverse().get(old));
        }
        final ImmutableSet<String> candidateIDs = candidateIDsB.build();
        final String newID = Ordering.natural().sortedCopy(candidateIDs).get(0);
        if(candidateIDs.size() > 1) {
          // only log if we're converting multiple sets.
          log.debug("Collapsing response sets {} to {}", candidateIDs, newID);
        }
        newResponseSetIDMapB.put(newID, nu);
      }
      newResponseSetIDMap = Optional.of(newResponseSetIDMapB.build());
    } else {
      newResponseSetIDMap = Optional.absent();
    }

    return ResponseLinking.builder().docID(responseLinking.docID())
        .responseSets(newResponseSets).incompleteResponses(newIncompletes)
        .responseSetIds(newResponseSetIDMap).build();
  }

  public static CorpusEventLinking apply(CorpusEventLinking corpusEventLinking,
      final SystemOutputStore2016 transformedStore) throws IOException {
    final ImmutableSet.Builder<DocEventFrameReference> retainedHoppersB = ImmutableSet.builder();
    for (final Symbol docid : transformedStore.docIDs()) {
      for (final String hopperID : transformedStore.read(docid).linking().responseSetIds().get()
          .keySet()) {
        retainedHoppersB.add(DocEventFrameReference.of(docid, hopperID));
      }
    }
    final ImmutableSet<DocEventFrameReference> retainedHoppers = retainedHoppersB.build();
    final CorpusEventLinking.Builder ret = CorpusEventLinking.builder();
    for (final CorpusEventFrame ref : corpusEventLinking.corpusEventFrames()) {
      final ImmutableSet<DocEventFrameReference> retainedComponents =
          Sets.intersection(ref.docEventFrames(), retainedHoppers).immutableCopy();
      if (retainedComponents.size() > 0) {
        ret.addCorpusEventFrames(CorpusEventFrame.of(ref.id(), retainedComponents));
        if(retainedComponents.size() != ref.docEventFrames().size()) {
          log.debug("Deleting from {} doc event frame refs", ref.id(),
              Sets.difference(ref.docEventFrames(), retainedHoppers));
        }
      } else {
        log.info("Deleting corpus event frame {}", ref.id());
      }
    }
    return ret.build();
  }

  public String summaryString() {
    return replacedResponses.size() + " mapped; " + deletedResponses.size() + " deleted";
  }
}
