package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Multisets.copyHighestCountFirst;

/**
 * Merges two answer keys together. By default, conflicts are resolved by taking
 * the assessment in the baseline answer key.  For coref, if a CAS is present in baseline,
 * the baseline coref cluster is used. If not, whatever "baseline cluster" contains the most
 * CASes from its cluster in "additional" in used. If none exists, a new cluster is created.
 * Tie breaking is undefined but deterministic.
 *
 */
public final  class MergeAssessmentStores {
    private static Logger log = LoggerFactory.getLogger(MergeAssessmentStores.class);
    private MergeAssessmentStores() {}

    private int numAdditionalMerged = 0;

    public static MergeAssessmentStores create() {
        return new MergeAssessmentStores();
    }

    public void logReport() {
        log.info("Merged in a total of {} new assessments", numAdditionalMerged);
    }

    /**
     * The two answer keys must match in document ID or an {@link java.lang.IllegalArgumentException}
     * will be thrown.
     *
     * @param baseline
     * @param additional
     * @return
     */
    public AnswerKey mergeAnswerKeys(AnswerKey baseline, AnswerKey additional) {
        checkArgument(baseline.docId() == additional.docId());
        log.info("For {}, merging an answer key with {} assessed and {} unassessed into a"
            + " baseline with {} assessed and {} unassessed", baseline.docId(),
                additional.annotatedResponses().size(), additional.unannotatedResponses().size(),
                baseline.annotatedResponses().size(), baseline.unannotatedResponses().size());

        // (1) determine which responses are newly assessed
        final Set<Response> alreadyAssessedInBaseline = FluentIterable.from(baseline.annotatedResponses())
                .transform(AssessedResponse.Response).toSet();
        final Predicate<AssessedResponse> ResponseNotAssessedInBaseline =
                compose(
                        not(in(alreadyAssessedInBaseline)),
                        AssessedResponse.Response);
        final Set<AssessedResponse> newAssessedResponses = FluentIterable.from(additional.annotatedResponses())
                .filter(ResponseNotAssessedInBaseline)
                .toSet();

        // add newly assessed responses together with baseline assessed responses to new
        // result, fixing the coreference indices of new assessments if needed
        final ImmutableSet.Builder<AssessedResponse> resultAssessed = ImmutableSet.builder();
        resultAssessed.addAll(baseline.annotatedResponses());
        resultAssessed.addAll(computeCoref(newAssessedResponses, baseline, additional));

        final ImmutableSet<Response> responsesAssessedInAdditional =
                FluentIterable.from(additional.annotatedResponses())
                    .transform(AssessedResponse.Response)
                    .toSet();
        final Set<Response> stillUnannotated =
                Sets.union(
                        // things unassessed in baseline which were still not
                        // assessed in additional
                        Sets.difference(baseline.unannotatedResponses(),
                                responsesAssessedInAdditional),
                        Sets.difference(additional.unannotatedResponses(),
                                alreadyAssessedInBaseline));

        log.info("\t{} additional assessments found; {} remain unassessed", newAssessedResponses.size(),
                stillUnannotated.size());
        numAdditionalMerged += newAssessedResponses.size();

        return AnswerKey.from(baseline.docId(), resultAssessed.build(), stillUnannotated);
    }

    private ImmutableSet<AssessedResponse> computeCoref(Iterable<AssessedResponse> newAssessedResponses,
                       AnswerKey baseline, AnswerKey additional)
    {
        final ImmutableSet.Builder<AssessedResponse> ret = ImmutableSet.builder();

        // (1) initialize our mapping of CASes to coref indices with the
        // mappings seen in *baseline*
        final Map<KBPString, Integer> newCASToCoref = Maps.newHashMap(
                baseline.makeCASToCorefMap());
        // the next empty index for adding a new coref cluster
        // this potentially could fail due to overflow, but the chances are tiny and
        // the stakes are low, so we won't worry about it
        int nextFreeIndex = newCASToCoref.isEmpty()?1: (1+Collections.max(newCASToCoref.values()));

        // (2) gather a map of coref IDs to the CASes in the corresponding cluster
        // from *additional*
        final Multimap<Integer, KBPString> additionalCorefToCAS = additional.makeCorefToCASMultimap();

        for (final AssessedResponse newAssessedResponse : newAssessedResponses) {
            final Optional<Integer> coref;

            final KBPString CAS = newAssessedResponse.response().canonicalArgument();
            // if response is so wrong that it has no coref, this is still
            // the case, so return absent
            if (!newAssessedResponse.assessment().coreferenceId().isPresent()) {
                coref = Optional.absent();
            } else if (newCASToCoref.containsKey(CAS)) {
                // if CAS present in baseline, use baseline coref
                coref = Optional.of(newCASToCoref.get(CAS));
            } else {
                final StringBuilder msg = new StringBuilder();

                //  otherwise, use the baseline cluster which contains most  clustermates of CAS
                // in "additional"
                final Collection<KBPString> clusterMates = additionalCorefToCAS.get(
                        newAssessedResponse.assessment().coreferenceId().get());
                msg.append("\t\tFor CAS ").append(CAS).append(" no coref ID was found in baseline. It has ")
                        .append(clusterMates.size()).append(" clustermates\n");

                // We use an ImmutableMultiset to have deterministic iteration order
                // and therefore deterministic tie-breaking if two clusters tie
                // on clustermate count
                final ImmutableMultiset.Builder<Integer> bCorefIDsOfClusterMatesInBaseline = ImmutableMultiset.builder();
                for (final KBPString clusterMate : clusterMates) {
                    final Integer corefForClusterMate = newCASToCoref.get(clusterMate);
                    if (corefForClusterMate != null) {
                        // we don't have to worry about iterating over the target CAS itself
                        // because we know it will never pass this check
                        bCorefIDsOfClusterMatesInBaseline.add(corefForClusterMate);
                        msg.append("\t\t\t").append(clusterMate).append(" ---> ")
                                .append(corefForClusterMate).append("\n");
                    } else {
                        msg.append("\t\t\t").append(clusterMate).append(" ---> unknown\n");
                    }
                }

                final ImmutableMultiset<Integer> corefIDsOfClusterMatesInBaseline =
                        bCorefIDsOfClusterMatesInBaseline.build();

                if (!corefIDsOfClusterMatesInBaseline.isEmpty()) {
                    // we know this will be non-null due to the above check
                    coref = Optional.of(
                        getFirst(copyHighestCountFirst(corefIDsOfClusterMatesInBaseline), null));
                    msg.append("\t\tMapping to dominant cluster mate cluster ").append(coref.get()).append("\n");
                } else {
                    // if we had no clustermates with known coref indices, start a new cluster.
                    // When it comes time to assign coref to our clustermates, if any,
                    // then we know they will have at least one clustermate with a known coref ID
                    coref = Optional.of(nextFreeIndex++);
                    msg.append("\t\tMapping to new cluster ").append(coref.get()).append("\n");
                }
                log.info(msg.toString());
            }

            if (coref.isPresent()) {
                newCASToCoref.put(CAS, coref.get());
            }

            ret.add(AssessedResponse.from(newAssessedResponse.response(),
                    newAssessedResponse.assessment().copyWithModifiedCoref(coref)));
        }

        return ret.build();
    }

    private static void usage() {
        System.err.println("usage: MergeAssessmentStores <paramFile>\n"+
                "Where parameters are:\n"+
                "\tbaseline: the assessment store to merge into. Has priority in conflicts.\n"+
                "\tadditional: the new assessments to merge in.\n"+
                "\toutput: where to write the merged store\n");
        System.exit(1);
    }

    private static void trueMain(String[] argv) throws IOException {
        if (argv.length != 1) {
            usage();
        }

        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());

        final File baselinePath = params.getExistingDirectory("baseline");
        final File additionalPath = params.getExistingDirectory("additional");
        final File outputPath = params.getCreatableDirectory("output");

        log.info("Baseline assessment store: {}", baselinePath);
        log.info("Assessment store to be merged in: {}", additionalPath);
        log.info("Merged store will be written to: {}", outputPath);

        final MergeAssessmentStores merger = MergeAssessmentStores.create();

        final AnnotationStore baseline = AssessmentSpecFormats.openAnnotationStore(baselinePath);
        final AnnotationStore additional = AssessmentSpecFormats.openAnnotationStore(additionalPath);
        final AnnotationStore outStore = AssessmentSpecFormats.createAnnotationStore(outputPath);

        for (final Symbol docid : Sets.union(baseline.docIDs(), additional.docIDs())) {
            outStore.write(
                    merger.mergeAnswerKeys(
                            baseline.readOrEmpty(docid),
                            additional.readOrEmpty(docid)));
        }
        baseline.close();
        additional.close();
        outStore.close();
        merger.logReport();
    }

    public static void main(String[] argv) {
        try {
            trueMain(argv);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
