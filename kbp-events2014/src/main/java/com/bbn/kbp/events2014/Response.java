package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A system response for the KBP 2014 Event Argument task. Each response is a single event argument.
 * See the assessment guidelines for more details.
 */
public final class Response {

  private static final Logger log = LoggerFactory.getLogger(Response.class);

  private Response(final Symbol docid, final Symbol type, final Symbol role,
      final KBPString canonicalArgumentString,
      final CharOffsetSpan baseFiller,
      final Set<CharOffsetSpan> argumentJustifications,
      final Set<CharOffsetSpan> predicateJustifications,
      final KBPRealis realis) {
    this.docid = checkNotNull(docid);
    this.type = checkNotNull(type);
    this.role = checkNotNull(role);
    this.realis = checkNotNull(realis);
    this.canonicalArgumentString = checkNotNull(canonicalArgumentString);
    this.baseFiller = checkNotNull(baseFiller);
    this.additionalArgumentJustifications = ImmutableSet.copyOf(argumentJustifications);
    this.predicateJustifications = ImmutableSet.copyOf(predicateJustifications);
    checkPredicateJustificationsContainsBaseFiller();
    checkArgument(!predicateJustifications.isEmpty(), "Predicate justifications may not be empty");
    this.cachedSHA1Hash = computeSHA1Hash();
  }

  public static Response createFrom(final Symbol docid, final Symbol type, final Symbol role,
      final KBPString canonicalArgumentString, final CharOffsetSpan baseFiller,
      final Set<CharOffsetSpan> argumentJustifications,
      final Set<CharOffsetSpan> predicateJustifications, final KBPRealis realis) {
    return new Response(docid, type, role, canonicalArgumentString, baseFiller,
        argumentJustifications, predicateJustifications, realis);
  }

  /**
   * Returns a 'unique-ish' ID for this response used for the 2014 evaluation. In the Java
   * implementation, the response's {@code hashCode} is always returned.  Note that if you read
   * input files from another source into {@code Response} objects, the original IDs will be lost.
   */
  public int old2014ResponseID() {
    return hashCode();
  }

  /**
   * The document containing this event argument.
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
   */
  public Symbol role() {
    return role;
  }

  /**
   * The canonical argument.
   */
  public KBPString canonicalArgument() {
    return canonicalArgumentString;
  }

  /**
   * The set of additional argument justifications.
   */
  public Set<CharOffsetSpan> additionalArgumentJustifications() {
    return additionalArgumentJustifications;
  }

  /**
   * The set of predicate justifications. Guaranteed to be non-empty.
   */
  public Set<CharOffsetSpan> predicateJustifications() {
    return predicateJustifications;
  }

  /**
   * The realis of the event argument.
   */
  public KBPRealis realis() {
    return realis;
  }

  /**
   * The base filler.
   */
  public CharOffsetSpan baseFiller() {
    return baseFiller;
  }

  private static final ImmutableSet<Symbol> temporalRoles = SymbolUtils.setFrom("TIME", "Time");

  /**
   * Is the role {@code Time} or {@code TIME}?
   */
  public boolean isTemporal() {
    return temporalRoles.contains(role());
  }

  /**
   * Creates a new response which is the same as this one, except it changes the canonical
   * argument.
   */
  public Response copyWithSwappedCanonicalArgument(final KBPString alternateCAS) {
    if (canonicalArgumentString.equals(alternateCAS)) {
      return this;
    }

    return Response.createFrom(docid, type, role, alternateCAS, baseFiller,
        additionalArgumentJustifications, predicateJustifications, realis);
  }

  /**
   * Creates a new response which is the same as this one, except it changes the realis. If the
   * requested realis is the same as the current one, this same object is returned.
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
  private final HashCode cachedSHA1Hash;

  // see computeSHA1Hash
  private static final int PJ_CODE = 0;
  private static final int AAJ_CODE = 1;

  private static final HashFunction SHA1_HASHER = Hashing.sha1();

  private HashCode computeSHA1Hash() {
    final Hasher hasher = SHA1_HASHER.newHasher()
        .putString(docid.toString(), Charsets.UTF_8)
        .putString(type.toString(), Charsets.UTF_8)
        .putString(role.toString(), Charsets.UTF_8)
        .putString(canonicalArgumentString.string(), Charsets.UTF_8)
        .putInt(canonicalArgument().charOffsetSpan().startInclusive())
        .putInt(canonicalArgument().charOffsetSpan().endInclusive())
        .putInt(baseFiller.startInclusive())
        .putInt(baseFiller.endInclusive());

    // we put PJ_CODE and AAJ_CODE into the hash because without them,
    // observe that shifting a second PJ element to being the first AAJ
    // element results in the same hash
    hasher.putInt(PJ_CODE);
    for (final CharOffsetSpan pj : Ordering.natural().sortedCopy(predicateJustifications)) {
      hasher.putInt(pj.startInclusive()).putInt(pj.endInclusive());
    }

    hasher.putInt(AAJ_CODE);
    for (final CharOffsetSpan aaj : Ordering.natural().sortedCopy(
        additionalArgumentJustifications())) {
      hasher.putInt(aaj.startInclusive()).putInt(aaj.endInclusive());
    }

    hasher.putInt(realis.ordinal());

    return hasher.hash();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(docid.toString(), type.toString(), role.toString(),
        canonicalArgumentString, baseFiller,
        additionalArgumentJustifications, predicateJustifications, realis.stableHashCode());
  }

  public String uniqueIdentifier() {
    return cachedSHA1Hash.toString();
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
        && Objects.equal(baseFiller, other.baseFiller)
        && Objects.equal(realis, other.realis)
        && Objects.equal(additionalArgumentJustifications, other.additionalArgumentJustifications)
        && Objects.equal(predicateJustifications, other.predicateJustifications);
  }

  @Override
  public String toString() {
    return String.format("%s-%s-%s(%s[%s]; %s; %s; %s)", docid, type, role,
        canonicalArgumentString, baseFiller, realis, predicateJustifications,
        additionalArgumentJustifications);
  }

  /**
   * Orders responses by <ol><li>the document ID</li> <li>the justifications, with earlier
   * justifications coming first</li> <li>The base filler location</li> <li>the canonical argument
   * location</li> <li>the realis value</li> </ol>
   */
  public static final Ordering<Response> ByJustificationLocation = new Ordering<Response>() {
    @Override
    public int compare(final Response left, final Response right) {
      return ComparisonChain.start()
          .compare(left.docID().toString(), right.docID().toString())
          .compare(
              Ordering.natural().sortedCopy(left.predicateJustifications()),
              Ordering.natural().sortedCopy(right.predicateJustifications()),
              Ordering.<CharOffsetSpan>natural().lexicographical())
          .compare(left.baseFiller(), right.baseFiller())
          .compare(left.canonicalArgument().charOffsetSpan(),
              right.canonicalArgument().charOffsetSpan())
          .compare(left.realis, right.realis)
          .result();
    }
  };

  public static final Function<Response, Symbol> DocID = new Function<Response, Symbol>() {
    @Override
    public Symbol apply(final Response x) {
      return x.docID();
    }
  };
  /**
   * @deprecated
   */
  @Deprecated
  public static final Function<Response, Integer> Old2104ResponseID =
      new Function<Response, Integer>() {
        @Override
        public Integer apply(Response x) {
          return x.old2014ResponseID();
        }
      };

  public static Function<Response, String> uniqueIdFunction() {
    return new Function<Response, String>() {
      @Override
      public String apply(Response x) {
        return x.uniqueIdentifier();
      }
    };
  }

  /**
   * @deprecated
   */
  @Deprecated
  public static final Ordering<Response> ByOld2014Id =
      Ordering.natural().onResultOf(Response.Old2104ResponseID);

  public static final Ordering<Response> byUniqueIdOrdering() {
    return Ordering.natural().onResultOf(uniqueIdFunction());
  }

  private void checkPredicateJustificationsContainsBaseFiller() {
    boolean foundContainingPJ = false;
    for (final CharOffsetSpan pj : predicateJustifications()) {
      if (pj.contains(baseFiller)) {
        foundContainingPJ = true;
        break;
      }
    }
    if (!foundContainingPJ) {
      log.warn(
          "For response {}, the predicate justification does not contain the base filler {}. " +
              "This is a warning rather than an exception for backward-compatibility.", this,
          baseFiller);
    }
  }

  public static Function<Response, KBPString> CASFunction() {
    return new Function<Response, KBPString>() {
      @Override
      public KBPString apply(Response input) {
        return input.canonicalArgument();
      }
    };
  }

  public static Function<Response, Symbol> typeFunction() {
    return new Function<Response, Symbol>() {
      @Override
      public Symbol apply(Response input) {
        return input.type();
      }
    };
  }

  public static Function<Response, KBPRealis> realisFunction() {
    return new Function<Response, KBPRealis>() {
      @Override
      public KBPRealis apply(Response input) {
        return input.realis();
      }
    };
  }

  public static Function<Response, CharOffsetSpan> baseFillerFunction() {
    return new Function<Response, CharOffsetSpan>() {
      @Override
      public CharOffsetSpan apply(final Response input) {
        return input.baseFiller();
      }
    };
  }
}
