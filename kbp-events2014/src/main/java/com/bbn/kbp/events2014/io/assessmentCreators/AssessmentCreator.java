package com.bbn.kbp.events2014.io.assessmentCreators;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.*;
import com.google.common.base.Optional;

import java.util.List;

public interface AssessmentCreator {
    public Optional<ResponseAssessment> createAssessmentFromFields(final Optional<FieldAssessment> aet,
        final Optional<FieldAssessment> aer, final Optional<FieldAssessment> casAssessment,
        final Optional<KBPRealis> realis, final Optional<FieldAssessment> baseFillerAssessment,
        final Optional<Integer> coreference, final Optional<ResponseAssessment.MentionType> mentionTypeOfCAS);

    public AnswerKey createAnswerKey(Symbol docID, List<AssessedResponse> assessedResponses,
                                     List<Response> unassessedResponses);
}
