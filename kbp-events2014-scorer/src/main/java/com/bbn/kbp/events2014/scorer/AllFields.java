package com.bbn.kbp.events2014.scorer;

import java.util.List;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.KBPRealis;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * Represents a KBP event argument response considered over all its fields (except confidence).
 * @author rgabbard
 *
 */
public final class AllFields {
	public static AllFields create(final Symbol docid, final Symbol type, final Symbol role,
			final KBPRealis realis,
			final String argumentCanonicalString, final List<CharOffsetSpan> argumentJustifications,
			final List<CharOffsetSpan> predicateJustifications)
	{

		return new AllFields(docid, type, role, realis, argumentCanonicalString,
			ImmutableList.copyOf(argumentJustifications), ImmutableList.copyOf(predicateJustifications));
	}


	@Override
	public int hashCode() {
		return Objects.hashCode(docid, type, role, realis, argumentCanonicalString, argumentJustifications, predicateJustifications);
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
		final AllFields other = (AllFields) obj;
		return Objects.equal(docid, other.docid)
				&& Objects.equal(type, other.type)
				&& Objects.equal(role, other.role)
				&& Objects.equal(realis, other.realis)
				&& Objects.equal(argumentCanonicalString, other.argumentCanonicalString)
				&& Objects.equal(argumentJustifications, other.argumentJustifications)
				&& Objects.equal(predicateJustifications, other.predicateJustifications);
	}

	private AllFields(final Symbol docid, final Symbol type, final Symbol role, final KBPRealis realis,
			final String argumentCanonicalString,
			final List<CharOffsetSpan> argumentJustifications, final List<CharOffsetSpan> predicateJustifications)
	{
		this.docid = docid;
		this.type = type;
		this.role = role;
		this.realis = realis;
		this.argumentCanonicalString = argumentCanonicalString;
		this.argumentJustifications = argumentJustifications;
		this.predicateJustifications = predicateJustifications;
	}

	private final Symbol docid;
	private final Symbol type;
	private final Symbol role;
	private final KBPRealis realis;
	private final String argumentCanonicalString;
	private final List<CharOffsetSpan> argumentJustifications;
	private final List<CharOffsetSpan> predicateJustifications;
}
