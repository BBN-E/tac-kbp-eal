package com.bbn.kbp.events2014;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.scoring.Scoreds;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.bbn.bue.common.collections.IterableUtils.allEqual;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Iterables.transform;

/**
 * Represents a KBP 2014 event argument attachment system's output on a document.
 */
public final class SystemOutput {

  private final Symbol docId;
  private final ImmutableSet<Response> responses;
  private final ImmutableMap<Response, Double> confidences;

  private SystemOutput(final Symbol docId, final Iterable<Response> responses,
      final Map<Response, Double> confidences) {
    this.docId = checkNotNull(docId);
    this.responses = ImmutableSet.copyOf(responses);
    for (final Response response : responses) {
      checkArgument(docId == response.docID(),
          "System output %s contains response with non-matching docid %s",
          docId, response.docID());
    }
    this.confidences = ImmutableMap.copyOf(confidences);
    checkArgument(all(confidences.values(), Range.closed(0.0, 1.0)),
        "System output %s contains confidences outside [0.0,1.0]", docId);
  }

  /**
   * All system responses.
   */
  public ImmutableSet<Response> responses() {
    return responses;
  }

  /**
   * All system responses with corresponding scores.  Scores are kept separately from the responses
   * themselves because they do not determine the identity of a response.
   */
  public ImmutableSet<Scored<Response>> scoredResponses() {
    final ImmutableSet.Builder<Scored<Response>> ret = ImmutableSet.builder();

    for (final Response response : responses) {
      ret.add(Scored.from(response, confidence(response)));
    }

    return ret.build();
  }

  public Symbol docId() {
    return docId;
  }

  /**
   * The number of system responses.
   */
  public int size() {
    return responses().size();
  }

  /**
   * Returns the system score/confidence for a response. Throws an exception if the provided
   * response is not found in this assessment store.
   */
  public double confidence(final Response response) {
    checkNotNull(response);
    final Double ret = confidences.get(response);
    if (ret != null) {
      return ret;
    } else {
      throw new RuntimeException(String.format(
          "Tried to lookup confidence of response %s which was not withAdditionalJustifications this system output",
          response));
    }
  }

  /**
   * Same as {@code confidence()} but returns a {@link com.bbn.bue.common.scoring.Scored} object.
   */
  public Scored<Response> score(final Response response) {
    return Scored.from(response, confidence(response));
  }

  /**
   * Implements the selection strategy to use when the system provides the same response with
   * multiple justifications.  From the provided responses, it prefers the response with the highest
   * score.  If there is a tie, it prefers the response with the higher hash code. If no responses
   * are provided, {@code Optional.absent()} is returned.
   */
  public Optional<Response> selectFromMultipleSystemResponses(final Collection<Response> args) {
    if (args.isEmpty()) {
      return Optional.absent();
    }
    if (args.size() == 1) {
      // will never return null because size is 1
      return Optional.of(getFirst(args, null));
    }

    return Optional.of(Collections.max(args, ByConfidenceThenOld2014ID));
  }

  public static SystemOutput from(final Symbol docId,
      final Iterable<Scored<Response>> scoredResponses) {
    return new SystemOutput(docId, transform(scoredResponses, Scoreds.<Response>itemsOnly()),
        Scoreds.asMapKeepingHigestScore(scoredResponses));
  }

  private final Ordering<Response> ByConfidenceThenOld2014ID = new Ordering<Response>() {
    @Override
    public int compare(final Response left, final Response right) {
      return ComparisonChain.start()
          .compare(confidence(left), confidence(right))
          .compare(left.old2014ResponseID(),
              right.old2014ResponseID())
          .result();
    }
  };

  public static final Function<SystemOutput, Symbol> DocID = new Function<SystemOutput, Symbol>() {
    @Override
    public Symbol apply(SystemOutput input) {
      return input.docId();
    }
  };

  public static final Function<SystemOutput, ImmutableSet<Response>> Responses =
      new Function<SystemOutput, ImmutableSet<Response>>() {
        @Override
        public ImmutableSet<Response> apply(SystemOutput input) {
          return input.responses();
        }
      };

  public static SystemOutput unionKeepingMaximumScore(Iterable<SystemOutput> systemOutputs) {
    checkArgument(!isEmpty(systemOutputs), "Cannot take union of zero system outputs");
    checkArgument(allEqual(transform(systemOutputs, DocID)),
        "Cannot take the union of system outputs with different docids");
    final Multimap<Response, Double> responseToScore = HashMultimap.create();
    for (final SystemOutput output : systemOutputs) {
      for (final Scored<Response> scoredResponse : output.scoredResponses()) {
        responseToScore.put(scoredResponse.item(), scoredResponse.score());
      }
    }
    final List<Scored<Response>> responsesWithBestScores = Lists.newArrayList();
    for (final Map.Entry<Response, Collection<Double>> forResponse : responseToScore.asMap()
        .entrySet()) {
      // keep the best score for each response
      responsesWithBestScores.add(
          Scored.from(forResponse.getKey(), Collections.max(forResponse.getValue())));
    }
    // getFirst safe because of checkArgument above
    return SystemOutput.from(getFirst(systemOutputs, null).docId(), responsesWithBestScores);
  }

  public SystemOutput copyWithFilteredResponses(Predicate<Scored<Response>> predicate) {
    return SystemOutput.from(docId(), Iterables.filter(scoredResponses(), predicate));
  }


  @Override
  public int hashCode() {
    return Objects.hashCode(docId, responses, confidences);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final SystemOutput other = (SystemOutput) obj;
    return Objects.equal(this.docId, other.docId) && Objects.equal(this.responses, other.responses)
        && Objects.equal(this.confidences, other.confidences);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("docID", docId).add("scoredResponses",
        scoredResponses()).toString();
  }

  /**
   * For testing
   */
  public static SystemOutput createWithConstantScore(final Symbol docID,
      final ImmutableSet<Response> responses, final double score) {
    final ImmutableMap.Builder<Response, Double> scores = ImmutableMap.builder();
    for (final Response response : responses) {
      scores.put(response, score);
    }
    return new SystemOutput(docID, responses, scores.build());
  }
}
