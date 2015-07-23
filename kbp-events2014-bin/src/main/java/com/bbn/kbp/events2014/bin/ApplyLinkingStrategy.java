package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.ArgumentStore;
import com.bbn.kbp.events2014.linking.LinkingStrategy;
import com.bbn.kbp.events2014.linking.SameEventTypeLinker;

import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public final class ApplyLinkingStrategy {

  private static final Logger log = LoggerFactory.getLogger(ApplyLinkingStrategy.class);

  private ApplyLinkingStrategy() {
    throw new UnsupportedOperationException();
  }

  private static void usage() {
    log.error("Applies a linking strategy to produce response sets from a system's responses.\n" +
        "usage: applyLinkingStrategy parameterFile\n" +
        "Parameter files are lines of key : value pairs\n" +
        "Parameters:\n" +
        "\targumentSystemStore: directory containing system EA tuples to link\n" +
        "\tlinkingSystemStore: destination directory to write out the linked system EA tuples\n");
    System.exit(1);
  }

  public static void main(final String[] argv) throws IOException {
    if (argv.length != 1) {
      usage();
    }

    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    log.info(params.dump());

    final File argumentSystemStoreDir = params.getExistingDirectory("argumentSystemStore");
    final ArgumentStore argumentSystemStore =
        AssessmentSpecFormats.openSystemOutputStore(argumentSystemStoreDir, AssessmentSpecFormats.Format.KBP2015);
    final ImmutableSet<Symbol> docIDs = argumentSystemStore.docIDs();

    final File linkingSystemStoreDir = params.getEmptyDirectory("linkingSystemStore");
    final LinkingStore linkingSystemStore = LinkingSpecFormats.openOrCreateLinkingStore(linkingSystemStoreDir);

    // default to SameEventTypeLinker for now. We could parameterize this in future when there's multiple strategies.
    final LinkingStrategy linkingStrategy =
        SameEventTypeLinker.create(ImmutableSet.of(KBPRealis.Actual, KBPRealis.Other));

    for (final Symbol docID : docIDs) {
      final ArgumentOutput docOutput = argumentSystemStore.read(docID);

      log.info("For document {} got {} responses", docID, docOutput.size());

      final ResponseLinking responseLinking = linkingStrategy.linkResponses(docOutput);
      linkingSystemStore.write(responseLinking);
      log.info("For document {}, grouped {} responses into {} event frames", docID,
          docOutput.size(), responseLinking.responseSets().size());
    }
    linkingSystemStore.close();
    argumentSystemStore.close();
  }
}
