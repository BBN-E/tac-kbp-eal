package com.bbn.kbp.events2014.scorer;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.linking.EventArgumentLinkingAligner;
import com.bbn.kbp.events2014.linking.ExactMatchEventArgumentLinkingAligner;

import static com.google.common.base.Preconditions.checkArgument;

public final class LinkingScorer {
  // TODO: check this is the right one
  private final EventArgumentLinkingAligner aligner =
      ExactMatchEventArgumentLinkingAligner.create();

  private LinkingScorer() {
  }

  public static LinkingScorer create() {
    return new LinkingScorer();
  }

  public LinkingScore scoreLinking(AnswerKey answerKey, ResponseLinking referenceLinking,
      SystemOutput systemOutput, ResponseLinking systemLinking)
  {
    checkArgument(answerKey.docId() == systemOutput.docId(), "System output has doc ID %s "
        "but answer key has doc ID %s", systemOutput.docId(), answerKey.docId());
    checkArgument(answerKey.docId() == referenceLinking.docID(),
        "Answer key docID %s does not match "
            + "reference linking doc ID %s", answerKey.docId(), referenceLinking.docID());
    checkArgument(systemOutput.docId() == systemLinking.docID(), "System output docID %s does "
        + "not match system linking docID %s", systemOutput.docId(), systemLinking.docID());

    checkArgument(referenceLinking.incompleteResponses().isEmpty(),
        "Reference key for %s has %s responses whose linking has not been annotated",
        referenceLinking.docID(), referenceLinking.incompleteResponses().size());

    validitychecksAndFiltering();

    final EventArgumentLinking referenceArgumentLinking = aligner.align(referenceLinking, answerKey);
    final EventArgumentLinking systemArgumentLinking = aligner.align(systemLinking, answerKey);



  }


  private static final class LinkingScore {

  }
}
