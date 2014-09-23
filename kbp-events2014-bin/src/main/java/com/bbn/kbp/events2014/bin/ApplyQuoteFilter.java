package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.transformers.QuoteFilter;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class ApplyQuoteFilter {
    private static final Logger log = LoggerFactory.getLogger(ApplyQuoteFilter.class);

    private ApplyQuoteFilter() {
        throw new UnsupportedOperationException();
    }

    private static void usage() {
        log.error("usage: ApplyQuoteFilter paramFile\n" +
                "parameters are:\n" +
                "\tinputStore: input system output store\n" +
                "\toutputStore: location to write filtered output store. Must be non-existent or an empty directory.\n" +
                "\tquoteFilter: file storing serialized quote filter");
        System.exit(1);
    }

    public static void main(String[] argv) throws IOException {
        try {
            trueMain(argv);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void trueMain(String[] argv) throws IOException {
        if (argv.length != 1) {
            usage();
        }

        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());

        final QuoteFilter quoteFilter = loadQuoteFilter(params);
        final Map<File, File> inputStoreToOutputStore = getInputOutput(params);

        for (final Map.Entry<File,File> inputOutputPair : inputStoreToOutputStore.entrySet()) {
            filterStore(inputOutputPair.getKey(), inputOutputPair.getValue(), quoteFilter,
                    params.getEnum("fileFormat", AssessmentSpecFormats.Format.class));
        }
    }

    private static void filterStore(File sourceStorePath, File destStorePath, QuoteFilter quoteFilter,
                                    AssessmentSpecFormats.Format fileFormat) throws IOException
    {
        log.info("Filtering {} to {}", sourceStorePath, destStorePath);
        final SystemOutputStore sourceStore =
                AssessmentSpecFormats.openSystemOutputStore(sourceStorePath, fileFormat);
        final SystemOutputStore destStore =
                AssessmentSpecFormats.createSystemOutputStore(destStorePath, fileFormat);

        log.info("Source store has {} documents", sourceStore.docIDs().size());
        for (final Symbol docID : sourceStore.docIDs()) {
            final SystemOutput original = sourceStore.read(docID);
            final SystemOutput filtered = quoteFilter.apply(original);
            log.info("For document {}, filtered out {} responses from quotes",
                    docID, original.size() - filtered.size());

            destStore.write(filtered);
        }

        sourceStore.close();
        destStore.close();
    }

    private static QuoteFilter loadQuoteFilter(Parameters params) throws IOException {
        final File filterFile = params.getExistingFile("quoteFilter");

        log.info("Loading quote filter from {}", filterFile);
        return QuoteFilter.loadFrom(Files.asByteSource(filterFile));
    }

    private static ImmutableMap<File, File> getInputOutput(Parameters params) {
        final ImmutableMap.Builder<File,File> ret = ImmutableMap.builder();

        boolean inputStorePresent = params.isPresent("inputStore");
        boolean inputStoresPresent = params.isPresent("inputStoresDir");

        if (inputStorePresent != inputStoresPresent) {
            if (inputStorePresent) {
                ret.put(params.getExistingDirectory("inputStore"), params.getCreatableDirectory("outputStore"));
            } else {
                final File inputStoresDir = params.getExistingDirectory("inputStoresDir");
                final File outputStoresDir = params.getCreatableDirectory("outputStoresDir");

                for (File subDir : inputStoresDir.listFiles()) {
                    if (subDir.isDirectory()) {
                        final File outputDir = new File(outputStoresDir, subDir.getName());
                        outputDir.mkdirs();
                        ret.put(subDir, outputDir);
                    }
                }
            }
        } else {
            throw new RuntimeException("Exactly one of inputStore and inputStoresDir must be present");
        }

        return ret.build();
    }
}