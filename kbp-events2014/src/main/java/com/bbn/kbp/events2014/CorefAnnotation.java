package com.bbn.kbp.events2014;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.google.common.base.Preconditions.*;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Multisets.copyHighestCountFirst;

public final class CorefAnnotation {
    private static final Logger log = LoggerFactory.getLogger(CorefAnnotation.class);

    private final Symbol docId;
    private final ImmutableMultimap<Integer, KBPString> idToCASes;
    private final ImmutableMap<KBPString, Integer> CASesToIDs;
    private final ImmutableSet<KBPString> unannotated;

    private CorefAnnotation(final Symbol docId, final Multimap<Integer, KBPString> idToCASes,
                            final Map<KBPString, Integer> CASesToIDs, final Set<KBPString> unannotated)
    {
        this.docId = checkNotNull(docId);
        this.idToCASes = ImmutableMultimap.copyOf(idToCASes);
        this.CASesToIDs = ImmutableMap.copyOf(CASesToIDs);
        this.unannotated = ImmutableSet.copyOf(unannotated);
        checkConsistency();
    }

    private void checkConsistency() {
        for (final Map.Entry<KBPString, Integer> entry : CASesToIDs.entrySet()) {
            final KBPString CAS = entry.getKey();
            final Integer corefID = entry.getValue();
            checkState(idToCASes.get(corefID).contains(CAS));
        }
        for (final Map.Entry<Integer, KBPString> entry : idToCASes.entries()) {
            final KBPString CAS = entry.getValue();
            final Integer corefID = entry.getKey();
            checkState(CASesToIDs.containsKey(CAS) && corefID.equals(CASesToIDs.get(CAS)));
        }
        checkState(Sets.intersection(CASesToIDs.keySet(), unannotated).isEmpty(),
                "Same CAS may not be both annotated and unannotated");
    }


    public static CorefAnnotation create(final Symbol docId, final Map<KBPString, Integer> CASesToIds,
                                         Set<KBPString> unannotated)
    {
        return new CorefAnnotation(docId, reverse(CASesToIds), CASesToIds, unannotated);
    }

    private static Multimap<Integer, KBPString> reverse(Map<KBPString, Integer> CASesToIds) {
        return Multimaps.invertFrom(
                Multimaps.forMap(CASesToIds),
                ArrayListMultimap.<Integer,KBPString>create());
    }

    public ImmutableSet<KBPString> annotatedCASes() {
        return CASesToIDs.keySet();
    }

    public ImmutableSet<KBPString> unannotatedCASes() {
        return unannotated;
    }

    public Optional<Integer> corefId(KBPString cas) {
        if (CASesToIDs.containsKey(cas)) {
            return Optional.of(CASesToIDs.get(cas));
        }
        return Optional.absent();
    }

    public boolean isComplete() {
        return unannotated.isEmpty();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(docId, idToCASes, CASesToIDs, unannotated);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final CorefAnnotation other = (CorefAnnotation) obj;
        return Objects.equal(this.docId, other.docId) && Objects.equal(this.idToCASes, other.idToCASes) && Objects.equal(this.CASesToIDs, other.CASesToIDs) && Objects.equal(this.unannotated, other.unannotated);
    }

    public static CorefAnnotation createEmpty(Symbol docid) {
        return create(docid, ImmutableMap.<KBPString, Integer>of(),
                ImmutableSet.<KBPString>of());
    }

    public static Builder strictBuilder(Symbol docId) {
        return new Builder(docId, false);
    }

    public static Builder laxBuilder(Symbol docId) {
        return new Builder(docId, true);
    }

    public Builder strictCopyBuilder() {
        final Builder ret = strictBuilder(docId);
        for (final KBPString unannotated : unannotatedCASes()) {
            ret.addUnannotatedCAS(unannotated);
        }
        for (final Map.Entry<KBPString, Integer> entry : CASesToIDs.entrySet()) {
            ret.corefCAS(entry.getKey(), entry.getValue());
        }
        return ret;
    }

    public CorefAnnotation copyWithUnannotatedAsSingletons(Random rng) {
        // if we had a huge number of coref clusters, this could take a very long
        // time, but in such a case you have bigger problems...
        final Builder builder = strictCopyBuilder();
        for (final KBPString cas : unannotatedCASes()) {
            builder.putInNewRandomCluster(cas, rng);
        }
        return builder.build();
    }

    public Symbol docId() {
        return docId;
    }

    public CorefAnnotation copyRemovingStringsNotIn(Set<KBPString> toKeep) {
        final Predicate<KBPString> keep = in(toKeep);
        return create(docId(), Maps.filterKeys(CASesToIDs, keep),
                Sets.filter(unannotatedCASes(), keep));
    }

    public CorefAnnotation copyMerging(CorefAnnotation toMerge) {
        checkArgument(docId == toMerge.docId());
        // (1) initialize our mapping of CASes to coref indices with the
        // mappings seen in *baseline*. This is used to track the mappings during
        // construction, but ret (below) is used to construct the result
        final Map<KBPString, Integer> newCASToCoref = Maps.newHashMap(CASesToIDs);
        final Builder ret = strictCopyBuilder();

        // the next empty index for adding a new coref cluster
        // this potentially could fail due to overflow, but the chances are tiny and
        // the stakes are low, so we won't worry about it
        int nextIndex = newCASToCoref.isEmpty()?1: (1+ Collections.max(newCASToCoref.values()));

        // (2) gather a map of coref IDs to the CASes in the corresponding cluster
        // from *additional*
        final Multimap<Integer, KBPString> additionalCorefToCAS = toMerge.idToCASes;

        for (final Map.Entry<KBPString,Integer> newCorefRelation : toMerge.CASesToIDs.entrySet()) {
            final int corefId = newCorefRelation.getValue();
            final KBPString CAS = newCorefRelation.getKey();

            if (!newCASToCoref.containsKey(CAS)) {
                // this CAS was not previously coreffed
                final StringBuilder msg = new StringBuilder();

                // use the baseline cluster which contains most  clustermates of CAS
                // in "additional"
                final Collection<KBPString> clusterMates = additionalCorefToCAS.get(
                        corefId);
                msg.append("\t\tFor CAS ").append(CAS).append(" no coref ID was found in baseline. It has ")
                        .append(clusterMates.size()).append(" clustermates\n");

                // We use an ImmutableMultiset to have deterministic iteration order
                // and therefore deterministic tie-breaking if two clusters tie
                // on clustermate count
                final ImmutableMultiset.Builder<Integer> bCorefIDsOfClusterMatesInBaseline = ImmutableMultiset.builder();
                for (final KBPString clusterMate : clusterMates) {
                    final Integer corefForClusterMate = newCASToCoref.get(clusterMate);
                    if (corefForClusterMate != null) {
                        // we don't have to worry about iterating over the target CAS itself
                        // because we know it will never pass this check
                        bCorefIDsOfClusterMatesInBaseline.add(corefForClusterMate);
                        msg.append("\t\t\t").append(clusterMate).append(" ---> ")
                                .append(corefForClusterMate).append("\n");
                    } else {
                        msg.append("\t\t\t").append(clusterMate).append(" ---> unknown\n");
                    }
                }

                final ImmutableMultiset<Integer> corefIDsOfClusterMatesInBaseline =
                        bCorefIDsOfClusterMatesInBaseline.build();

                final int newCorefIdx;
                if (!corefIDsOfClusterMatesInBaseline.isEmpty()) {
                    // we know this will be non-null due to the above check
                    newCorefIdx  = getFirst(copyHighestCountFirst(corefIDsOfClusterMatesInBaseline), null);
                    msg.append("\t\tMapping to dominant cluster mate cluster ").append(newCorefIdx).append("\n");
                } else {
                    // if we had no clustermates with known coref indices, start a new cluster.
                    // When it comes time to assign coref to our clustermates, if any,
                    // then we know they will have at least one clustermate with a known coref ID
                    newCorefIdx = nextIndex;
                    nextIndex = nextFreeIndex(nextIndex, newCASToCoref);
                    msg.append("\t\tMapping to new cluster ").append(newCorefIdx).append("\n");
                }
                log.info(msg.toString());
                newCASToCoref.put(CAS, newCorefIdx);
                ret.corefCAS(CAS, newCorefIdx);
            }
        }

        for (final KBPString unannotatedCAS : toMerge.unannotated) {
            ret.addUnannotatedCAS(unannotatedCAS);
        }

        return ret.build();
    }

    public int nextFreeIndex(int nextIndex, Map<KBPString, Integer> curMapping) {
        if (curMapping.size() > Integer.MAX_VALUE/2) {
            throw new RuntimeException("Too many coref mappings!  What on earth are you doing?");
        }
        for (int candidateIndex = nextIndex+1; ; ++candidateIndex) {
            if (!curMapping.values().contains(candidateIndex)) {
                return candidateIndex;
            }
        }
    }

    public Set<KBPString> allCASes() {
        return Sets.union(CASesToIDs.keySet(), unannotatedCASes());
    }

    /**
     * Returns the normalization of a {@link com.bbn.kbp.events2014.KBPString}
     * to some canonical coreferent {@code KBPString}.  The particular {@code KBPString}
     * returned is undefined, other than being guaranteed to be coreferent and that it will
     * be the same for all coreferent inputs. If a provided {@code KBPString} has no coreference
     * information, a {@link java.util.NoSuchElementException} is thrown.
     */
    public KBPString normalizeStrictly(KBPString s) {
        final Integer id = CASesToIDs.get(s);
        if (id != null) {
            // will never be null by construction of CASestoIDs & idToCASes
            return Iterables.getFirst(idToCASes.get(id), null);
        } else {
            throw new NoSuchElementException("Cannot normalize " + s);
        }
    }

    /**
     * Returns a function which normalizes {@link com.bbn.kbp.events2014.KBPString}s
     * to some canonical coreferent {@code KBPString}.  The particular {@code KBPString}
     * returned is undefined, other than being guaranteed to be coreferent and that it will
     * be the same for all coreferent inputs. If a provided {@code KBPString} has no coreference
     * information, a {@link java.util.NoSuchElementException} is thrown.
     */
    public Function<KBPString, KBPString> strictCASNormalizerFunction() {
        return new Function<KBPString, KBPString>() {
            @Override
            public KBPString apply(KBPString input) {
                return normalizeStrictly(input);
            }
        };
    }

    /**
     * Returns a function which normalizes {@link com.bbn.kbp.events2014.KBPString}s
     * to some canonical coreferent {@code KBPString}.  The particular {@code KBPString}
     * returned is undefined, other than being guaranteed to be coreferent and that it will
     * be the same for all coreferent inputs. If a provided {@code KBPString} has no coreference
     * information, this behaves like the identity function.
     */
    public Function<KBPString, KBPString> laxCASNormalizerFunction() {
        return new Function<KBPString, KBPString>() {
            @Override
            public KBPString apply(KBPString input) {
                final Integer id = CASesToIDs.get(input);
                if (id != null) {
                    // will never be null by construction of CASestoIDs & idToCASes
                    return Iterables.getFirst(idToCASes.get(id), null);
                } else {
                    return input;
                }
            }
        };
    }

    /**
     * Returns a function which normalizes {@link com.bbn.kbp.events2014.KBPString}s
     * to some canonical coreferent {@code KBPString}.  The particular {@code KBPString}
     * returned is undefined, other than being guaranteed to be coreferent and that it will
     * be the same for all coreferent inputs. If a provided {@code KBPString} has no coreference
     * information, a {@link com.google.common.base.Optional#absent()} is returned.
     */
    public Function<KBPString, Optional<KBPString>> normalizeCASIfPossibleFunction() {
        return new Function<KBPString, Optional<KBPString>>() {
            @Override
            public Optional<KBPString> apply(KBPString input) {
                final Integer id = CASesToIDs.get(input);
                if (id != null) {
                    // will never be null by construction of CASestoIDs & idToCASes
                    return Optional.of(Iterables.getFirst(idToCASes.get(id), null));
                } else {
                    return Optional.absent();
                }
            }
        };
    }

    public static final class Builder {
        private final Symbol docId;

        // we need to keep both mutable and immutable versions because we need the mutable version
        // to do lookups during building, but we need the immutable version for determinism
        private final ImmutableMap.Builder<KBPString, Integer> CASesToIDs = ImmutableMap.builder();
        private final Map<KBPString, Integer> CASesToIDsMutable = Maps.newHashMap();

        private final ImmutableSet.Builder<KBPString> unannotated = ImmutableSet.builder();
        private final boolean suppressExceptionOnDupes;

        private Builder(final Symbol docId, boolean suppressExceptionOnDupes) {
            this.docId = checkNotNull(docId);
            this.suppressExceptionOnDupes = suppressExceptionOnDupes;
        }

        public CorefAnnotation build() {
            return CorefAnnotation.create(docId, CASesToIDs.build(),
                    Sets.filter(unannotated.build(), not(in(CASesToIDsMutable.keySet()))));
        }

        public Builder addUnannotatedCAS(KBPString cas) {
            checkNotNull(cas);
            if (!CASesToIDsMutable.containsKey(cas)) {
                unannotated.add(cas);
            } else {
                log.warn("CAS " + cas.toString() + " may not be both annotated and unannotated for coref. Treating as annotated.");
            }
            return this;
        }

        public Builder corefCAS(KBPString cas, int corefId) {
            checkNotNull(cas);
            if (!CASesToIDsMutable.containsKey(cas)) {
                CASesToIDs.put(cas, corefId);
                CASesToIDsMutable.put(cas, corefId);
            } else {
                if (CASesToIDsMutable.get(cas) != corefId && !suppressExceptionOnDupes) {
                    throw new RuntimeException(cas.toString() + " has multiple coref IDs: "
                        + CASesToIDsMutable.get(cas) + " and " + corefId);
                }
            }
            return this;
        }

        public Builder putInNewRandomCluster(KBPString cas, Random rng) {
            checkNotNull(cas);
            int id = rng.nextInt();
            while (CASesToIDsMutable.values().contains(id)) {
                id = rng.nextInt();
            }
            corefCAS(cas, id);
            return this;
        }

        public void addUnannotatedCASes(Iterable<KBPString> unannotatedCASes) {
            for (final KBPString cas : unannotatedCASes) {
                addUnannotatedCAS(cas);
            }
        }
    }

    public String toString() {
        return Objects.toStringHelper(this)
                .add("docID", docId)
                .add("coreffed", "{"+StringUtils.NewlineJoiner.join(CASesToIDs.entrySet())+"}")
                .add("uncoreffed", "{"+StringUtils.NewlineJoiner.join(unannotatedCASes())+"}")
                .toString();
    }
}
