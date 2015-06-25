package com.bbn.kbp.events2014;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;

/**
 * Represents an answer key which may contain both responses with assessments and unannotated
 * responses which need assessing. Each answer key is tied to a particular document.  It is
 * guaranteed that no response will be both assessed and unassessed.
 */
public final class AnswerKey {

  private static final Logger log = LoggerFactory.getLogger(AnswerKey.class);

  private final Symbol docid;
  private final ImmutableMap<Response, AssessedResponse> annotatedArgs;
  private final ImmutableSet<Response> unannotatedResponses;
  private final CorefAnnotation corefAnnotation;

  private AnswerKey(final Symbol docId, final Iterable<AssessedResponse> annotatedArgs,
      final Iterable<Response> unannotatedResponses, CorefAnnotation corefAnnotation) {
    this.docid = checkNotNull(docId);
    try {
      this.annotatedArgs = DuplicateTolerantImmutableMapBuilder
          .uniqueIndex(annotatedArgs, AssessedResponse.Response);
    } catch (IllegalArgumentException iae) {
      throw new IllegalStateException("The same response is assessed multiple times differently",
          iae);
    }
    this.unannotatedResponses = ImmutableSet.copyOf(unannotatedResponses);
    this.corefAnnotation = checkNotNull(corefAnnotation);
    assertConsistency();
  }

  /**
   * Get all assessed responses in this answer key *
   */
  public ImmutableSet<AssessedResponse> annotatedResponses() {
    // this is a little ugly but we don't want to change the type signature
    return ImmutableSet.copyOf(annotatedArgs.values());
  }

  /**
   * Get all unassessed response in this answer key *
   */
  public ImmutableSet<Response> unannotatedResponses() {
    return unannotatedResponses;
  }

  public CorefAnnotation corefAnnotation() {
    return corefAnnotation;
  }

  /**
   * Get all responses, assessed and unassessed
   */
  public ImmutableSet<Response> allResponses() {
    return ImmutableSet.copyOf(
        concat(FluentIterable.from(annotatedResponses()).transform(AssessedResponse.Response),
            unannotatedResponses()));
  }

  /**
   * The document ID of the document this answer key corresponds to. Will never be null.
   */
  public Symbol docId() {
    return docid;
  }

  /**
   * Returns whether all responses in this answer key have been assessed.
   */
  public boolean completelyAnnotated() {
    return unannotatedResponses.isEmpty();
  }

  public Builder modifiedCopyBuilder() {
    return new Builder(docId(), annotatedArgs, unannotatedResponses,
        corefAnnotation.strictCopyBuilder());
  }

  /**
   * Prefer using {@link #modifiedCopyBuilder()}.
   */
  @Deprecated
  public AnswerKey copyAddingPossiblyUnannotated(final Iterable<Response> newUnannotated) {
    return modifiedCopyBuilder().addUnannotated(newUnannotated).build();
  }

  public static AnswerKey from(final Symbol docId, final Iterable<AssessedResponse> annotatedArgs,
      final Iterable<Response> unannotatedResponses, CorefAnnotation corefAnnotation) {
    return new AnswerKey(docId, annotatedArgs, unannotatedResponses,
        removeCorefForAbsentStrings(annotatedArgs, unannotatedResponses, corefAnnotation));
  }

  private static CorefAnnotation removeCorefForAbsentStrings(
      Iterable<AssessedResponse> annotatedArgs,
      Iterable<Response> unannotatedResponses, CorefAnnotation corefAnnotation) {
    return corefAnnotation.copyRemovingStringsNotIn(ImmutableSet.copyOf(
        transform(
            concat(unannotatedResponses,
                transform(annotatedArgs, AssessedResponse.Response)),
            Response.CASFunction())));
  }

  /**
   * Creates an {@code AnswerKey} where the same response may appear both as assessed and
   * unassessed.  This removes any such tuples from the unassessed set before calling {@link
   * #from(com.bbn.bue.common.symbols.Symbol, Iterable, Iterable, CorefAnnotation)}. This is simply
   * provided for convenience.
   */
  public static AnswerKey fromPossiblyOverlapping(Symbol docID, Iterable<AssessedResponse> assessed,
      Iterable<Response> unassessedResponses, CorefAnnotation corefAnnotation) {
    final ImmutableSet<AssessedResponse> assessedResponsesSet = ImmutableSet.copyOf(assessed);
    final ImmutableSet<Response> unassessedResponseSet = ImmutableSet.copyOf(unassessedResponses);
    final Set<Response> assessedResponses = FluentIterable.from(assessedResponsesSet)
        .transform(AssessedResponse.Response).toSet();

    if (Sets.intersection(assessedResponses, unassessedResponseSet).isEmpty()) {
      return from(docID, assessedResponsesSet, unassessedResponseSet, corefAnnotation);
    } else {
      return from(docID, assessedResponsesSet,
          Sets.difference(unassessedResponseSet, assessedResponses),
          corefAnnotation);
    }

  }


  private void assertConsistency() {
    // check there are no response listed as both annotated and unannotated
    checkArgument(Sets.intersection(annotatedArgs.keySet(), unannotatedResponses).isEmpty(),
        "For %s, there are responses which are both unannotated and unannotated", docid);

    assertNoIncompatibleCorefAnnotations();
    checkState(corefAnnotation.docId() == docId(), "Coref doc ID does not match assessment doc ID");
  }

  private void assertNoIncompatibleCorefAnnotations() {
    final ImmutableSet<KBPString> allResponseCASes = FluentIterable
        .from(allResponses())
        .transform(Response.CASFunction())
        .toSet();
    checkSetsEqual(corefAnnotation.allCASes(), "CASes in coref annotation",
        allResponseCASes, "CASes in responses");

    // all correctly assessed responses must have coreffed CASes. Coref of others is optional.
    for (final AssessedResponse assessedResponse : annotatedResponses()) {
      if (assessedResponse.assessment().entityCorrectFiller().isPresent()
          && assessedResponse.assessment().entityCorrectFiller().get().isAcceptable()) {
        if (!corefAnnotation.annotatedCASes().contains(
            assessedResponse.response().canonicalArgument()))
        {
          throw new IllegalArgumentException("Coref annotation is missing coref for canonical argument "
          + assessedResponse.response().canonicalArgument() + " for " + assessedResponse);
        }
      }
    }

    // further consistency checking already done within the corefAnnotation object
  }

  @MoveToBUECommon
  private static <T> void checkSetsEqual(Set<T> left, String leftName, Set<T> right, String rightName) {
    if (!left.equals(right)) {
      final StringBuilder msg = new StringBuilder();
      msg.append(leftName).append(" should exactly equal ").append(rightName).append(" but ");
      final ImmutableSet<T> leftOnly = Sets.difference(left, right).immutableCopy();
      final ImmutableSet<T> rightOnly = Sets.difference(right, left).immutableCopy();

      if (!leftOnly.isEmpty()) {
        msg.append(leftOnly).append(" are only in ").append(leftName).append(" ");
      }
      if (!rightOnly.isEmpty()) {
        msg.append(rightOnly).append(" are only in ").append(rightName).append(" ");
      }

      throw new IllegalArgumentException(msg.toString());
    }
  }

  public Optional<ResponseAssessment> assessment(Response response) {
    checkNotNull(response);
    for (final AssessedResponse assessedResponse : annotatedResponses()) {
      if (assessedResponse.response().equals(response)) {
        return Optional.of(assessedResponse.assessment());
      }
    }
    return Optional.absent();
  }

  public Optional<AssessedResponse> assess(Response response) {
    final Optional<ResponseAssessment> ret = assessment(response);
    if (ret.isPresent()) {
      return Optional.of(AssessedResponse.from(response, ret.get()));
    } else {
      return Optional.absent();
    }
  }

  public AnswerKey filter(Filter filter) {
    final ImmutableSet<AssessedResponse> newAnnotated = FluentIterable.from(annotatedResponses())
        .filter(filter.assessedFilter()).toSet();
    final ImmutableSet<Response> newUnannotated = FluentIterable.from(unannotatedResponses())
        .filter(filter.unassessedFilter()).toSet();
    final CorefAnnotation newCorefAnnotation = removeCorefForAbsentStrings(newAnnotated,
        newUnannotated, corefAnnotation());
    return new AnswerKey(docId(), newAnnotated, newUnannotated, newCorefAnnotation);
  }

  public AnswerKey unannotatedCopy() {
    final CorefAnnotation.Builder newCorefAnnotation = CorefAnnotation.strictBuilder(docId());
    for (final Response response : allResponses()) {
      newCorefAnnotation.addUnannotatedCAS(response.canonicalArgument());
    }
    return AnswerKey.from(docId(), ImmutableSet.<AssessedResponse>of(), allResponses(),
        newCorefAnnotation.build());
  }

  public boolean completelyAssesses(final SystemOutput systemOutput) {
    return Sets.difference(systemOutput.responses(),annotatedArgs.keySet()).isEmpty();
  }

  public void checkCompletelyAssesses(final SystemOutput systemOutput) {
    if (!completelyAssesses(systemOutput)) {
      throw new RuntimeException("The following responses from the system output are not assessed: "
        + StringUtils.NewlineJoiner.join(
          Sets.difference(systemOutput.responses(), annotatedArgs.keySet())));
    }
  }

  public interface Filter {

    public Predicate<AssessedResponse> assessedFilter();

    public Predicate<Response> unassessedFilter();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(docid, annotatedArgs, unannotatedResponses, corefAnnotation);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final AnswerKey other = (AnswerKey) obj;
    return Objects.equal(this.docid, other.docid) && Objects
        .equal(this.annotatedArgs, other.annotatedArgs) && Objects
        .equal(this.unannotatedResponses, other.unannotatedResponses) && Objects
        .equal(this.corefAnnotation, other.corefAnnotation);
  }

  public static AnswerKey createEmpty(Symbol docid) {
    return AnswerKey.from(docid, ImmutableSet.<AssessedResponse>of(), ImmutableSet.<Response>of(),
        CorefAnnotation.createEmpty(docid));
  }

  public AnswerKey copyMerging(AnswerKey toMerge) {
    // (1) determine which responses are newly assessed
    final Set<Response> alreadyAssessedInBaseline = FluentIterable.from(annotatedResponses())
        .transform(AssessedResponse.Response).toSet();

    final Predicate<AssessedResponse> ResponseNotAssessedInBaseline =
        compose(
            not(in(alreadyAssessedInBaseline)),
            AssessedResponse.Response);
    final Set<AssessedResponse> newAssessedResponses =
        FluentIterable.from(toMerge.annotatedResponses())
            .filter(ResponseNotAssessedInBaseline)
            .toSet();

    // add newly assessed responses together with baseline assessed responses to new
    // result, fixing the coreference indices of new assessments if needed
    final ImmutableSet.Builder<AssessedResponse> resultAssessed = ImmutableSet.builder();
    resultAssessed.addAll(annotatedResponses());
    resultAssessed.addAll(newAssessedResponses);

    final ImmutableSet<Response> responsesAssessedInAdditional =
        FluentIterable.from(toMerge.annotatedResponses())
            .transform(AssessedResponse.Response)
            .toSet();
    final Set<Response> stillUnannotated =
        Sets.union(
            // things unassessed in baseline which were still not
            // assessed in additional
            Sets.difference(unannotatedResponses(),
                responsesAssessedInAdditional),
            Sets.difference(toMerge.unannotatedResponses(),
                alreadyAssessedInBaseline));

    log.info("\t{} additional assessments found; {} remain unassessed", newAssessedResponses.size(),
        stillUnannotated.size());

    return AnswerKey.from(docId(), resultAssessed.build(), stillUnannotated,
        corefAnnotation().copyMerging(toMerge.corefAnnotation()));
  }

  public String toString() {
    return Objects.toStringHelper(this)
        .add("docId", docid)
        .add("assessedResponses",
            "{" + StringUtils.NewlineJoiner.join(annotatedResponses()) + "}")
        .add("unassessedResponses",
            "{" + StringUtils.NewlineJoiner.join(unannotatedResponses()))
        .add("coref", corefAnnotation()).toString();
  }

  public static final class Builder {

    private final Symbol docId;
    private final Map<Response, AssessedResponse> annotatedArgs;
    private final Set<Response> unannotatedResponses;
    private final CorefAnnotation.Builder corefAnnotation;

    private Builder(final Symbol docId,
        final Map<Response, AssessedResponse> annotatedArgs,
        final Set<Response> unannotatedResponses,
        final CorefAnnotation.Builder corefAnnotation) {
      this.docId = checkNotNull(docId);
      this.annotatedArgs = Maps.newHashMap(annotatedArgs);
      this.unannotatedResponses = Sets.newHashSet(unannotatedResponses);
      this.corefAnnotation = checkNotNull(corefAnnotation);
      checkArgument(Sets.intersection(annotatedArgs.keySet(), unannotatedResponses).isEmpty(),
          "No response may be both annotated and unannotated");
    }

    /**
     * Adds the specified response as an unassessed response to the answer key. If the response is
     * already assessed, this is ignored.
     */
    public Builder addUnannotated(Response r) {
      if (!annotatedArgs.containsKey(r)) {
        corefAnnotation.addUnannotatedCAS(r.canonicalArgument());
        unannotatedResponses.add(r);
      }
      // if we attempt to add an assessed response as unannotated nothing happens
      return this;
    }

    /**
     * Adds the specified response as an unassessed responses to the answer key. If any response is
     * already assessed, the assessment is not removed.
     */
    public Builder addUnannotated(Iterable<Response> responses) {
      for (final Response r : responses) {
        addUnannotated(r);
      }
      return this;
    }

    public Builder remove(Response response) {
      annotatedArgs.remove(response);
      unannotatedResponses.remove(response);
      return this;
    }

    public AnswerKey build() {
      final Set<KBPString> allCASes = FluentIterable.from(annotatedArgs.keySet())
          .append(unannotatedResponses)
          .transform(Response.CASFunction()).toSet();

      return new AnswerKey(docId, annotatedArgs.values(), unannotatedResponses,
          corefAnnotation.build().copyRemovingStringsNotIn(allCASes));
    }

    /**
     * Replaces one response with another, maintaining the original assessment, if any. The random
     * number generator is used when creating a new coref cluster, if necessary,
     */
    public Builder replaceAssessedResponseMaintainingAssessment(final Response original,
        final Response replacement, final Random rng) {
      if (original.equals(replacement)) {
        return this;
      }

      if (annotatedArgs.keySet().contains(original)) {
        final ResponseAssessment assessment = annotatedArgs.get(original).assessment();
        if (annotatedArgs.containsKey(replacement)) {
          final ResponseAssessment assessmentOfReplacement = annotatedArgs.get(replacement).assessment();
          if (!assessmentOfReplacement.equals(assessment)) {
            log.warn("When replacing responses in answer key, merged two "
                    + "responses with differing assessments {} and {}, keeping original (the latter)",
                assessmentOfReplacement, assessment);
          }
        }
        annotatedArgs.remove(original);
        annotatedArgs.put(replacement, AssessedResponse.from(replacement, assessment));
        corefAnnotation.registerCAS(replacement.canonicalArgument(), rng);
      } else if (unannotatedResponses.contains(original)) {
        unannotatedResponses.remove(original);
        if (!annotatedArgs.containsKey(replacement)) {
          unannotatedResponses.add(replacement);
        }
      } else {
        throw new RuntimeException("Cannot replace " + original + " with " + replacement
            + " because " + original + " is unknown");
      }
      return this;
    }

    public Builder replaceAssessment(final Response response,
        final ResponseAssessment newAssessment) {

      if (annotatedArgs.containsKey(response)) {
        annotatedArgs.put(response, AssessedResponse.from(response, newAssessment));
      } else {
        throw new IllegalArgumentException("Cannot replace assessment for response " + response +
            " because it is not yet assessed");
      }
      return this;
    }
  }
}

/**
 * A way of building immutable maps which allows duplicate identical entries.  Guava's standard
 * {@link com.google.common.collect.ImmutableMap.Builder} will crash when a duplicate key is
 * encountered, even if the value is the same.  This is often inconvenient.  This class allows
 * duplicate entries but otherwise works like an {@link com.google.common.collect.ImmutableMap.Builder}.
 *  Use this when you want to preserve the deterministic ordering properties of {@link
 * ImmutableMap}. If you don't care, just us a {@link java.util.HashMap}.
 */
@MoveToBUECommon
final class DuplicateTolerantImmutableMapBuilder<K, V> {

  private final ImmutableMap.Builder<K, V> innerBuilder = ImmutableMap.builder();
  private final Map<K, V> mappingsSeen = Maps.newHashMap();

  public DuplicateTolerantImmutableMapBuilder put(K key, V value) {
    checkNotNull(value);
    final V currentValue = mappingsSeen.get(key);
    if (currentValue != null) {
      if (!value.equals(currentValue)) {
        throw new IllegalArgumentException("Attempting to add entry mapping " + key + " to " + value
            + " but it is already mapped to " + currentValue);
      }
    } else {
      mappingsSeen.put(key, value);
      innerBuilder.put(key, value);
    }
    return this;
  }

  public ImmutableMap<K, V> build() {
    return innerBuilder.build();
  }

  public static <K, V> ImmutableMap<K, V> uniqueIndex(Iterable<V> values,
      Function<? super V, K> keyFunction) {
    final DuplicateTolerantImmutableMapBuilder ret = new DuplicateTolerantImmutableMapBuilder();
    for (final V val : values) {
      ret.put(keyFunction.apply(val), val);
    }
    return ret.build();
  }
}

