package com.bbn.kbp.events2014.bin;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.linking.LinkingStrategy;
import com.google.common.collect.ImmutableSet;

public final class ApplyLinkingStrategy {
	 private static final Logger log = LoggerFactory.getLogger(ApplyLinkingStrategy.class);
	 
	 private static void usage() {
		 log.error("Applies a linking strategy to produce response sets from a system's responses.\n" +
				 "usage: applyLinkingStrategy parameterFile\n" +
				 "Parameter files are lines of key : value pairs\n" +
				 "Parameters:\n" +
				 "\tsystemOutputStore: the system output to link\n" +
				 "\tsystemLinkingStore: destination directory to write out the produced links\n" +
				 "\tfileFormat: KBP2015\n");
		 System.exit(1);
	 }
	 
	 public static void main(final String[] argv) {
		 if(argv.length!=1) {
			 usage();
		 }
		 
		 try {
			 final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
			 log.info(params.dump());
			 
			 final File systemOutputStoreDir = params.getExistingDirectory("systemOutputStore");
			 final AssessmentSpecFormats.Format fileFormat = params.getEnum("fileFormat", AssessmentSpecFormats.Format.class);
	         
			 final SystemOutputStore systemOutputStore = AssessmentSpecFormats.openSystemOutputStore(systemOutputStoreDir, fileFormat);
			 final ImmutableSet<Symbol> docIDs = systemOutputStore.docIDs();
			 
			 final File systemLinkingStoreDir = params.getExistingDirectory("systemLinkingStore");
			 final LinkingStore linkingStore = LinkingSpecFormats.openOrCreateLinkingStore(systemLinkingStoreDir);
		     
			 for(final Symbol docID : docIDs) {
				 final SystemOutput docOutput = systemOutputStore.read(docID);
				 log.info("For document {} got {} responses", docID, docOutput.size());
				 
				 final ResponseLinking responseLinking = LinkingStrategy.sameEventTypeLinking(docOutput);
				 linkingStore.write(responseLinking);
			 }
			 
			 			 
	            
		 } catch (Throwable t) {
			 t.printStackTrace();
			 System.exit(1);
		 }
	 }
}