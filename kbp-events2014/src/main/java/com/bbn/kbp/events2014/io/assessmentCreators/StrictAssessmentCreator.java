package com.bbn.kbp.events2014.io.assessmentCreators;

import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.google.common.base.Optional;

/**
 * Creates {@link com.bbn.kbp.events2014.ResponseAssessment}s which enforcing
 * all constraints from the specification.
 */
public final class StrictAssessmentCreator implements AssessmentCreator {
    private StrictAssessmentCreator() {}

    @Override
    public Optional<ResponseAssessment> createAssessmentFromFields(FieldAssessment aet,
        Optional<FieldAssessment> aer, Optional<FieldAssessment> casAssessment, Optional<KBPRealis> realis,
        Optional<FieldAssessment> baseFillerAssessment, Optional<Integer> coreference,
        Optional<ResponseAssessment.MentionType> mentionTypeOfCAS)
    {
        return Optional.of(ResponseAssessment.create(aet, aer, casAssessment, realis, baseFillerAssessment,
                coreference, mentionTypeOfCAS));
    }

    public static AssessmentCreator create() {
        return new StrictAssessmentCreator();
    }
}
