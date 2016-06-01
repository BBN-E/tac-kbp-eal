package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;
import com.bbn.kbp.events2014.ResponseLinking;
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
      final SystemOutputLayout outputLayout =
          SystemOutputLayout.ParamParser.fromParamVal(params.getString("outputLayout"));
      final LinkingStoreSource linkingStoreSource;
      if (params.getString("outputLayout").contains("2016")) {
        linkingStoreSource = LinkingStoreSource.createFor2016();
      } else {
        linkingStoreSource = LinkingStoreSource.createFor2015();
      }
      importForeignIDs(outputStoreBase, outputDirectory, linkingStoreSource,
          outputLayout);
    }
  }


  private static void processMultipleStores(Parameters params) throws IOException {
    final File inputBase = params.getExistingDirectory("inputBase");
    final File outputBase = params.getCreatableFile("outputBase");
    final SystemOutputLayout outputLayout =
        SystemOutputLayout.ParamParser.fromParamVal(params.getString("outputLayout"));
    final LinkingStoreSource linkingStoreSource;
    if (params.getString("outputLayout").contains("2016")) {
      linkingStoreSource = LinkingStoreSource.createFor2016();
    } else {
      linkingStoreSource = LinkingStoreSource.createFor2015();
    }
    for (final File f : inputBase.listFiles()) {
      if (f.isDirectory()) {
        final File outputDir = new File(outputBase, f.getName());
        outputDir.mkdirs();
        importForeignIDs(f, outputDir, linkingStoreSource, outputLayout);
      }
    }
  }

  public static void importForeignIDs(final File source, final File outputDirectory,
      final LinkingStoreSource linkingStoreSource, final SystemOutputLayout outputLayout)
      throws IOException {
    final ArgumentStore originalArgumentStore =
        AssessmentSpecFormats.openSystemOutputStore(new File(source, "arguments"),
            AssessmentSpecFormats.Format.KBP2015);

    final LinkingStore originalLinkingStore =
        linkingStoreSource.openLinkingStore(new File(source, "linking"));

    final SystemOutputStore newOutput = outputLayout.openOrCreate(outputDirectory);

    for (final Symbol docid : originalArgumentStore.docIDs()) {
      log.info("Converting {}", docid);
      final ImmutableMap.Builder<String, String> originalToSystem = ImmutableMap.builder();
      final ArgumentOutput originalArguments = AssessmentSpecFormats
          .uncachedReadFromArgumentStoreCachingOldIDS(originalArgumentStore, docid,
              originalToSystem);

      final Optional<ResponseLinking> transformedLinking =
          originalLinkingStore.readTransformingIDs(docid,
              originalArguments.responses(),
              Optional.<ImmutableMap<String, String>>of(originalToSystem.build()));
      if (transformedLinking.isPresent()) {
        // create system output
        final DocumentSystemOutput2015 newSystemOutput =
            DocumentSystemOutput2015.from(originalArguments, transformedLinking.get());
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
