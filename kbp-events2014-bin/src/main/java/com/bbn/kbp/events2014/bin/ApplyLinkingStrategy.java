package com.bbn.kbp.events2014.bin;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.linking.EventArgumentLinkingAligner.InconsistentLinkingException;
import com.bbn.kbp.events2014.linking.ExactMatchEventArgumentLinkingAligner;
import com.bbn.kbp.events2014.linking.LinkingStrategy;
import com.bbn.kbp.events2014.linking.SameEventTypeLinker;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

public final class ApplyLinkingStrategy {
	 private static final Logger log = LoggerFactory.getLogger(ApplyLinkingStrategy.class);
	 
	 private static void usage() {
		 log.error("Applies a linking strategy to produce response sets from a system's responses.\n" +
				 "usage: applyLinkingStrategy parameterFile\n" +
				 "Parameter files are lines of key : value pairs\n" +
				 "Parameters:\n" +
				 "\targumentSystemStore: the system output to link\n" +
				 "\tlinkingSystemStore: destination directory to write out the produced links\n");
		 System.exit(1);
	 }
	 
	 public static void main(final String[] argv) {
		 if(argv.length!=1) {
			 usage();
		 }
		 
		 try {
			 final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
			 log.info(params.dump());
			 
			 final File systemOutputStoreDir = params.getExistingDirectory("argumentSystemStore");	// system EA tuples
			 
			 final AssessmentSpecFormats.Format fileFormat = AssessmentSpecFormats.Format.KBP2014;
	         
			 // whether it is KBP2014 or KBP2015 doesn't really matter, because it is only used when I call the write method, which is not used here
			 final SystemOutputStore systemOutputStore = AssessmentSpecFormats.openSystemOutputStore(systemOutputStoreDir, fileFormat);
			 final ImmutableSet<Symbol> docIDs = systemOutputStore.docIDs();
			 
			 final File systemLinkingStoreDir = params.getExistingDirectory("linkingSystemStore");	// destination directory to write to
			 final LinkingStore linkingStore = LinkingSpecFormats.openOrCreateLinkingStore(systemLinkingStoreDir);
		     
			 // default to SameEventTypeLinker for now. We could parameterize this in future when there's multiple strategies.
			 LinkingStrategy linkingStrategy = SameEventTypeLinker.create();
			 
			 for(final Symbol docID : docIDs) {
				 final SystemOutput docOutput = systemOutputStore.read(docID);
				 // Symbol docId, Set<Response> responses , Map<Response, Double> confidences
				 
				 log.info("For document {} got {} responses", docID, docOutput.size());
				 
				 final ResponseLinking responseLinking = linkingStrategy.linkResponses(docOutput);
				 // Symbol docId, Set<ResponseSet> responseSets , Set<Response> incompleteResponses (which will be empty)
				 
				 linkingStore.write(responseLinking);
				 // for each ResponseSet , writes out uniqueIdFunction() of each Response on the same line
				 // the id of each Response is the cachedSHA1Hash, which is everything about a Response: type, role, CAS, realis, basefiller, predicate justifications, etc.
			 }
			 linkingStore.close();
				    
		 } catch (Throwable t) {
			 t.printStackTrace();
			 System.exit(1);
		 }
	 }
	  
}