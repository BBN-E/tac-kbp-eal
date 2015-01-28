package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.linking.LinkingStrategy;
import com.bbn.kbp.events2014.linking.SameEventTypeLinker;

import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

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
        "\targumentAnnotationStore: directory containing assessed EA tuples\n" +
        "\targumentSystemStore: directory containing system EA tuples to link\n" +
        "\tlinkingSystemStore: destination directory to write out the linked system EA tuples\n");
    System.exit(1);
  }

  public static void main(final String[] argv) {
    if (argv.length != 1) {
      usage();
    }

    try {
      final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
      log.info(params.dump());

      final AssessmentSpecFormats.Format fileFormat = AssessmentSpecFormats.Format.KBP2014;

      // produced by annotators after they have assessed EA tuples
      // we'll use this as sanity check: discard any system response not found in overall pool
      final File argumentAnnotationStoreDir =
          params.getExistingDirectory("argumentAnnotationStore");
      final AnnotationStore argumentAnnotationStore =
          AssessmentSpecFormats.openAnnotationStore(argumentAnnotationStoreDir, fileFormat);
      // DirectoryAnnotationStore implements AnnotationStore
      // fileFormat is only used in argumentKeyStore.write method

      final File argumentSystemStoreDir =
          params.getExistingDirectory("argumentSystemStore");        // system EA tuples
      // whether it is KBP2014 or KBP2015 doesn't really matter, because it is only used when I call the write method, which is not used here
      final SystemOutputStore argumentSystemStore =
          AssessmentSpecFormats.openSystemOutputStore(argumentSystemStoreDir, fileFormat);
      final ImmutableSet<Symbol> docIDs = argumentSystemStore.docIDs();

      final File linkingSystemStoreDir = params
          .getExistingDirectory("linkingSystemStore");        // destination directory to write to
      final LinkingStore linkingSystemStore =
          LinkingSpecFormats.openOrCreateLinkingStore(linkingSystemStoreDir);

      // default to SameEventTypeLinker for now. We could parameterize this in future when there's multiple strategies.
      LinkingStrategy linkingStrategy =
          SameEventTypeLinker.create(ImmutableSet.of(KBPRealis.Actual, KBPRealis.Other));

      for (final Symbol docID : docIDs) {
        final AnswerKey answerKey = argumentAnnotationStore.read(docID);

        final SystemOutput docOutput = argumentSystemStore.read(docID);
        // Symbol docId, Set<Response> responses , Map<Response, Double> confidences

        log.info("For document {} got {} responses", docID, docOutput.size());

        // we will do a sanity check: filter Responses in docOutput against answerKey.allResponses()
        // TODO: answerKey.allResponses() include unannotated Responses. Would it be possible for a system response to be unannotated?
        final ResponseLinking responseLinking = linkingStrategy.linkResponses(docOutput, answerKey);
        // Symbol docId, Set<ResponseSet> responseSets , Set<Response> incompleteResponses (which will be empty)

        linkingSystemStore.write(responseLinking);
        // for each ResponseSet , writes out uniqueIdFunction() of each Response on the same line
        // the id of each Response is the cachedSHA1Hash, which is everything about a Response: type, role, CAS, realis, basefiller, predicate justifications, etc.

        for (final ResponseSet responseSet : responseLinking.responseSets()) {
          for (final Response r : responseSet.asSet()) {
            System.out.println(r.uniqueIdentifier() + " " + r);
          }
        }

      }
      linkingSystemStore.close();

    } catch (Throwable t) {
      log.error("Exception: {}", t);
      System.exit(1);
    }
  }

}
