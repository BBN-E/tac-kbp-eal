package com.bbn.kbp.events2014;

import java.util.Set;

import com.bbn.bue.common.symbols.Symbol;

import com.bbn.bue.common.symbols.SymbolUtils;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A system response for the KBP 2014 Event Argument task. Each response is a single event argument.
 * See the assessment guidelines for more details.
 */
public final class Response  {

    private Response(final Symbol docid, final Symbol type, final Symbol role,
		final KBPString canonicalArgumentString,
		final CharOffsetSpan baseFiller,
		final Set<CharOffsetSpan> argumentJustifications,
		final Set<CharOffsetSpan> predicateJustifications,
		final KBPRealis realis)
	{
		this.docid = checkNotNull(docid);
		this.type = checkNotNull(type);
		this.role = checkNotNull(role);
		this.realis = checkNotNull(realis);
		this.canonicalArgumentString = checkNotNull(canonicalArgumentString);
		this.baseFiller = checkNotNull(baseFiller);
		this.additionalArgumentJustifications = ImmutableSet.copyOf(argumentJustifications);
		this.predicateJustifications = ImmutableSet.copyOf(predicateJustifications);
		checkArgument(!predicateJustifications.isEmpty(), "Predicate justifications may not be empty");
	 }

	public static Response createFrom(final Symbol docid, final Symbol type, final Symbol role,
			final KBPString canonicalArgumentString, final CharOffsetSpan baseFiller,
			final Set<CharOffsetSpan> argumentJustifications,
			final Set<CharOffsetSpan> predicateJustifications, final KBPRealis realis)
	{
		return new Response(docid, type, role, canonicalArgumentString, baseFiller,
			argumentJustifications, predicateJustifications, realis);
	}

    /**
     * Returns a 'unique-ish' ID for this response. In the Java implementation,
     * the response's {@code hashCode} is always returned.  Note that if you read input files
     * from another source into {@code Response} objects, the original IDs will be lost.
     * @return
     */
    public int responseID() {
        return hashCode();
    }

    /**
     * The document containing this event argument.
     * @return
     */
	public Symbol docID() {
		return docid;
	}

    /**
     * The event type.
     */
	public Symbol type() {
		return type;
	}

    /**
     * The event role.
     * @return
     */
	public Symbol role() {
		return role;
	}

    /**
     * The canonical argument.
     * @return
     */
	public KBPString canonicalArgument() {
		return canonicalArgumentString;
	}

    /**
     * The set of additional argument justifications.
     * @return
     */
	public Set<CharOffsetSpan> additionalArgumentJustifications() {
		return additionalArgumentJustifications;
	}

    /**
     * The set of predicate justifications. Guaranteed to be non-empty.
     * @return
     */
	public Set<CharOffsetSpan> predicateJustifications() {
		return predicateJustifications;
	}

    /**
     * The realis of the event argument.
     * @return
     */
	public KBPRealis realis() {
		return realis;
	}

    /**
     * The base filler.
     * @return
     */
	public CharOffsetSpan baseFiller() {
		return baseFiller;
	}

    private static final ImmutableSet<Symbol> temporalRoles = SymbolUtils.setFrom("TIME", "Time");

    /**
     * Is the role {@code Time} or {@code TIME}?
     * @return
     */
    public boolean isTemporal() {
        return temporalRoles.contains(role());
    }

    /**
     * Creates a new response which is the same as this one, except it changes the
     * canonical argument.
     *
     * @param alternateCAS
     * @return
     */
	public Response copyWithSwappedCanonicalArgument(final KBPString alternateCAS) {
		if (canonicalArgumentString.equals(alternateCAS)) {
			return this;
		}

		return Response.createFrom(docid, type, role, alternateCAS, baseFiller,
			additionalArgumentJustifications, predicateJustifications, realis);
	}

    /**
    * Creates a new response which is the same as this one, except it changes the
    * realis.
    */
	public Response copyWithSwappedRealis(final KBPRealis alternateRealis) {
		if (realis.equals(alternateRealis)) {
			return this;
		}

		return Response.createFrom(docid, type, role, canonicalArgumentString, baseFiller,
			additionalArgumentJustifications, predicateJustifications, alternateRealis);
	}

	private final Symbol docid;
	private final Symbol type;
	private final Symbol role;
	private final KBPString canonicalArgumentString;
	private final CharOffsetSpan baseFiller;
	private final Set<CharOffsetSpan> additionalArgumentJustifications;
	private final Set<CharOffsetSpan> predicateJustifications;
	private final KBPRealis realis;

	@Override
	public int hashCode() {
		return Objects.hashCode(docid.toString(), type.toString(), role.toString(), canonicalArgumentString, baseFiller,
			additionalArgumentJustifications, predicateJustifications, realis.stableHashCode());
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
		final Response other = (Response) obj;
		return Objects.equal(docid, other.docid)
			&& Objects.equal(type, other.type)
			&& Objects.equal(role, other.role)
			&& Objects.equal(canonicalArgumentString, other.canonicalArgumentString)
			&& Objects.equal(baseFiller,other.baseFiller)
			&& Objects.equal(realis, other.realis)
			&& Objects.equal(additionalArgumentJustifications, other.additionalArgumentJustifications)
			&& Objects.equal(predicateJustifications, other.predicateJustifications);
	}

	@Override
	public String toString() {
		return String.format("%s-%s-%s(%s[%s]; %s; %s; %s)", docid, type, role,
			canonicalArgumentString, baseFiller, realis, predicateJustifications, additionalArgumentJustifications);
	}

    /**
     * Orders responses by
     *   <ol><li>the document ID</li>
     *   <li>the justifications, with earlier justifications coming first</li>
     *   <li>The base filler location</li>
     *   <li>the canonical argument location</li>
     *   <li>the realis value</li>
     *   </ol>
     */
	public static final Ordering<Response> ByJustificationLocation = new Ordering<Response> () {
		@Override
		public int compare(final Response left, final Response right) {
			return ComparisonChain.start()
				.compare(left.docID().toString(), right.docID().toString())
				.compare(Ordering.natural().sortedCopy(left.predicateJustifications()),
					Ordering.natural().sortedCopy(right.predicateJustifications()),
					Ordering.<CharOffsetSpan>natural().lexicographical())
				.compare(left.baseFiller(), right.baseFiller())
				.compare(left.canonicalArgument().charOffsetSpan(), right.canonicalArgument().charOffsetSpan())
				.compare(left.realis, right.realis)
				.result();
		}
	};

    public static final Function<Response,Symbol> DocID = new Function<Response, Symbol> () {
        @Override
        public Symbol apply(final Response x) {
            return x.docID();
        }
    };
    public static Function<Response, Integer> ResponseID = new Function<Response, Integer> () {
        @Override
        public Integer apply(Response x) {
            return x.responseID();
        }
    };

    /**
     * Orders responses by their IDs.
     */
    public static final Ordering<Response> ById = Ordering.natural().onResultOf(Response.ResponseID);
}