package com.bbn.kbp.events2014.io.assessmentCreators;

import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.google.common.base.Optional;

public interface AssessmentCreator {
    public Optional<ResponseAssessment> createAssessmentFromFields(final FieldAssessment aet,
        final Optional<FieldAssessment> aer, final Optional<FieldAssessment> casAssessment,
        final Optional<KBPRealis> realis, final Optional<FieldAssessment> baseFillerAssessment,
        final Optional<Integer> coreference, final Optional<ResponseAssessment.MentionType> mentionTypeOfCAS);
}
