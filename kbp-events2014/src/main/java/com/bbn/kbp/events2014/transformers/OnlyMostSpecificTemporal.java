package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.KBPTIMEXExpression;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the rule for scoring temporal arguments as described below:
 *
 * We would like to make the following proposed change to the assessment and scoring of *Time*
 * arguments.
 *
 * (1) Systems are required to report CAS for Time arguments in the XXXX-XX-XX timex format (and not
 * the week-based format). Note: This was already true. There is pretty good library support for
 * doing so, see for example: Java: http://www.joda.org/joda-time/ Python:
 * https://docs.python.org/2.7/library/datetime.html(standard library) Haskell:
 * http://hackage.haskell.org/package/datetime-0.1
 *
 * (2) Assessmentwill judge the context of the temporal CAS in the context as specified in the
 * current guidelines.
 *
 * (3) LDC will *not* do coreference of temporal arguments
 *
 * (4) The scoring metric will 'ignore' times that are subsumed by more specific times for
 * (EventType, Role, CAS, Realis) tuples. That is given correct answerswith: R1:
 * (Transport.Movement, Time, 2014-04-XX, Actual) R2: (Transport.Movement, Time, 2014-XX-XX,
 * Actual)
 *
 * R1 will be 'required' to achieve 100% recall; R2 will be ignored (i.e. not treated as a FA, but
 * also not sufficient to match R1).
 *
 * For participants, this will raise the importance of finding a correct, and most specific date
 * (and in cases where the date is given as e.g. the 15th week of 2014, systems will have to do the
 * temporal resolution.)
 */
public final class OnlyMostSpecificTemporal implements Function<SystemOutput, SystemOutput>,
    ResponseMappingRule {

  private static final Logger log = LoggerFactory.getLogger(OnlyMostSpecificTemporal.class);
  private final ImmutableSet<TypeRoleFillerRealis> bannedResponseSignatures;

  private OnlyMostSpecificTemporal(Iterable<TypeRoleFillerRealis> bannedResponseSignatures) {
    this.bannedResponseSignatures = ImmutableSet.copyOf(bannedResponseSignatures);
  }

  @Deprecated
  public static OnlyMostSpecificTemporal forAnswerKey(AnswerKey key) {
    final ImmutableSet<TypeRoleFillerRealis> bannedResponseSignatures =
        computeBannedResponseSignatures(key);

    return new OnlyMostSpecificTemporal(bannedResponseSignatures);
  }

  private static ImmutableSet<TypeRoleFillerRealis> computeBannedResponseSignatures(
      final AnswerKey key) {
    final ImmutableSet.Builder<TypeRoleFillerRealis> bannedResponseSignatures =
        ImmutableSet.builder();

    for (final AssessedResponse response : key.annotatedResponses()) {
      if (response.assessment().entityCorrectFiller().isPresent()
          && response.assessment().entityCorrectFiller().get().isAcceptable()
          && response.response().isTemporal()) {
        try {
          final KBPTIMEXExpression time = KBPTIMEXExpression.parseTIMEX(
              response.response().canonicalArgument().string());

          final TypeRoleFillerRealis responseSignature = responseSignature(response.response());

          for (final KBPTIMEXExpression lessSpecificTimex : time.lessSpecificCompatibleTimes()) {
            bannedResponseSignatures.add(responseSignature.copyWithCAS(
                KBPString.from(lessSpecificTimex.toString(), DUMMY_OFFSETS)));
          }
        } catch (KBPTIMEXExpression.KBPTIMEXException timexException) {
          log.warn(
              "While applying only-most-specific-temporal rule, encountered an illegal temporal "
                  + "expression " + response.response().canonicalArgument().string()
                  + " which was evaluated as "
                  + "correct. Such responses should have incorrect CAS assessments.");
        }

      }
    }
    return bannedResponseSignatures.build();
  }

  private static TypeRoleFillerRealis responseSignature(Response response) {
    final TypeRoleFillerRealis responseSignatureWithOffsets =
        TypeRoleFillerRealis.fromSystemResponseUnnormalized(response);
    return responseSignatureWithOffsets
        .copyWithCAS(copyWithDummyOffsets(responseSignatureWithOffsets.argumentCanonicalString()));
  }

  private static final CharOffsetSpan DUMMY_OFFSETS = CharOffsetSpan.fromOffsetsOnly(0, 0);

  private static KBPString copyWithDummyOffsets(KBPString s) {
    return KBPString.from(s.string(), DUMMY_OFFSETS);
  }

  @Deprecated
  @Override
  public SystemOutput apply(SystemOutput input) {
    final ImmutableList.Builder<Scored<Response>> newResponses = ImmutableList.builder();

    boolean filteredAny = false;
    for (final Scored<Response> scoredResponse : input.scoredResponses()) {
      if (!bannedResponseSignatures.contains(responseSignature(scoredResponse.item()))) {
        newResponses.add(scoredResponse);
      } else {
        filteredAny = true;
        log.info("Temporal system response {} is being removed because answer key contains" +
            "a more specific correct time.", scoredResponse.item());
      }
    }

    if (filteredAny) {
      return SystemOutput.from(input.docId(), newResponses.build());
    } else {
      return input;
    }
  }


  @Override
  public ResponseMapping computeResponseTransformation(final AnswerKey answerKey) {
    final ImmutableSet<TypeRoleFillerRealis> bannedResponseSignatures =
        computeBannedResponseSignatures(answerKey);

    final ImmutableSet.Builder<Response> toDelete = ImmutableSet.builder();
    for (final Response response : answerKey.allResponses()) {
      if (bannedResponseSignatures.contains(responseSignature(response))) {
        toDelete.add(response);
      }
    }

    return ResponseMapping.create(ImmutableMap.<Response, Response>of(), toDelete.build());
  }
}
