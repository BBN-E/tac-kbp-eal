package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.collections.MapUtils;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

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
    for (final ResponseSet responseSet : responseLinking.responseSets()) {
      final ImmutableSet<Response> filteredResponses = FluentIterable.from(responseSet)
          .filter(notDeleted)
          .transform(responseMapping).toSet();
      if (!filteredResponses.isEmpty()) {
        newResponseSetsB.add(ResponseSet.from(filteredResponses));
      }
    }

    final ImmutableSet<ResponseSet> newResponseSets = newResponseSetsB.build();
    final Predicate<Response> notLinked = not(in(ImmutableSet.copyOf(
        Iterables.concat(newResponseSets))));
    final ImmutableSet<Response> newIncompletes =
        FluentIterable.from(responseLinking.incompleteResponses())
            .filter(notDeleted)
            .filter(notLinked)
            .toSet();
    return ResponseLinking.from(responseLinking.docID(), newResponseSets, newIncompletes);
  }

  public String summaryString() {
    return replacedResponses.size() + " mapped; " + deletedResponses.size() + " deleted";
  }
}
