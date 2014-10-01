package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PoolSystemOutput {
    private static final Logger log = LoggerFactory.getLogger(PoolSystemOutput.class);
    private enum AddMode { CREATE, APPEND }

    private static void trueMain(String[] argv) throws IOException {
        if (argv.length != 1) {
            usage();
        }

        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        final List<File> storesToPool = FileUtils.loadFileList(params.getExistingFile("storesToPool"));
        final File outputStorePath = params.getCreatableDirectory("pooledStore");
        final AddMode addMode = params.getEnum("addMode", AddMode.class);
        final AssessmentSpecFormats.Format fileFormat = params.getEnum("fileFormat", AssessmentSpecFormats.Format.class);

        final SystemOutputStore outputStore = getOutputStore(outputStorePath, addMode, fileFormat);

        // gather all our input, which includes anything currently in the output store
        final Set<Symbol> allDocIds = Sets.newHashSet();
        final Map<String, SystemOutputStore> storesToCombine = Maps.newHashMap();

        log.info("Output store currently contains responses for {} documents",
                    outputStore.docIDs().size());
        storesToCombine.put(outputStorePath.getAbsolutePath(), outputStore);
        allDocIds.addAll(outputStore.docIDs());

        for (final File inputStoreFile : storesToPool) {
            final SystemOutputStore inputStore = AssessmentSpecFormats.openSystemOutputStore(inputStoreFile, fileFormat);
            log.info("Importing responses for {} documents from {} to {}",
                    inputStore.docIDs().size(), inputStoreFile,
                    outputStorePath);

            allDocIds.addAll(inputStore.docIDs());
            storesToCombine.put(inputStoreFile.getAbsolutePath(), inputStore);
        }

        for (final Symbol docId : allDocIds) {
            final List<SystemOutput> responseSets = Lists.newArrayList();

            final StringBuilder sb = new StringBuilder();

            for (final Map.Entry<String, SystemOutputStore> storeEntry : storesToCombine.entrySet()) {
                final SystemOutput responses = storeEntry.getValue().readOrEmpty(docId);
                responseSets.add(responses);
                sb.append(String.format("\t%5d response from %s\n", responses.size(), storeEntry.getKey()));
            }

            final SystemOutput combinedOutput = SystemOutput.unionKeepingMaximumScore(responseSets);
            outputStore.write(combinedOutput);
            log.info("\nFor document {}\n{}\n{} responses total", docId, sb.toString(), combinedOutput.size());
        }

        // storesToCombine.values() includes the output store
        for (final SystemOutputStore store : storesToCombine.values()) {
            store.close();
        }
    }

    private static SystemOutputStore getOutputStore(File outputStorePath, AddMode addMode,
                                                    AssessmentSpecFormats.Format fileFormat) throws IOException
    {
        final SystemOutputStore outputStore;
        if (addMode == AddMode.CREATE) {
            outputStore = AssessmentSpecFormats.createSystemOutputStore(outputStorePath, fileFormat);
        } else if (addMode == AddMode.APPEND) {
            outputStore = AssessmentSpecFormats.openSystemOutputStore(outputStorePath, fileFormat);
        } else {
            throw new RuntimeException(String.format("Unknown add mode %s", addMode));
        }
        return outputStore;
    }

    private static void usage() {
        log.error("usage: poolSystemOutput param_file\n" +
                "where the parameters are:\n" +
                "\tstoresToPool: file listing paths to stores to pool\n" +
                "\tpooledStore: directory of system output store for pooling results\n" +
                "\taddMode: one of \n" +
                "\t\tCREATE: create a new repository\n" +
                "\t\tAPPEND: append to an existing repository, or create one if none exists");
        System.exit(1);
    }

    public static void main(String[] argv) {
        try {
            trueMain(argv);
        } catch (Exception e) {
            // proper return value for use in queue sequences
            e.printStackTrace();
            System.exit(1);
        }
    }
}
