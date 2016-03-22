package com.bbn.kbp.events2014.io.assessmentCreators;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.FillerMentionType;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;

import com.google.common.base.Optional;

import java.util.List;

/**
 * Creates {@link com.bbn.kbp.events2014.ResponseAssessment}s which enforcing all constraints from
 * the specification.
 */
public final class StrictAssessmentCreator implements AssessmentCreator {

  private StrictAssessmentCreator() {
  }

  @Override
  public AssessmentParseResult createAssessmentFromFields(Optional<FieldAssessment> aet,
      Optional<FieldAssessment> aer, Optional<FieldAssessment> casAssessment,
      Optional<KBPRealis> realis,
      Optional<FieldAssessment> baseFillerAssessment, Optional<Integer> coreference,
      Optional<FillerMentionType> mentionTypeOfCAS) {
    return new AssessmentParseResult(ResponseAssessment.of(aet, aer, casAssessment, realis,
        baseFillerAssessment, mentionTypeOfCAS), coreference.orNull());
  }

  @Override
  public AnswerKey createAnswerKey(Symbol docID, List<AssessedResponse> assessedResponses,
      List<Response> unassessedResponses, CorefAnnotation corefAnnotation) {
    return AnswerKey
        .fromPossiblyOverlapping(docID, assessedResponses, unassessedResponses, corefAnnotation);
  }

  @Override
  public CorefAnnotation.Builder corefBuilder(Symbol docId) {
    return CorefAnnotation.strictBuilder(docId);
  }

  public static AssessmentCreator create() {
    return new StrictAssessmentCreator();
  }
}
