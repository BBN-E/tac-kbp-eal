package com.bbn.kbp.events2014.scorer;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.linking.EventArgumentLinkingAligner;
import com.bbn.kbp.events2014.linking.ExactMatchEventArgumentLinkingAligner;
import com.bbn.kbp.events2014.linking.LinkingUtils;
import com.bbn.nlp.coreference.measures.B3Scorer;

import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Preconditions.checkArgument;

public final class LinkingScorer {
  // TODO: check this is the right one
  private final EventArgumentLinkingAligner aligner =
      ExactMatchEventArgumentLinkingAligner.create();
  private final B3Scorer b3Scorer = B3Scorer.createByElementScorer();

  private LinkingScorer() {
  }

  public static LinkingScorer create() {
    return new LinkingScorer();
  }

  public LinkingScore scoreLinking(AnswerKey answerKey, ResponseLinking referenceLinking,
      ResponseLinking systemLinking)
  {
    checkArgument(answerKey.docId() == systemLinking.docID(), "System output has doc ID %s " +
        "but answer key has doc ID %s", systemLinking.docID(), answerKey.docId());
    checkArgument(answerKey.docId() == referenceLinking.docID(),
        "Answer key docID %s does not match "
            + "reference linking doc ID %s", answerKey.docId(), referenceLinking.docID());
    checkArgument(systemLinking.docID() == systemLinking.docID(), "System output docID %s does "
        + "not match system linking docID %s", systemLinking.docID(), systemLinking.docID());

    checkArgument(referenceLinking.incompleteResponses().isEmpty(),
        "Reference key for %s has %s responses whose linking has not been annotated",
        referenceLinking.docID(), referenceLinking.incompleteResponses().size());

    checkArgument(systemLinking.incompleteResponses().isEmpty(),
        "System linking for %s has incomplete responses", systemLinking.docID());

    // some responses (e.g. generic responses, ones incorrectly assessed)
    // are not linkable
    final AnswerKey filteredAnswerKey = answerKey.filter(
        LinkingUtils.linkableResponseFilter2015ForGold());
    final ImmutableSet<Response> linkableResponses = FluentIterable
        .from(filteredAnswerKey.annotatedResponses())
        .transform(AssessedResponse.Response)
        .toSet();

    // a system might include responses over unlinkable things, such as system responses
    // which get assessed incorrect. We delete these.
    final ResponseLinking filteredSystemLinking = systemLinking.copyWithFilteredResponses(
        Predicates.in(linkableResponses));

    final EventArgumentLinking referenceArgumentLinking =
        aligner.align(referenceLinking, answerKey);
    final EventArgumentLinking systemArgumentLinking =
        aligner.align(filteredSystemLinking, answerKey);

    return LinkingScore.fromFMeasureInfo(b3Scorer.score(systemArgumentLinking.linkedAsSet(),
        referenceArgumentLinking.linkedAsSet()));

  }
}
