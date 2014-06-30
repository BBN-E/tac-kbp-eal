package com.bbn.kbp.events2014.io.assessmentCreators;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.*;
import com.google.common.base.Optional;

import java.util.List;

/**
 * Creates {@link com.bbn.kbp.events2014.ResponseAssessment}s which enforcing
 * all constraints from the specification.
 */
public final class StrictAssessmentCreator implements AssessmentCreator {
    private StrictAssessmentCreator() {}

    @Override
    public Optional<ResponseAssessment> createAssessmentFromFields(Optional<FieldAssessment> aet,
        Optional<FieldAssessment> aer, Optional<FieldAssessment> casAssessment, Optional<KBPRealis> realis,
        Optional<FieldAssessment> baseFillerAssessment, Optional<Integer> coreference,
        Optional<ResponseAssessment.MentionType> mentionTypeOfCAS)
    {
        return Optional.of(ResponseAssessment.create(aet, aer, casAssessment, realis, baseFillerAssessment,
                coreference, mentionTypeOfCAS));
    }

    @Override
    public AnswerKey createAnswerKey(Symbol docID, List<AssessedResponse> assessedResponses, List<Response> unassessedResponses) {
        return AnswerKey.fromPossiblyOverlapping(docID, assessedResponses, unassessedResponses);
    }

    public static AssessmentCreator create() {
        return new StrictAssessmentCreator();
    }
}
