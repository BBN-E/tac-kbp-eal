package com.bbn.kbp.events2014;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The annotations available for a KBP response.
 *
 * @author rgabbard
 *
 */
public final class ResponseAssessment {

    public enum MentionType {
		NOMINAL, NAME;

		public static String stringOrNil(final Optional<MentionType> mt) {
			if (mt.isPresent()) {
				return mt.get().toString();
			} else {
				return "NIL";
			}
		}

		public static Optional<MentionType> parseOptional(final String s) {
			if (s.equals("NIL")) {
				return Optional.absent();
			} else {
				return Optional.of(MentionType.valueOf(s));
			}
		}
	}

	private final FieldAssessment justificationSupportsEventType;
	private final FieldAssessment justificationSupportsRole;
	private final FieldAssessment entityCorrectFiller;
	private final FieldAssessment mentionCorrectFiller;
	private final KBPRealis realis;
	private final MentionType mentionTypeOfCAS;

	public Optional<FieldAssessment> justificationSupportsEventType() {
		return Optional.fromNullable(justificationSupportsEventType);
	}

	public Optional<FieldAssessment> justificationSupportsRole() {
		return Optional.fromNullable(justificationSupportsRole);
	}

	public Optional<FieldAssessment> entityCorrectFiller() {
		return Optional.fromNullable(entityCorrectFiller);
	}

	/**
	 * E) Is the entity mentions string (column 6) a correct filler for the event-type/role tuple? (N/A if A and/or B is marked as wrong)
	 */
	public Optional<FieldAssessment> baseFillerCorrect() {
		return Optional.fromNullable(mentionCorrectFiller);
	}

	public Optional<KBPRealis> realis() {
		return Optional.fromNullable(realis);
	}


	public Optional<MentionType> mentionTypeOfCAS() {
		return Optional.fromNullable(mentionTypeOfCAS);
	}


	private static final ImmutableSet<FieldAssessment> CWL = ImmutableSet.of(FieldAssessment.CORRECT,
		FieldAssessment.INCORRECT, FieldAssessment.INEXACT);

	private ResponseAssessment(final FieldAssessment justificationSupportsEventType,
                               final FieldAssessment justificationSupportsRole,
                               final FieldAssessment entityCorrectFiller,
                               final KBPRealis realis,
                               final FieldAssessment baseFillerCorrectFiller,
                               final MentionType casMentionType)
	{
		this.justificationSupportsEventType = justificationSupportsEventType;
		checkArgument(justificationSupportsEventType == null
                || CWL.contains(justificationSupportsEventType));

		this.justificationSupportsRole = justificationSupportsRole;
		if (justificationSupportsEventType != null && justificationSupportsEventType.isAcceptable()) {
			checkNotNull(justificationSupportsRole);
			checkArgument(CWL.contains(justificationSupportsRole));
		}

		this.entityCorrectFiller = entityCorrectFiller;

		this.realis = realis;


		this.mentionCorrectFiller = baseFillerCorrectFiller;
		this.mentionTypeOfCAS = casMentionType;

		// the filler and realis judgements should be present iff the event type and
		// role judgements are not incorrect
		final boolean wrong = justificationSupportsEventType == null
                || justificationSupportsEventType == FieldAssessment.INCORRECT
				|| justificationSupportsRole == FieldAssessment.INCORRECT;
		if (wrong) {
			checkArgument(entityCorrectFiller == null);
			checkArgument(baseFillerCorrectFiller == null);
			checkArgument(realis == null);
			checkArgument(casMentionType == null);
		} else {
			checkNotNull(entityCorrectFiller);
			checkNotNull(baseFillerCorrectFiller);
			checkNotNull(realis);
			checkNotNull(casMentionType);
		}
	}

	public static ResponseAssessment create(final Optional<FieldAssessment> justificationSupportsEventType,
			final Optional<FieldAssessment> justificationSupportsFiller,
			final Optional<FieldAssessment> entityCorrectFiller,
			final Optional<KBPRealis> realis,
			final Optional<FieldAssessment> mentionCorrectFiller,
			final Optional<MentionType> mentionTypeOfCAS)
	{
		return new ResponseAssessment(justificationSupportsEventType.orNull(), justificationSupportsFiller.orNull(),
			entityCorrectFiller.orNull(), realis.orNull(),
			mentionCorrectFiller.orNull(), mentionTypeOfCAS.orNull());
	}

    public ResponseAssessment copyWithModifiedCASAssessment(FieldAssessment newCASAssessment) {
        return create(justificationSupportsEventType(), justificationSupportsRole(),
                Optional.of(newCASAssessment), realis(), baseFillerCorrect(),
                mentionTypeOfCAS());
    }

    public ResponseAssessment copyWithModifiedRealisAssessment(Optional<KBPRealis> newRealis) {
        if (newRealis.orNull() == realis) {
            return this;
        }

        return create(justificationSupportsEventType(), justificationSupportsRole(),
                entityCorrectFiller(), newRealis, baseFillerCorrect(),
                mentionTypeOfCAS());
    }


    @Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();

        sb.append("[");
        if (justificationSupportsEventType != null) {
            sb.append("t=").append(justificationSupportsEventType);
        } else {
            sb.append("IGNORE");
        }
		if (justificationSupportsRole != null) {
			sb.append("; r=").append(justificationSupportsRole);
		}
		if (entityCorrectFiller != null) {
			sb.append("; CAS=").append(entityCorrectFiller);
		}
		if (realis != null) {
			sb.append("; rs=").append(realis);
		}
		if (mentionCorrectFiller != null) {
			sb.append("; bF=").append(mentionCorrectFiller);
		}
		if (mentionTypeOfCAS != null) {
			sb.append("; mt=").append(mentionTypeOfCAS);
		}
		sb.append("]");
		return sb.toString();
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(entityCorrectFiller,
			justificationSupportsEventType,justificationSupportsRole,
			mentionCorrectFiller,
			realis, mentionTypeOfCAS);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ResponseAssessment other = (ResponseAssessment) obj;
		if (entityCorrectFiller != other.entityCorrectFiller) {
			return false;
		}
		if (justificationSupportsEventType != other.justificationSupportsEventType) {
			return false;
		}
		if (justificationSupportsRole != other.justificationSupportsRole) {
			return false;
		}
		if (mentionCorrectFiller != other.mentionCorrectFiller) {
			return false;
		}
		if (realis != other.realis) {
			return false;
		}
		if (mentionTypeOfCAS != other.mentionTypeOfCAS) {
			return false;
		}
		return true;
	}
}
