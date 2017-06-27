package com.bbn.kbp.events2014;


import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import org.immutables.func.Functional;
import org.immutables.value.Value;

/**
 * Represents an (event type, argument role, canonical filler string, realis) tuple
 * withAdditionalJustifications a KBP response.
 *
 * @author rgabbard
 */
@Value.Immutable(prehash = true)
@Functional
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@TextGroupPublicImmutable
abstract class _TypeRoleFillerRealis implements Comparable<TypeRoleFillerRealis> {

  @Value.Parameter
  public abstract Symbol docID();

  @Value.Parameter
  public abstract Symbol type();

  @Value.Parameter
  public abstract Symbol role();

  @Value.Parameter
  public abstract KBPRealis realis();

  @Value.Parameter
  public abstract KBPString argumentCanonicalString();

  @Value.Derived
  public String uniqueIdentifier() {
    return computeSHA1Hash().toString();
  }


  @Override
  public final String toString() {
    return String.format("%s:%s-%s[%s]/%s",
        docID(), type(), role(), argumentCanonicalString(), realis());
  }

  private static final HashFunction SHA1_HASHER = Hashing.sha1();

  private HashCode computeSHA1Hash() {
    final Hasher hasher = SHA1_HASHER.newHasher()
        .putString(docID().toString(), Charsets.UTF_8)
        .putString(type().toString(), Charsets.UTF_8)
        .putString(role().toString(), Charsets.UTF_8)
        .putString(argumentCanonicalString().string(), Charsets.UTF_8)
        .putInt(argumentCanonicalString().charOffsetSpan().startInclusive())
        .putInt(argumentCanonicalString().charOffsetSpan().endInclusive())
        .putInt(realis().ordinal());

    return hasher.hash();
  }

  public static Function<Response, TypeRoleFillerRealis> extractFromSystemResponse(
      final Function<KBPString, KBPString> CASNormalizer) {
    return new Function<Response, TypeRoleFillerRealis>() {
      @Override
      public TypeRoleFillerRealis apply(final Response arg) {
        return TypeRoleFillerRealis.of(arg.docID(), arg.type(), arg.role(), arg.realis(),
            CASNormalizer.apply(arg.canonicalArgument()));
      }
    };
  }

  /**
   * Function to turn a {@link com.bbn.kbp.events2014.Response} to its equivalence class if the
   * coreference information is known for the filler.  If it isn't, {@link
   * com.google.common.base.Optional#absent()} is returned.
   */
  public static Function<Response, Optional<TypeRoleFillerRealis>> extractFromSystemResponseIfPossible(
      final Function<KBPString, Optional<KBPString>> CASNormalizer) {
    return new Function<Response, Optional<TypeRoleFillerRealis>>() {
      @Override
      public Optional<TypeRoleFillerRealis> apply(final Response arg) {
        final Optional<KBPString> normalizedCAS = CASNormalizer.apply(arg.canonicalArgument());

        if (normalizedCAS.isPresent()) {
          return Optional.of(TypeRoleFillerRealis.of(arg.docID(), arg.type(),
              arg.role(), arg.realis(), normalizedCAS.get()));
        } else {
          return Optional.absent();
        }
      }
    };
  }

  public static TypeRoleFillerRealis fromSystemResponseUnnormalized(final Response r) {
    return TypeRoleFillerRealis
        .of(r.docID(), r.type(), r.role(), r.realis(), r.canonicalArgument());
  }

  @Override
  public int compareTo(final TypeRoleFillerRealis o) {
    return ComparisonChain.start()
        .compare(docID().toString(), o.docID().toString())
        .compare(type().toString(), o.type().toString())
        .compare(role().toString(), o.type().toString())
        .compare(realis(), o.realis())
        .compare(argumentCanonicalString(), argumentCanonicalString())
        .result();
  }

  public static Ordering<TypeRoleFillerRealis> byType() {
    return Ordering.natural().onResultOf(SymbolUtils.desymbolizeFunction())
        .onResultOf(TypeRoleFillerRealisFunctions.type());
  }

  public static Ordering<TypeRoleFillerRealis> byRole() {
    return Ordering.natural().onResultOf(SymbolUtils.desymbolizeFunction())
        .onResultOf(TypeRoleFillerRealisFunctions.role());
  }

  public static Ordering<TypeRoleFillerRealis> byCAS() {
    return new Ordering<TypeRoleFillerRealis>() {
      @Override
      public int compare(final TypeRoleFillerRealis left, final TypeRoleFillerRealis right) {
        return left.argumentCanonicalString().string().compareTo(right.argumentCanonicalString().string());
      }
    };
  }

  public static Ordering<TypeRoleFillerRealis> byRealis() {
    return new Ordering<TypeRoleFillerRealis>() {
      @Override
      public int compare(final TypeRoleFillerRealis left, final TypeRoleFillerRealis right) {
        return left.realis().name().compareTo(right.realis().name());
      }
    };
  }
}

