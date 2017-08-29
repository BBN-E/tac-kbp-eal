package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import org.immutables.func.Functional;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A system response for the KBP 2014 Event Argument task. Each response is a single event argument.
 * See the assessment guidelines for more details.
 */
@Value.Immutable
@Functional
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@TextGroupPublicImmutable
abstract class _Response {

  private static final Logger log = LoggerFactory.getLogger(Response.class);

  /**
   * The document containing this event argument.
   */
  @Value.Parameter
  public abstract Symbol docID();

  /**
   * The event type.
   */
  @Value.Parameter
  public abstract Symbol type();

  /**
   * The event role.
   */
  @Value.Parameter
  public abstract Symbol role();

  /**
   * The canonical argument.
   */
  @Value.Parameter
  public abstract KBPString canonicalArgument();

  /**
   * The base filler.
   */
  @Value.Parameter
  public abstract CharOffsetSpan baseFiller();

  /**
   * The set of additional argument justifications.
   */
  @Value.Parameter
  public abstract Set<CharOffsetSpan> additionalArgumentJustifications();

  /**
   * The set of predicate justifications. Guaranteed to be non-empty.
   */
  @Value.Parameter
  public abstract Set<CharOffsetSpan> predicateJustifications();

  /**
   * The realis of the event argument.
   */
  @Value.Parameter
  public abstract KBPRealis realis();

  /**
   * Returns a 'unique-ish' ID for this response used for the 2014 evaluation. In the Java
   * implementation, the response's {@code hashCode} is always returned.  Note that if you read
   * input files from another source into {@code Response} objects, the original IDs will be lost.
   */
  @Value.Derived
  public int old2014ResponseID() {
    return hashCode();
  }

  /**
   * Returns a unique ID for this response used for the 2015 and 2016 evaluations.
   */
  @Value.Derived
  public String uniqueIdentifier2015() {
    return hasher2016().hash().toString();
  }

  /**
   * Returns a unique ID for this response used for the 2016.
   */
  @Value.Derived
  public String uniqueIdentifier() {
    return computeSHA1Hash().toString();
  }

  @Value.Check
  protected void check() {
    checkArgument(!docID().asString().isEmpty(), "Document ID may not be empty for a response");
    checkArgument(!type().asString().isEmpty(), "Event type may not be empty for a response");
    checkArgument(!role().asString().isEmpty(), "Argument role may not be empty for a response");
    checkPredicateJustificationsContainsBaseFiller();
    checkArgument(!predicateJustifications().isEmpty(), "Predicate justifications may not be empty");
  }

  private static final ImmutableSet<Symbol> temporalRoles = SymbolUtils.setFrom("TIME", "Time");

  /**
   * Is the role {@code Time} or {@code TIME}?
   */
  public final boolean isTemporal() {
    return temporalRoles.contains(role());
  }

  // see computeSHA1Hash
  private static final int PJ_CODE = 0;
  private static final int AAJ_CODE = 1;

  private static final HashFunction SHA1_HASHER = Hashing.sha1();

  private HashCode computeSHA1Hash() {
    return hasher2016().hash();
  }

  private Hasher hasher2016() {
    final Hasher hasher = SHA1_HASHER.newHasher()
        .putString(docID().toString(), Charsets.UTF_8)
        .putString(type().toString(), Charsets.UTF_8)
        .putString(role().toString(), Charsets.UTF_8)
        .putString(canonicalArgument().string(), Charsets.UTF_8)
        .putInt(canonicalArgument().charOffsetSpan().startInclusive())
        .putInt(canonicalArgument().charOffsetSpan().endInclusive())
        .putInt(baseFiller().startInclusive())
        .putInt(baseFiller().endInclusive());

    // we put PJ_CODE and AAJ_CODE into the hash because without them,
    // observe that shifting a second PJ element to being the first AAJ
    // element results in the same hash
    hasher.putInt(PJ_CODE);
    for (final CharOffsetSpan pj : Ordering.natural().sortedCopy(predicateJustifications())) {
      hasher.putInt(pj.startInclusive()).putInt(pj.endInclusive());
    }

    hasher.putInt(AAJ_CODE);
    for (final CharOffsetSpan aaj : Ordering.natural().sortedCopy(
        additionalArgumentJustifications())) {
      hasher.putInt(aaj.startInclusive()).putInt(aaj.endInclusive());
    }

    hasher.putInt(realis().ordinal());

    return hasher;
  }

  @Override
  public final int hashCode() {
    return Objects.hashCode(docID().toString(), type().toString(), role().toString(),
        canonicalArgument(), baseFiller(),
        additionalArgumentJustifications(), predicateJustifications(), realis().stableHashCode());
  }

  @Override
  public final boolean equals(final Object obj) {
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
    return Objects.equal(docID(), other.docID())
        && Objects.equal(type(), other.type())
        && Objects.equal(role(), other.role())
        && Objects.equal(canonicalArgument(), other.canonicalArgument())
        && Objects.equal(baseFiller(), other.baseFiller())
        && Objects.equal(realis(), other.realis())
        && Objects.equal(additionalArgumentJustifications(), other.additionalArgumentJustifications())
        && Objects.equal(predicateJustifications(), other.predicateJustifications());
  }

  @Override
  public String toString() {
    return String.format("%s-%s-%s(%s[%s]; %s; %s; %s)", docID(), type(), role(),
        canonicalArgument(), baseFiller(), realis(), predicateJustifications(),
        additionalArgumentJustifications());
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
          .compare(left.realis(), right.realis())
          .result();
    }
  };


  public static final Ordering<Response> byEvent() {
    return Ordering.natural().onResultOf(SymbolUtils.desymbolizeFunction())
        .onResultOf(ResponseFunctions.type());
  }

  public static final Ordering<Response> byRole() {
    return Ordering.natural().onResultOf(SymbolUtils.desymbolizeFunction())
        .onResultOf(ResponseFunctions.role());
  }

  public static final Ordering<Response> byFillerString() {
    return new Ordering<Response>() {
      @Override
      public int compare(final Response left, final Response right) {
        CharOffsetSpan leftString = left.baseFiller();
        CharOffsetSpan rightString = right.baseFiller();
        if (leftString.string().isPresent() && rightString.string().isPresent()) {
          return leftString.string().get().compareTo(rightString.string().get());
        }
        return leftString.compareTo(rightString);
      }
    };
  }

  public static final Ordering<Response> byCASSttring() {
    return Ordering.natural().onResultOf(ResponseFunctions.canonicalArgument());
  }

  /**
   * @deprecated
   */
  @Deprecated
  public static final Ordering<Response> ByOld2014Id =
      Ordering.natural().onResultOf(ResponseFunctions.old2014ResponseID());

  public static Ordering<Response> byUniqueIdOrdering2015() {
    return Ordering.natural().onResultOf(ResponseFunctions.uniqueIdentifier2015());
  }

  public static final Ordering<Response> byUniqueIdOrdering() {
    return Ordering.natural().onResultOf(ResponseFunctions.uniqueIdentifier());
  }

  private void checkPredicateJustificationsContainsBaseFiller() {
    boolean foundContainingPJ = false;
    for (final CharOffsetSpan pj : predicateJustifications()) {
      if (pj.contains(baseFiller())) {
        foundContainingPJ = true;
        break;
      }
    }
    if (!foundContainingPJ) {
      log.warn(
          "For response {}, the predicate justification does not contain the base filler {}. " +
              "This is a warning rather than an exception for backward-compatibility.", this,
          baseFiller());
    }
  }
}
