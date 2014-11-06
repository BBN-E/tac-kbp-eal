package com.bbn.kbp.events2014.scorer.observers.breakdowns;

import com.bbn.bue.common.collections.MultimapUtils;
import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Ordering;

import java.util.Map;

import static com.google.common.base.Functions.compose;
import static com.google.common.base.Functions.toStringFunction;

public final class BreakdownFunctions {
    public static final Function<TypeRoleFillerRealis,Symbol> TypeDotRole = new Function<TypeRoleFillerRealis, Symbol> () {

        @Override
        public Symbol apply(TypeRoleFillerRealis input) {
            return Symbol.from(input.type().toString() + "." + input.role().toString());
        }
    };
    /**
     * Will identify genres for ACE and pilot documents. Ideally this would be refactored to read the mappings from
     * a file.
     */
    public static final Function<TypeRoleFillerRealis,Symbol> Genre = new GenreFunction();

    private static class GenreFunction implements Function<TypeRoleFillerRealis, Symbol> {
        @Override
        public Symbol apply(TypeRoleFillerRealis input) {
            final String docid = input.docID().toString();
            for (final Map.Entry<String, Symbol> classifier : prefixToGenre.entrySet()) {
                if (docid.startsWith(classifier.getKey())) {
                    return classifier.getValue();
                }
            }
            return UNKNOWN;
        }

        private static final Symbol UNKNOWN = Symbol.from("Unknown");
        private static final Symbol NW = Symbol.from("Newswire");
        private static final Symbol DF = Symbol.from("Forum");
        private static final Symbol BLOG = Symbol.from("Blog");
        private static final Symbol SPEECH = Symbol.from("Speech");
        private static final Symbol BC = Symbol.from("Broadcast conversation");
        private static final Symbol BN = Symbol.from("Broadast News");

        private static final Map<String, Symbol> prefixToGenre = MultimapUtils.copyAsMap(
                ImmutableMultimap.<Symbol, String>builder()
                        .putAll(BC, "CNN_CF", "CNN_IP")
                        .putAll(BN, "CNN_", "CNNHL")
                        .putAll(DF, "bolt", "marce", "alt.", "aus.cars", "Austin-Grad", "misc.",
                                "rec.", "seattle.", "soc.", "talk.", "uk.")
                        .putAll(NW, "APW", "AFP", "CNA", "NYT", "WPB", "XIN")
                        .putAll(BLOG, "AGGRESSIVEVOICEDAILY", "BACONSREBELLION",
                                "FLOPPING_ACES", "GETTINGPOLITICAL", "HEALINGIRAQ", "MARKBACKER", "OIADVANTAGE", "TTRACY")
                        .putAll(SPEECH, "fsh_")
                        .build()
                        .inverse());
    }

    public static final Map<String, Function<TypeRoleFillerRealis, Symbol>> StandardBreakdowns = ImmutableMap.of(
            "Type", TypeRoleFillerRealis.Type,
            "Role", BreakdownFunctions.TypeDotRole,
            "Genre", BreakdownFunctions.Genre,
            "DocId", TypeRoleFillerRealis.DocID,
            "Realis", compose(Symbol.FromString,
                        compose(toStringFunction(), TypeRoleFillerRealis.realisFunction())));


    private BreakdownFunctions() {
        throw new UnsupportedOperationException();
    }

    /**
     *
     * @return A mapping from each breakdown type to inner maps. These inner maps map from
     * the categories for that breakdown type to confusion matrices for only that category.
     */
    public static <SignatureType, ProvenanceType>
        ImmutableMap<String, BrokenDownProvenancedConfusionMatrix<SignatureType, ProvenanceType>>
    computeBreakdowns(
            ProvenancedConfusionMatrix<ProvenanceType> corpusConfusionMatrix,
            Map<String, Function<? super ProvenanceType, SignatureType>> breakdowns,
            Ordering<SignatureType> resultKeyOrdering)
    {
        final ImmutableMap.Builder<String, BrokenDownProvenancedConfusionMatrix<SignatureType, ProvenanceType>> printModes =
                ImmutableMap.builder();

        for (final Map.Entry<String, Function<? super ProvenanceType, SignatureType>> breakdownEntry : breakdowns.entrySet()) {
            printModes.put(breakdownEntry.getKey(), corpusConfusionMatrix.breakdown(breakdownEntry.getValue(),
                    resultKeyOrdering));
        }
        return printModes.build();
    }
}
