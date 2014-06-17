package com.bbn.kbp.events2014.scorer.observers.breakdowns;

import com.bbn.bue.common.collections.MultimapUtils;
import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;

import java.util.Map;

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
    public static final Function<TypeRoleFillerRealis,Symbol> Genre = new Function<TypeRoleFillerRealis, Symbol>() {
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

        private final Symbol UNKNOWN = Symbol.from("Unknown");
        private final Symbol NW = Symbol.from("Newswire");
        private final Symbol DF = Symbol.from("Forum");
        private final Symbol BLOG = Symbol.from("Blog");
        private final Symbol SPEECH = Symbol.from("Speech");
        private final Symbol BC = Symbol.from("Broadcast conversation");
        private final Symbol BN = Symbol.from("Broadast News");

        private final Map<String, Symbol> prefixToGenre = MultimapUtils.copyAsMap(
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
    };


    private BreakdownFunctions() {
        throw new UnsupportedOperationException();
    }

    public static <ProvenanceType> Map<String, Map<Symbol, ProvenancedConfusionMatrix<ProvenanceType>>> computeBreakdowns(
            ProvenancedConfusionMatrix<ProvenanceType> corpusConfusionMatrix,
            Map<String, Function<? super ProvenanceType, Symbol>> breakdowns)
    {
        final Map<String, Map<Symbol, ProvenancedConfusionMatrix<ProvenanceType>>> printModes = Maps.newHashMap();
        printModes.put("Aggregate", ImmutableMap.of(Symbol.from("Aggregate"), corpusConfusionMatrix));


        for (final Map.Entry<String, Function<? super ProvenanceType, Symbol>> breakdownEntry : breakdowns.entrySet()) {
            printModes.put(breakdownEntry.getKey(), corpusConfusionMatrix.breakdown(breakdownEntry.getValue()));
        }
        return printModes;
    }
}
