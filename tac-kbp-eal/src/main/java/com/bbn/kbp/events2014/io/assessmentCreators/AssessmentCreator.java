package com.bbn.kbp.events2014.io.assessmentCreators;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;

import com.google.common.base.Optional;

import java.util.List;

public interface AssessmentCreator {

  public AssessmentParseResult createAssessmentFromFields(final Optional<FieldAssessment> aet,
      final Optional<FieldAssessment> aer, final Optional<FieldAssessment> casAssessment,
      final Optional<KBPRealis> realis, final Optional<FieldAssessment> baseFillerAssessment,
      final Optional<Integer> coreference,
      final Optional<ResponseAssessment.MentionType> mentionTypeOfCAS);

  public AnswerKey createAnswerKey(Symbol docID, List<AssessedResponse> assessedResponses,
      List<Response> unassessedResponses, CorefAnnotation corefAnnotation);

  public class AssessmentParseResult {

    private final ResponseAssessment assessment;
    private final Integer corefId;

    AssessmentParseResult(ResponseAssessment assessment, Integer corefId) {
      this.assessment = assessment;
      this.corefId = corefId;
    }

    public Optional<ResponseAssessment> assessment() {
      return Optional.fromNullable(assessment);
    }

    public Optional<Integer> corefId() {
      return Optional.fromNullable(corefId);
    }

    public static AssessmentParseResult fromCorefOnly(Optional<Integer> corefId) {
      return new AssessmentParseResult(null, corefId.orNull());
    }
  }

  public CorefAnnotation.Builder corefBuilder(Symbol docId);
}
