package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput2015;
import com.bbn.kbp.events2014.SystemOutputLayout;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Created by jdeyoung on 7/27/15.
 */
public final class ImportForeignIDs {

  private ImportForeignIDs() {
    throw new UnsupportedOperationException();
  }

  private static final Logger log = LoggerFactory.getLogger(ImportForeignIDs.class);

  public static void main(String... args) {
    try {
      trueMain(args);
    } catch (final Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void trueMain(final String[] args) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(args[0]));

    if (params.getBoolean("doMultipleStores")) {
      processMultipleStores(params);
    } else {
      File outputStoreBase = params.getExistingDirectory("input");
      final File outputDirectory = params.getCreatableDirectory("output");
      importForeignIDs(outputStoreBase, outputDirectory);
    }
  }

  private static void processMultipleStores(Parameters params) throws IOException {
    final File inputBase = params.getExistingDirectory("inputBase");
    final File outputBase = params.getCreatableFile("outputBase");
    for (final File f : inputBase.listFiles()) {
      if (f.isDirectory()) {
        final File outputDir = new File(outputBase, f.getName());
        outputDir.mkdirs();
        importForeignIDs(f, outputDir);
      }
    }
  }

  public static void importForeignIDs(final File source, final File outputDirectory)
      throws IOException {
    final ArgumentStore originalArgumentStore =
        AssessmentSpecFormats.openSystemOutputStore(new File(source, "arguments"),
            AssessmentSpecFormats.Format.KBP2015);
    final LinkingStore originalLinkingStore =
        LinkingStoreSource.createFor2015().openLinkingStore(new File(source, "linking"));

    final SystemOutputStore newOutput =
        SystemOutputLayout.KBP_EA_2015.openOrCreate(outputDirectory);

    for (final Symbol docid : originalArgumentStore.docIDs()) {
      log.info("Converting {}", docid);
      final ImmutableBiMap.Builder<String, String> originalToSystem = ImmutableBiMap.builder();
      final ArgumentOutput originalArguments = AssessmentSpecFormats
          .uncachedReadFromArgumentStoreCachingOldIDS(originalArgumentStore, docid,
              originalToSystem);

      final Optional<ResponseLinking> transformedLinking = originalLinkingStore.readTransformingIDs(docid,
              originalArguments.responses(),
              Optional.<ImmutableMap<String, String>>of(originalToSystem.build()));
      if (transformedLinking.isPresent()) {
        // create system output
        final SystemOutput2015 newSystemOutput =
            SystemOutput2015.from(originalArguments, transformedLinking.get());
        newOutput.write(newSystemOutput);
      } else {
        throw new IOException("No linking found for " + docid);
      }
    }
    originalArgumentStore.close();
    originalLinkingStore.close();
    newOutput.close();
  }
}
