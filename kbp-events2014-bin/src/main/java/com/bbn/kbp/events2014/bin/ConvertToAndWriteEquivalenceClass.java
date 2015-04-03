package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.TypeRoleFillerRealisSet;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.EventArgumentEquivalenceSpecFormats;
import com.bbn.kbp.events2014.io.EventArgumentEquivalenceStore;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.linking.EventArgumentLinkingAligner;
import com.bbn.kbp.events2014.linking.EventArgumentLinkingAligners;
import com.bbn.kbp.events2014.linking.LinkingUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public final class ConvertToAndWriteEquivalenceClass {

  private static final Logger log =
      LoggerFactory.getLogger(ConvertToAndWriteEquivalenceClass.class);

  private static void usage() {
    log.error("Converts Response unique ids to Equivalence Response ids.\n" +
        "usage: convertToAndWriteEquivalenceClass parameterFile\n" +
        "Parameter files are lines of key : value pairs\n" +
        "Parameters:\n" +
        "\targumentAnnotationStore: directory containing assessed EA tuples\n" +
        "\tlinkingStore: directory containing linked Response IDs\n" +
        "\tequivalenceStore: directory to write out the linked Equivalence IDs\n");
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
      final File argumentAnnotationStoreDir =
          params.getExistingDirectory("argumentAnnotationStore");
      final AnnotationStore argumentAnnotationStore =
          AssessmentSpecFormats.openAnnotationStore(argumentAnnotationStoreDir, fileFormat);
      // DirectoryAnnotationStore implements AnnotationStore
      // fileFormat is only used in argumentKeyStore.write method

      // produced by annotators after they have grouped EA tuples into event frames, or from system output
      final File linkingStoreDir = params.getExistingDirectory("linkingStore");
      final LinkingStore linkingStore =
          LinkingSpecFormats.openOrCreateLinkingStore(linkingStoreDir);
      // DirectoryLinkingStore implements LinkingStore

      //final Set<Symbol> argumentKeyStoreDocIDs = argumentAnnotationStore.docIDs();
      final ImmutableSet<Symbol> linkingStoreDocIDs = linkingStore.docIDs();

      // if linkingStoreIsGold=true, then it is produced as a result of assessment
      // if false, then it is produced by system
      final boolean linkingStoreIsGold = params.getBoolean("linkingStoreIsGold");
      final EventArgumentLinkingAligner aligner =
          EventArgumentLinkingAligners.getExactMatchEventArgumentLinkingAligner();
      final AnswerKey.Filter answerKeyFilter;
      if (linkingStoreIsGold) {
        answerKeyFilter = LinkingUtils.linkableResponseFilter2015ForGold();
      } else {
        answerKeyFilter = LinkingUtils.linkableResponseFilter2015ForSystemOutput();
      }

      final File equivalenceStoreDir = params.getCreatableDirectory(
          "equivalenceStore");        // destination directory to write equivalence response ids
      final EventArgumentEquivalenceStore equivalenceStore =
          EventArgumentEquivalenceSpecFormats.openOrCreateEquivalenceStore(equivalenceStoreDir);

      for (final Symbol docID : linkingStoreDocIDs) {
        final AnswerKey answerKey = argumentAnnotationStore.read(docID);
        // AnswerKey: Set<AssessedResponse> annotatedArgs, Set<Response> unannotatedResponses, CorefAnnotation corefAnnotation
        // CorefAnnotation is CorefAnnotation.Builder with suppressExceptionOnDupes=false
        //
        // Some EA tuples might not be assessed. But all CAS should be assessed for coreference, i.e. within the assessment portion of a EA tuple,
        // its 5th column should have an integer representing its coreferent id. Or else, there should be some other tuple having the same CAS which is assessed.
        // Else, if a particular CAS is not assessed in any tuple, it will not be found in CASesToIDs and it will be found in 'unannotated' in CorefAnnotation
        //
        // Reads all EA tuples into answerKey: Set<AssessedResponse> annotatedArgs; ImmutableSet<Response> unannotatedResponses; CorefAnnotation corefAnnotation;
        // CorefAnnotation: ImmutableMultimap<Integer, KBPString> idToCASes; ImmutableMap<KBPString, Integer> CASesToIDs; ImmutableSet<KBPString> unannotated;

        final Optional<ResponseLinking> responseLinking = linkingStore.read(answerKey);
        // ResponseLinking: Symbol docId, Set<ResponseSet> responseSets, Set<Response> incompleteResponses (if there is a INCOMPELTE line)
        // Each file in the linkingStore are lines, where each line is tab-separated unique-response-id
        // The answerKey is provided simply to map from the unique-response-id to a Response object
        // Hence, the responses in linkingStore should be a subset of those in the answerKey (argumentAnnotationStore)
        System.out.println("== LINKED responses ==");
        for (final ResponseSet responses : responseLinking.get().responseSets()) {
          for (final Response response : responses) {
            System.out.println(response.uniqueIdentifier() + " " + response);
          }
          System.out.println("--------");
        }
        System.out.println("== INCOMPLETE / not-linked responses ==");
        for (final Response response : responseLinking.get().incompleteResponses()) {
          System.out.println(response.uniqueIdentifier() + " " + response);
        }

        final EventArgumentLinking equivalenceLinking =
            aligner.align(responseLinking.get(), answerKey.filter(answerKeyFilter));

        System.out.println("== LINKED Equivalence classes ==");
        for (final TypeRoleFillerRealisSet eqSet : equivalenceLinking.linkedAsSet()) {
          for (final TypeRoleFillerRealis eq : eqSet.asSet()) {
            System.out.println(eq.uniqueIdentifier() + " " + eq);
          }
          System.out.println("--------");
        }
        System.out.println("== INCOMPLETE / not-linked Equivalence classes ==");
        for (final TypeRoleFillerRealis eq : equivalenceLinking.incomplete()) {
          System.out.println(eq.uniqueIdentifier() + " " + eq);
        }

        equivalenceStore.write(equivalenceLinking);

        //final ResponseLinking responseLinking = aligner.alignToResponseLinking(ealKey, answerKey);

      }


    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }
}
