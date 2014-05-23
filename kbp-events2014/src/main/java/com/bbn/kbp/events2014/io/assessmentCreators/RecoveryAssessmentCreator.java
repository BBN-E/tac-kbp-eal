package com.bbn.kbp.events2014.io.assessmentCreators;

import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import java.util.Random;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class allows creating assessment objects when the provided input
 * does not satisfy the requirements of the specification. This is needed
 * for dealing with the pilot corpus annotation. It can also produce a report
 * of the problems found.
 */
public final class RecoveryAssessmentCreator implements AssessmentCreator {
    private int responsesAttempted = 0;
    private int makeMissingCorefSingleton = 0;
    private Multiset<String> extras = HashMultiset.create();
    private Multiset<String> missing = HashMultiset.create();
    private final Random rng;

    private RecoveryAssessmentCreator(final Random rng) {
        this.rng = checkNotNull(rng);
    }

    public static RecoveryAssessmentCreator createWithRandomSeed(final Random rng) {
        return new RecoveryAssessmentCreator(rng);
    }

    /**
     * Produces a human-readable report on what was erroneously present and missing in
     * the assessments created through this {@link AssessmentCreator}.
     * @return
     */
    public String report() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Processed ").append(responsesAttempted).append(" responses.\n");
        sb.append(extras.size()).append(" extra fields were removed. The breakdown is ")
                .append(extras).append("\n");
        sb.append("Coreference was missing in ").append(makeMissingCorefSingleton)
                .append(" responses. These were placed in singletons with random indices.")
                .append(" There is some tiny chance of a collision here, but it is too small to worry about.\n");
        sb.append("Required fields were missing in ").append(missing.size()).append(" responses. ")
                .append("These were treated as unannotated. The breakdown of missing fields is ")
                .append(missing).append("\n");
        return sb.toString();
    }

    /**
     * Attempts to create a {@link com.bbn.kbp.events2014.ResponseAssessment} from the provided fields
     * in a way that tries to recover from some common violations of the specification.  It will suppress
     * unnecessary fields and places items lacking coreference in singleton clusters with random indices. If
     * there are any missing fields, {@link com.google.common.base.Optional#absent()} is returned.
     */
    public Optional<ResponseAssessment> createAssessmentFromFields(final Optional<FieldAssessment> aet,
        final Optional<FieldAssessment> aer, final Optional<FieldAssessment> casAssessment,
        final Optional<KBPRealis> realis, final Optional<FieldAssessment> baseFillerAssessment,
        final Optional<Integer> coreference, final Optional<ResponseAssessment.MentionType> mentionTypeOfCAS)
    {
        ++responsesAttempted;

        Optional<FieldAssessment> fixedAET = aet;
        Optional<FieldAssessment> fixedAER = aer;
        Optional<FieldAssessment> fixedCAS = casAssessment;
        Optional<KBPRealis> fixedRealis = realis;
        Optional<FieldAssessment> fixedBF = baseFillerAssessment;
        Optional<Integer> fixedCoref = coreference;
        Optional<ResponseAssessment.MentionType> fixedMentionType = mentionTypeOfCAS;

        // if we're missing coreference, we put it in a singleton cluster with a random index
        if (!coreference.isPresent() && casAssessment.isPresent() && casAssessment.get().isAcceptable()) {
            ++makeMissingCorefSingleton;
            // there is some tiny chance of a collision here, but it's not worth worrying about
            fixedCoref = Optional.of(rng.nextInt());
        }

        // if we have extra if anything, we just clear it.
        // if we are missing anything besides coref, the whole response is treated
        // as unannotated (return Optional.absent())
        if (fixedAER.isPresent() && !FieldAssessment.isAcceptable(fixedAET)) {
            extras.add("AER");
            fixedAER = Optional.absent();
        } else if (!fixedAER.isPresent() && FieldAssessment.isAcceptable(fixedAET)) {
            missing.add("AER");
            return Optional.absent();
        }

        if (FieldAssessment.isAcceptable(fixedAET) && FieldAssessment.isAcceptable(fixedAER)) {
            // if AET and AER are both correct, the following
            // are required
            if (!fixedBF.isPresent()) {
                missing.add("BF");
                return Optional.absent();
            }

            if (!fixedCAS.isPresent()) {
                missing.add("CAS");
                return Optional.absent();
            }

            if (!fixedRealis.isPresent()) {
                missing.add("Realis");
                return Optional.absent();
            }

            if (!fixedMentionType.isPresent()) {
                missing.add("MentionType");
                return Optional.absent();
            }

        } else {
            // if AET and AER are not both correct,
            // the following ought to be absent
            if (fixedBF.isPresent()) {
                extras.add("BF");
                fixedBF = Optional.absent();
            }
            if (fixedCAS.isPresent()) {
                extras.add("CAS");
                fixedCAS = Optional.absent();
            }
            if (fixedRealis.isPresent()) {
                extras.add("Realis");
                fixedRealis = Optional.absent();
            }
            if (fixedMentionType.isPresent()) {
                extras.add("MentionType");
                fixedMentionType = Optional.absent();
            }
        }

        return Optional.of(ResponseAssessment.create(fixedAET, fixedAER, fixedCAS,
                fixedRealis, fixedBF, fixedCoref, fixedMentionType));
    }
}
