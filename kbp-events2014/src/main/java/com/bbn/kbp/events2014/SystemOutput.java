package com.bbn.kbp.events2014;

import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.scoring.Scoreds;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

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

  public static String DEFAULT_METADATA = "";
  private final Symbol docId;
  private final ImmutableSet<Response> responses;
  private final ImmutableMap<Response, Double> confidences;
  private final ImmutableMap<Response, String> metadata;

  private SystemOutput(final Symbol docId, final Iterable<Response> responses,
      final Map<Response, Double> confidences, final Map<Response, String> metadata) {
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
    this.metadata = ImmutableMap.copyOf(metadata);

    checkArgument(metadata.keySet().containsAll(this.responses),
        "Metadata is missing for some responses");
    checkArgument(confidences.keySet().containsAll(this.responses),
        "Confidences are missing for some responses");
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

  public ImmutableMap<Response, String> allMetadata() {
    return metadata;
  }

  public String metadata(final Response response) {
    checkNotNull(response);
    return metadata.get(response);
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

  public static SystemOutput createWithoutMetadata(final Symbol docId,
      final Iterable<Scored<Response>> scoredResponses) {
    final Map<Response, String> emptyMetadata = Maps.asMap(FluentIterable.from(scoredResponses)
        .transform(Scoreds.<Response>itemsOnly()).toSet(), Functions.constant(DEFAULT_METADATA));
    return from(docId, scoredResponses, emptyMetadata);
  }

  /**
   * Deprecated due to the addition of metadata tracking - older Functions which filtered responses
   * and constructed new SystemOutputs are now broken for analystics use because they do not
   * preserve metadata use @see SystemOutput#createWithoutMetadata
   */
  @Deprecated
  public static SystemOutput from(final Symbol docId,
      final Iterable<Scored<Response>> scoredResponses) {
    return createWithoutMetadata(docId, scoredResponses);
  }

  /**
   * retains only metadata pertaining to responses for which we have a score.
   */
  public static SystemOutput from(final Symbol docId,
      final Iterable<Scored<Response>> scoredResponses, final Map<Response, String> metadata) {
    ImmutableSet<Response> responses =
        ImmutableSet.copyOf(transform(scoredResponses, Scoreds.<Response>itemsOnly()));
    return new SystemOutput(docId, responses, Scoreds.asMapKeepingHigestScore(scoredResponses),
        Maps.filterKeys(metadata, Predicates.in(responses)));
  }

  public static SystemOutput createFromScoredAndMetadata(final Symbol docId,
      final Iterable<Scored<Response>> scoredResponses,
      final Map<Scored<Response>, String> metadata) {
    final Map<Response, Double> scores = Scoreds.asMapKeepingHigestScore(scoredResponses);
    final Iterable<Scored<Response>> highest =
        transform(scores.entrySet(), Scoreds.<Response>mapEntryToDoubleToScored());
    final Map<Response, String> responseToMetadata = MapUtils.copyWithKeysTransformedByInjection(
        Maps.filterKeys(metadata, Predicates.in(ImmutableSet.copyOf(highest))),
        Scoreds.<Response>itemsOnly());
    return new SystemOutput(docId, scores.keySet(), scores, responseToMetadata);
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

  /**
   * keeps the metadata associated with the highest score for a given Response, e.g. in the case of
   * multiple derivations
   */
  public static SystemOutput unionKeepingMaximumScore(Iterable<SystemOutput> systemOutputs) {
    checkArgument(!isEmpty(systemOutputs), "Cannot take union of zero system outputs");
    checkArgument(allEqual(transform(systemOutputs, DocID)),
        "Cannot take the union of system outputs with different docids");
    final Map<Response, Double> responseToScore = new HashMap<Response, Double>();
    final Map<Response, Scored<Response>> scoredResponses =
        new HashMap<Response, Scored<Response>>();
    final Map<Response, String> responseToMetadata = new HashMap<Response, String>();
    for (final SystemOutput output : systemOutputs) {
      for (final Scored<Response> scoredResponse : output.scoredResponses()) {
        final Double score = responseToScore.get(scoredResponse.item());
        if (score == null || score < scoredResponse.score()) {
          responseToScore.put(scoredResponse.item(), scoredResponse.score());
          responseToMetadata.put(scoredResponse.item(), output.metadata(scoredResponse.item()));
          scoredResponses.put(scoredResponse.item(), scoredResponse);
        }
      }
    }

    return SystemOutput
        .from(getFirst(systemOutputs, null).docId(), scoredResponses.values(), responseToMetadata);
  }

  public SystemOutput copyWithFilteredResponses(Predicate<Scored<Response>> predicate) {
    final Iterable<Scored<Response>> scoredResponses =
        Iterables.filter(scoredResponses(), predicate);
    final ImmutableSet<Response> responses = ImmutableSet.copyOf(
        transform(scoredResponses, Scoreds.<Response>itemsOnly()));
    final Predicate<Response> retainedResponse = Predicates.in(responses);
    // retain only the responses and metadata that this predicate filters
    return SystemOutput.from(docId(), scoredResponses, Maps.filterKeys(metadata, retainedResponse));
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
      final Iterable<Response> responses, final double score) {
    return createWithoutMetadata(docID,
        Iterables.transform(responses, SystemOutput.<Response>withConstantScoreFunction(
            score)));
  }

  @MoveToBUECommon
  private static <T> Function<T, Scored<T>> withConstantScoreFunction(final double score) {
    return new Function<T, Scored<T>>() {
      @Override
      public Scored<T> apply(final T input) {
        return Scored.from(input, score);
      }
    };
  }

  public Builder modifiedCopyBuilder() {
    return new Builder(docId(), Sets.newHashSet(responses), Maps.newHashMap(confidences),
        Maps.newHashMap(metadata));
  }

  public static final class Builder {

    private final Symbol docId;
    private final Set<Response> responses;
    private final Map<Response, Double> confidences;
    private final Map<Response, String> metadata;

    private Builder(final Symbol docId, final Set<Response> responses,
        final Map<Response, Double> confidences,
        final Map<Response, String> metadata) {
      this.confidences = confidences;
      this.docId = docId;
      this.responses = responses;
      this.metadata = metadata;
    }

    /**
     * Replaces {@code oldResponse} with {@code newResponse}. {@code oldResponse} must have been
     * previously added or a {@link NoSuchElementException} will be thrown.  If {@code newResponse}
     * is already present, we compare the score of {@code oldResponse} and {@code newResponse} and use
     * the score and metadata of the higher one. Otherwise, {@code
     * newResponse} will inherit {@code oldResponse}'s score and metadata.
     */
    public void replaceResponseKeepingBestScoreAndMetadata(Response oldResponse,
        Response newResponse) {
      if (!responses.contains(oldResponse)) {
        throw new NoSuchElementException("Cannot replace response " + oldResponse
            + " because it is not present");
      }

      if (oldResponse.equals(newResponse)) {
        return;
      }

      if (!responses.contains(newResponse) || confidences.get(oldResponse)>confidences.get(newResponse)) {
        confidences.put(newResponse, confidences.get(oldResponse));
        metadata.put(newResponse, metadata.get(oldResponse));
      }

      responses.add(newResponse);
      responses.remove(oldResponse);
      confidences.remove(oldResponse);
      metadata.remove(oldResponse);
    }

    public Builder remove(Response response) {
      responses.remove(response);
      confidences.remove(response);
      metadata.remove(response);
      return this;
    }

    public SystemOutput build() {
      return new SystemOutput(docId, responses, confidences, metadata);
    }
  }
}
