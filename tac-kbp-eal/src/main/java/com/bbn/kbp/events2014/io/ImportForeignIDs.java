package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.CorpusEventFrame;
import com.bbn.kbp.events2014.CorpusEventLinking;
import com.bbn.kbp.events2014.DocEventFrameReference;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutputLayout;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

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
      if (params.getString("outputLayout").contains("2016") || params.getString("outputLayout")
          .contains("2017")) {
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

    // aggregate any changes in response sets ids
    final ImmutableTable.Builder<Symbol, String, String> docIdOldLinkingToNewB =
        ImmutableTable.builder();
    final Set<String> knownResponseIds = Sets.newHashSet();
    for (final Symbol docid : originalArgumentStore.docIDs()) {
      log.info("Converting {}", docid);
      final ImmutableMap.Builder<String, String> originalToSystem = ImmutableMap.builder();
      final ArgumentOutput originalArguments = AssessmentSpecFormats
          .uncachedReadFromArgumentStoreCachingOldIDS(originalArgumentStore, docid,
              originalToSystem);

      final ImmutableMap.Builder<String, String> originalLinkingIDToSystem = ImmutableMap.builder();
      final Optional<ResponseLinking> transformedLinking =
          originalLinkingStore.readTransformingIDs(docid,
              originalArguments.responses(),
              Optional.of(originalToSystem.build()), Optional.of(originalLinkingIDToSystem));
      for (final Map.Entry<String, String> e : originalLinkingIDToSystem.build().entrySet()) {
        docIdOldLinkingToNewB.put(docid, e.getKey(), e.getValue());
      }
      if (transformedLinking.isPresent()) {
        knownResponseIds.addAll(transformedLinking.get().responseSetIds().get().keySet());
        // create system output
        final DocumentSystemOutput2015 newSystemOutput =
            DocumentSystemOutput2015.from(originalArguments, transformedLinking.get());
        newOutput.write(newSystemOutput);
      } else {
        throw new IOException("No linking found for " + docid);
      }
    }
    final File corpusLinkingdir = new File(source, "corpusLinking");
    if (corpusLinkingdir.exists()) {
      final ImmutableTable<Symbol, String, String> oldLinkingIDToNew =
          docIdOldLinkingToNewB.build();
      final CorpusEventLinking corpusEventLinking = new CorpusEventFrameLoader2016()
          .loadCorpusEventFrames(
              Files.asCharSource(new File(corpusLinkingdir, "corpusLinking"), Charsets.UTF_8));
      final CorpusEventLinking.Builder nuLinking = CorpusEventLinking.builder();
      for (final CorpusEventFrame cef : corpusEventLinking.corpusEventFrames()) {
        final CorpusEventFrame.Builder cefb = CorpusEventFrame.builder();
        cefb.id(cef.id());
        final ImmutableSet.Builder<DocEventFrameReference> newDocEventFrameReferences =
            ImmutableSet.builder();
        for (final DocEventFrameReference defr : cef.docEventFrames()) {
          if(!oldLinkingIDToNew.containsRow(defr.docID())) {
            newDocEventFrameReferences.add(defr);
            checkState(knownResponseIds.contains(defr.eventFrameID()));
          } else {
            final String nuId =
                checkNotNull(oldLinkingIDToNew.get(defr.docID(), defr.eventFrameID()));
            checkState(knownResponseIds.contains(nuId));
            newDocEventFrameReferences.add(DocEventFrameReference.of(defr.docID(), nuId));
          }
        }
        cefb.addAllDocEventFrames(newDocEventFrameReferences.build());
        nuLinking.addCorpusEventFrames(cefb.build());
      }
      ((CrossDocSystemOutputStore) newOutput).writeCorpusEventFrames(nuLinking.build());
    }

    originalArgumentStore.close();
    originalLinkingStore.close();
    newOutput.close();
  }
}
