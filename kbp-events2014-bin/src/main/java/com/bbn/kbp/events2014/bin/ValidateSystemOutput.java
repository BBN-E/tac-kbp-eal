package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

public final class ValidateSystemOutput {
    private static final Logger log = LoggerFactory.getLogger(ValidateSystemOutput.class);

    private ValidateSystemOutput() {
        throw new UnsupportedOperationException();
    }

    private static void usage() {
        log.error("Checks a system output store can be loaded and then dumps it in a human-readable way, resolving \n" +
            " offset spans against the original text.\n"+
            "usage: validateSystemOutput parameterFile\n" +
                "Parameter files are lines of key : value pairs\n" +
             "Parameters:\n\tsystemOutputStore: the system output to be validated \n" +
            "\tdump: whether to dump a human-readable form of the input to standard output\n" +
            "\tdocIDMap: (optional) a list of tab-separated pairs of doc ID and path to original text. Required only if dump is true.\n" +
            "\tvalidRoles: is data/2014.types.txt (for KBP 2014)");
        System.exit(1);
    }

    private static void trueMain(String[] argv) throws IOException {
        if (argv.length != 1) {
            usage();
        }
        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());
        final File systemOutputStoreFile = params.getExistingFileOrDirectory("systemOutputStore");
        final File validRolesFile = params.getExistingFile("validRoles");
        final boolean dump = params.getBoolean("dump");
        final File docIDMappingFile = dump ? params.getExistingFile("docIDMap") : null;

        log.info("Using map from document IDs to original text: {}", docIDMappingFile);
        final Map<Symbol, File> docIDMap = dump ? FileUtils.loadSymbolToFileMap(docIDMappingFile) : null;

        log.info("Validating types and roles against {}", validRolesFile);
        final Multimap<Symbol, Symbol> validRoles = FileUtils.loadSymbolMultimap(validRolesFile);

        log.info("Validating system output store {}", systemOutputStoreFile);
        final SystemOutputStore outputStore =
           AssessmentSpecFormats.openSystemOutputStore(systemOutputStoreFile);

        for (final Symbol docID : outputStore.docIDs()) {
            final SystemOutput docOutput = outputStore.read(docID);
            log.info("For document {} got {} responses", docID, docOutput.size());
            if (docOutput.size() > 0 && dump) {
                final String originalText = getOriginalText(docID, docIDMap);
                final StringBuilder msg = new StringBuilder();
                msg.append("\n"); // more readable if we skip a line after the log stamp
                for (final Response response : docOutput.responses()) {
                    assertValidTypes(response, validRoles);
                    msg.append(renderResponse(response, originalText));
                }
                log.info(msg.toString());
            }
        }

    }

    private static final ImmutableSet<Symbol> alwaysValidRoles = SymbolUtils.setFrom("Time", "Place");
    private static void assertValidTypes(Response response, Multimap<Symbol, Symbol> validRoles) {
        if (validRoles.containsKey(response.type())) {
            if (!alwaysValidRoles.contains(response.role())) {
                if (!validRoles.get(response.type()).contains(response.role())) {
                    log.error("Invalid role {} for event type {} used in document {}. Valid roles for this event are {}",
                        response.role(), response.type(), response.docID(),
                        StringUtils.CommaSpaceJoiner.join(validRoles.get(response.type())));
                    System.exit(1);
                }
            }
        } else {
            log.error("Invalid event type {} used in document {}. Valid event types are: {}", response.type(),
                    response.docID(), StringUtils.CommaSpaceJoiner.join(validRoles.keySet()));
            System.exit(1);
        }
    }

    private static String renderResponse(Response response, String originalText) {
        final StringBuilder sb = new StringBuilder();

        sb.append("\t");
        sb.append(response.type()).append("-").append(response.role()).append("-")
                .append(response.realis()).append("\n");
        final String CASFromOriginalText = resolveCharOffsets(response.canonicalArgument().charOffsetSpan(),
                response.docID(), originalText);

        if (CASFromOriginalText.equals(response.canonicalArgument().string())) {
            sb.append("\t\tCAS: ").append(CASFromOriginalText).append(" [EXACT MATCH WITH TEXT FROM OFFSETS]");
        } else {
            sb.append("\t\tCAS: ").append(response.canonicalArgument().string()).append(" [TEXT FROM OFFSETS: ")
                .append(CASFromOriginalText).append("]");
        }

        sb.append("\n\t\tPredicate justification(s): ");
        for (final CharOffsetSpan pjSpan : response.predicateJustifications()) {
            sb.append("\t\t\t").append(resolveCharOffsets(pjSpan, response.docID(), originalText));
        }
        sb.append("\n\t\tBase filler: ").append(resolveCharOffsets(response.baseFiller(), response.docID(), originalText));
        if (!response.additionalArgumentJustifications().isEmpty()) {
            sb.append("\n\t\tAdditional argument justification(s): ");
            for (final CharOffsetSpan ajSpan : response.additionalArgumentJustifications()) {
                sb.append("\t\t\t").append(resolveCharOffsets(ajSpan, response.docID(), originalText));
            }
        }

        sb.append("\n\n");

        return sb.toString();
    }

    private static String resolveCharOffsets(final CharOffsetSpan span, Symbol docID, String originalText) {
        try {
            return originalText.substring(span.startInclusive(), span.endInclusive()+1);
        } catch (IndexOutOfBoundsException iobe) {
            log.error("Offsets {} out of bounds for response in document {}. Document is {} characters long",
                    span, docID, originalText.length());
            throw iobe;
        }
    }

    private static String getOriginalText(Symbol docID, Map<Symbol, File> docIDMap) throws IOException {
        // can't actually return null
        String originalText = null;
        final File originalTextFile = docIDMap.get(docID);
        if (originalTextFile != null) {
            originalText = Files.asCharSource(originalTextFile, Charsets.UTF_8).read();
        } else {
            log.error("No original text found for document ID {} in supplied mapping", docID);
            System.exit(1);
        }
        return originalText;
    }

    public static void main(String[] argv) {
        try {
            trueMain(argv);
        } catch (Exception e) {
            // this is necessary because Java returns 0
            // even if it dies with an exception
            e.printStackTrace();
            System.exit(1);
        }
    }
}
