package com.bbn.kbp.events2014.transformers;


import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * From an answer key, we can derive additional assessed responses automatically
 * by copying the assessed tuple, but replacing the realis in the original tuple
 * with the assessor's realis.  This class carries out this answer key expansion.
 */
public final class ExpandFromRealis implements Function<AnswerKey, AnswerKey> {
    private static final Logger log = LoggerFactory.getLogger(ExpandFromRealis.class);
    private ExpandFromRealis() {

    }

    @Override
    public AnswerKey apply(AnswerKey input) {
        final Set<Response> existingResponses = Sets.newHashSet(input.allResponses());
        final ImmutableSet.Builder<AssessedResponse> newAssessedResponses = ImmutableSet.builder();
        newAssessedResponses.addAll(input.annotatedResponses());

        for (final AssessedResponse assessedResponse : input.annotatedResponses()) {
            if (assessedResponse.assessment().realis().isPresent()) {
                final Response responseWithAssessedRealis = assessedResponse.response()
                        .copyWithSwappedRealis(assessedResponse.assessment().realis().get());
                if (!existingResponses.contains(responseWithAssessedRealis)) {
                    newAssessedResponses.add(AssessedResponse.from(
                        responseWithAssessedRealis, assessedResponse.assessment()));
                    existingResponses.add(responseWithAssessedRealis);
                }
            }
        }

        return AnswerKey.from(input.docId(), newAssessedResponses.build(), input.unannotatedResponses(),
                input.corefAnnotation());
    }

    private static void trueMain(String[] argv) throws IOException {
        if (argv.length != 1) {
            usage();
            System.exit(1);
        }
        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());

        final File inputStoreLocation = params.getExistingDirectory("inputStore");
        final AnnotationStore inStore = AssessmentSpecFormats.openAnnotationStore(inputStoreLocation);
        final AnnotationStore outStore;

        if (params.isPresent("outputStore")) {
            outStore = AssessmentSpecFormats.openOrCreateAnnotationStore(params.getCreatableDirectory("outputStore"));
        } else if (params.getBoolean("inPlace")) {
            outStore = inStore;
        } else {
            throw new RuntimeException("Either outputStore must be specified or inPlace must be true");
        }

        final ExpandFromRealis expander = new ExpandFromRealis();

        for (final Symbol docId : inStore.docIDs()) {
            final AnswerKey original = inStore.read(docId);
            final AnswerKey expanded = expander.apply(original);

            if (expanded.annotatedResponses().size() != original.annotatedResponses().size()) {
                log.info("For {}, expanded from {} to {} assessed responses",
                        docId, original.annotatedResponses().size(),
                        expanded.annotatedResponses().size());
            } else {
                log.info("For {}, no responses added", docId);
            }
            outStore.write(expanded);
        }

        outStore.close();
        if (inStore != outStore) {
            inStore.close();
        }
    }

    private static void usage() {
        System.out.println("usage: ExpandFromRealis param_file\n" +
                "Parameters:\n"+
                "\tinputStore: annotation store to process\n"+
                "\toutputStore: (optional) where to write expanded output\n"+
                "\tinPlace: whether to expand the input store in-place (ignored unless outputStore absent)\n");
    }

    public static void main(String[] argv) {
        try {
            trueMain(argv);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
