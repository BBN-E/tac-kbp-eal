package com.bbn.kbp.events2014;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The assessments available for a KBP response.  This is obsolete in the 2016 evaluation,
 * since assessments are no longer used.
 *
 * @author rgabbard
 */
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@com.bbn.bue.common.TextGroupPublicImmutable
@Value.Immutable
@Functional
abstract class _ResponseAssessment {

  @Value.Parameter
  public abstract Optional<FieldAssessment> justificationSupportsEventType();

  @Value.Parameter
  public abstract Optional<FieldAssessment> justificationSupportsRole();

  @Value.Parameter
  public abstract Optional<FieldAssessment> entityCorrectFiller();

  @Value.Parameter
  public abstract Optional<KBPRealis> realis();

  /**
   * E) Is the entity mentions string (column 6) a correct filler for the event-type/role tuple?
   * (N/A if A and/or B is marked as wrong)
   */
  @Value.Parameter
  public abstract Optional<FieldAssessment> baseFillerCorrect();

  @Value.Parameter
  public abstract Optional<FillerMentionType> mentionTypeOfCAS();


  private static final ImmutableSet<FieldAssessment> CWL = ImmutableSet.of(FieldAssessment.CORRECT,
      FieldAssessment.INCORRECT, FieldAssessment.INEXACT);

  @Value.Check
  protected void checkPreconditions() {
    checkArgument(!justificationSupportsEventType().isPresent()
        || CWL.contains(justificationSupportsEventType().get()));

    if (justificationSupportsEventType().isPresent() && justificationSupportsEventType().get()
        .isAcceptable()) {
      checkArgument(justificationSupportsRole().isPresent());
      checkArgument(CWL.contains(justificationSupportsRole().get()));
    }

    // the filler and realis judgements should be present iff the event type and
    // role judgements are not incorrect
    final boolean wrong = !justificationSupportsEventType().isPresent()
        || justificationSupportsEventType().get() == FieldAssessment.INCORRECT
        || justificationSupportsRole().get() == FieldAssessment.INCORRECT;
    if (wrong) {
      checkArgument(!entityCorrectFiller().isPresent());
      checkArgument(!baseFillerCorrect().isPresent());
      checkArgument(!realis().isPresent());
      checkArgument(!mentionTypeOfCAS().isPresent());
    } else {
      checkArgument(entityCorrectFiller().isPresent());
      checkArgument(baseFillerCorrect().isPresent());
      checkArgument(realis().isPresent());
      checkArgument(mentionTypeOfCAS().isPresent());
    }
  }


  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();

    sb.append("[");
    if (justificationSupportsEventType().isPresent()) {
      sb.append("t=").append(justificationSupportsEventType().get());
    } else {
      sb.append("IGNORE");
    }
    if (justificationSupportsRole().isPresent()) {
      sb.append("; r=").append(justificationSupportsRole().get());
    }
    if (entityCorrectFiller().isPresent()) {
      sb.append("; CAS=").append(entityCorrectFiller().get());
    }
    if (realis().isPresent()) {
      sb.append("; rs=").append(realis().get());
    }
    if (baseFillerCorrect().isPresent()) {
      sb.append("; bF=").append(baseFillerCorrect().get());
    }
    if (mentionTypeOfCAS().isPresent()) {
      sb.append("; mt=").append(mentionTypeOfCAS().get());
    }
    sb.append("]");
    return sb.toString();
  }
}
