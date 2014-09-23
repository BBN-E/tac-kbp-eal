package com.bbn.kbp.events2014.bin;

import java.io.File;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.linking.ExactMatchEventArgumentLinkingAligner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

public final class ConvertToAndWriteEquivalenceClass {
	private static final Logger log = LoggerFactory.getLogger(ConvertToAndWriteEquivalenceClass.class);
	
	private static void usage() {
		 log.error("Converts Response unique ids to Equivalence Response ids.\n" +
				 "usage: convertToAndWriteEquivalenceClass parameterFile\n" +
				 "Parameter files are lines of key : value pairs\n" +
				 "Parameters:\n" +
				 "\targumentKeyStore: directory containing assessed EA tuples\n" +
				 "\tlinkingInputStore: directory containing linked Response unique ids\n" +
				 "\tlinkingOutputEqStore: directory to write out the linked Equivalence ids\n");
		 System.exit(1);
	 }
	
	 public static void main(final String[] argv) {
		 if(argv.length!=1) {
			 usage();
		 }
		 
		 try {
			 final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
			 log.info(params.dump());
			 
			 final AssessmentSpecFormats.Format fileFormat = AssessmentSpecFormats.Format.KBP2014;
			 
			 // produced by annotators after they have assessed EA tuples
			 final File argumentKeyStoreDir = params.getExistingDirectory("argumentKeyStore");
			 final AnnotationStore argumentKeyStore = AssessmentSpecFormats.openAnnotationStore(argumentKeyStoreDir, fileFormat);
			 // DirectoryAnnotationStore implements AnnotationStore
			 // fileFormat is only used in argumentKeyStore.write method
			 
			 // produced by annotators after they have grouped EA tuples into event frames
			 final File linkingKeyStoreDir = params.getExistingDirectory("linkingInputStore");
			 final LinkingStore linkingKeyStore = LinkingSpecFormats.openOrCreateLinkingStore(linkingKeyStoreDir);
			 // DirectoryLinkingStore implements LinkingStore
			 
			 final Set<Symbol> argumentKeyStoreDocIDs = argumentKeyStore.docIDs();
			 final ImmutableSet<Symbol> linkingKeyStoreDocIDs = linkingKeyStore.docIDs();
			 
			 final ExactMatchEventArgumentLinkingAligner aligner = 
					 ExactMatchEventArgumentLinkingAligner.createForCorrectWithRealises(ImmutableSet.of(KBPRealis.Actual, KBPRealis.Other));
			 
			 final File linkingOutputEqStoreDir = params.getExistingDirectory("linkingOutputEqStore");	// destination directory to write equivalence response ids
			 final LinkingStore linkingOutputEqStore = LinkingSpecFormats.openOrCreateLinkingStore(linkingOutputEqStoreDir);
			 
			 for(final Symbol docID : linkingKeyStoreDocIDs) {
				 final AnswerKey answerKey = argumentKeyStore.read(docID);
				 // AnswerKey: Set<AssessedResponse> annotatedArgs, Set<Response> unannotatedResponses, CorefAnnotation corefAnnotation
				 // CorefAnnotation is CorefAnnotation.Builder with suppressExceptionOnDupes=false
				 
				 final Optional<ResponseLinking> linkingKey = linkingKeyStore.read(answerKey);
				 // ResponseLinking: Symbol docId, Set<ResponseSet> responseSets, Set<Response> incompleteResponses (if there is a INCOMPELTE line)
				 // Each file in the linkingKeyStore are lines, where each line is tab-separated unique-response-id
				 // The answerKey is provided simply to map from the unique-response-id to a Response object
				 // Hence, the responses in linkingKeyStore can be a subset of those in the answerKey (argumentKeyStore)
				 
				 final EventArgumentLinking ealKey = aligner.align(linkingKey.get(), answerKey);
				 // this requires that the set of Response of linkingKey and answerKey be the same, else an exception will be thrown
				 
				 final ResponseLinking responseLinking = aligner.alignToResponseLinking(ealKey, answerKey);
				 linkingOutputEqStore.write(responseLinking);
			 }
			 
			 linkingOutputEqStore.close();
			    
		 } catch (Throwable t) {
			 t.printStackTrace();
			 System.exit(1);
		 }
	 }
}
