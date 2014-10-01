package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.*;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.transform;

/**
 * Projects a source response linking onto a target answer key possibly different
 * than the one it originated on  using a very simple algorithm:
 * <ul>
 *     <li>Each cluster in the source response linking is projected to a new cluster
 *     by mapping each response in the source cluster to its equivalence class under
 *     the target answer key and then taking all the responses in that equivalence class.
 *     Note that it may not be possible to map from a source response to a target
 *     equivalence class (e.g. the target deleted some responses), in which case the source
 *     response is just ignored.
 *     </li>
 *     <li>The incomplete response set in the projection is all assessed responses from the
 *     target answer key which did not end up in any cluster.</li>
 * </ul>
 *
 * Note that this algorithm can behave in dangerous and unexpected ways. For example, suppose
 * the target answer key differs from the original only by the annotator splitting a coreference
 * cluster.  Then every event frame containing any response with a CAS from the original cluster
 * will contain both of the new clusters, which is probably undesirable.
 *
 * This class has a fluent interface. Use it like this:
 * <pre>
 * {@code
 * NaiveResponseLinkingProjector naiveProjector = NaiveResponseLinkingProjector.create();
 * ResponseLinking projected = naiveProjector.from(sourceLinking).projectTo(targetAnswerKey);
 * }
 * </pre>
 */
public final class NaiveResponseLinkingProjector {
    private final ResponseLinking sourceLinking;

    private NaiveResponseLinkingProjector(ResponseLinking sourceLinking) {
        this.sourceLinking = sourceLinking;
    }

    public static NaiveResponseLinkingProjector create() {
        return new NaiveResponseLinkingProjector(null);
    }

    public NaiveResponseLinkingProjector from(ResponseLinking sourceLinking) {
        checkState(sourceLinking != null, "Cannot call from() twice");
        return new NaiveResponseLinkingProjector(checkNotNull(sourceLinking));
    }

    public ResponseLinking projectTo(AnswerKey targetAnswerKey) {
        checkState(sourceLinking != null, "You forgot to call from()");
        checkState(targetAnswerKey.docId() == sourceLinking.docID());

        final Function<Response, Optional<TypeRoleFillerRealis>> toEquivalenceClassIfPossible =
                TypeRoleFillerRealis.extractFromSystemResponseIfPossible(
                        targetAnswerKey.corefAnnotation().normalizeCASIfPossibleFunction());

        final ImmutableSet.Builder<TypeRoleFillerRealisSet> projectedSetsB =
                ImmutableSet.builder();

        for (final ResponseSet responseSet : sourceLinking.responseSets()) {
            // get the equivalence class for every response for which
            // coref information is available in the target
            final ImmutableSet<TypeRoleFillerRealis> projectedEquivalenceClasSet =
                    ImmutableSet.copyOf(Optional.presentInstances(
                            transform(responseSet, toEquivalenceClassIfPossible)));
            // empty sets are meaningless and invalid, so no response in the source
            // could be projected to the target, the set isn't projected either
            if (!projectedEquivalenceClasSet.isEmpty()) {
                projectedSetsB.add(TypeRoleFillerRealisSet.from(projectedEquivalenceClasSet));
            }
        }
        final ImmutableSet<TypeRoleFillerRealisSet> projectedSets = projectedSetsB.build();
        final ImmutableSet<TypeRoleFillerRealis> TRFRsInProjection = ImmutableSet.copyOf(concat(projectedSets));
        final ImmutableSet<TypeRoleFillerRealis> allTargetTRFRs = ImmutableSet.copyOf(
                Optional.presentInstances(FluentIterable
                    .from(targetAnswerKey.annotatedResponses())
                    .transform(AssessedResponse.Response)
                    .transform(toEquivalenceClassIfPossible)));

        final Set<TypeRoleFillerRealis> projectedIncompletesNotFoundElsewhere =
                Sets.difference(allTargetTRFRs, TRFRsInProjection);

        final EventArgumentLinking projectedEventArgumentLinking = EventArgumentLinking.create(
                targetAnswerKey.docId(), projectedSets, projectedIncompletesNotFoundElsewhere);

        return ExactMatchEventArgumentLinkingAligner.create().alignToResponseLinking(
                projectedEventArgumentLinking, targetAnswerKey);
    }
}
