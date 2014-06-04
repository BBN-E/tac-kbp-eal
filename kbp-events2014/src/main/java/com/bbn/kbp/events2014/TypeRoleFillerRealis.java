package com.bbn.kbp.events2014;


import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents an (event type, argument role, canonical filler string, realis) tuple withAdditionalJustifications a KBP response.
 * @author rgabbard
 *
 */
public final class TypeRoleFillerRealis implements Comparable<TypeRoleFillerRealis> {
    public static TypeRoleFillerRealis create(final Symbol docid, final Symbol type, final Symbol role,
			final KBPRealis realis, final KBPString argumentCanonicalString)
	{
		return new TypeRoleFillerRealis(checkNotNull(docid), checkNotNull(type), checkNotNull(role),
			checkNotNull(realis), checkNotNull(argumentCanonicalString));
	}

	public Symbol docID() {
		return docid;
	}

	public Symbol type() {
		return type;
	}

	public Symbol role() {
		return role;
	}

	public KBPRealis realis() {
		return realis;
	}

	public KBPString argumentCanonicalString() {
		return argumentCanonicalString;
	}

	public TypeRoleFillerRealis copyWithRealis(final KBPRealis alternateRealis) {
		return new TypeRoleFillerRealis(docid, type, role, alternateRealis, argumentCanonicalString);
	}

    public TypeRoleFillerRealis copyWithCAS(KBPString newCAS) {
        return new TypeRoleFillerRealis(docid, type, role, realis, newCAS);
    }

	@Override
	public int hashCode() {
		return Objects.hashCode(argumentCanonicalString, docid.toString(), role.toString(), type.toString(),
			realis);
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
		final TypeRoleFillerRealis other = (TypeRoleFillerRealis) obj;
		return Objects.equal(docid, other.docid) &&
				Objects.equal(type, other.type) &&
				Objects.equal(role, other.role) &&
				Objects.equal(realis, other.realis) &&
				Objects.equal(argumentCanonicalString, other.argumentCanonicalString);
	}

	@Override
	public String toString() {
		return String.format("%s:%s-%s[%s]/%s",
			docid, type, role, argumentCanonicalString, realis);
	}

	private TypeRoleFillerRealis(final Symbol docid, final Symbol type, final Symbol role,
			final KBPRealis realis, final KBPString argumentCanonicalString)
	{
		this.docid = docid;
		this.type = type;
		this.role = role;
		this.realis = realis;
		this.argumentCanonicalString = argumentCanonicalString;
	}

	private final Symbol docid;
	private final Symbol type;
	private final Symbol role;
	private final KBPRealis realis;
	private final KBPString argumentCanonicalString;

	public static Function<Response, TypeRoleFillerRealis> extractFromSystemResponse(final EntityNormalizer entityNormalizer) {
		return new Function<Response, TypeRoleFillerRealis>() {
			@Override
			public TypeRoleFillerRealis apply(final Response arg) {
				return TypeRoleFillerRealis.create(arg.docID(), arg.type(), arg.role(), arg.realis(),
					entityNormalizer.normalizeFiller(arg.docID(), arg.canonicalArgument()));
			}
		};
	}

    public static TypeRoleFillerRealis fromSystemResponseUnnormalized(final Response r) {
        return TypeRoleFillerRealis.create(r.docID(), r.type(), r.role(), r.realis(), r.canonicalArgument());
    }

	@Override
	public int compareTo(final TypeRoleFillerRealis o) {
		return ComparisonChain.start()
			.compare(docid.toString(),  o.docid.toString())
			.compare(type.toString(), o.type.toString())
			.compare(role.toString(), o.type.toString())
			.compare(realis, o.realis)
			.compare(argumentCanonicalString.toString(), argumentCanonicalString.toString())
			.result();
	}

    public static Function<TypeRoleFillerRealis,Symbol> Type = new Function<TypeRoleFillerRealis, Symbol>() {
        @Override
        public Symbol apply(TypeRoleFillerRealis input) {
            return input.type();
        }
    };


}
