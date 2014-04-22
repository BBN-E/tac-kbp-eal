package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.filters.QuoteFilter;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

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
        if (argv.length != 1) {
            usage();
        }

        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());

        final File inputStoreLocation = params.getExistingDirectory("inputStore");
        final File outputStoreLocation = params.getCreatableDirectory("outputStore");
        final File filterFile = params.getExistingFile("quoteFilter");

        log.info("Loading quote filter from {}", filterFile);
        final QuoteFilter quoteFilter = QuoteFilter.loadFrom(Files.asByteSource(filterFile));
        final SystemOutputStore sourceStore = AssessmentSpecFormats.openSystemOutputStore(inputStoreLocation);
        final SystemOutputStore destStore = AssessmentSpecFormats.createSystemOutputStore(outputStoreLocation);

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
}