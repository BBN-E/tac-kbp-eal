package com.bbn.kbp.events2014.bin;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats.DirectoryLinkingStore;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.scorer.bin.KBP2015Scorer;

public final class FilterResponseLinking {
  
  private static final Logger log = LoggerFactory.getLogger(KBP2015Scorer.class);
  
  public static void main(String[] argv) {
    try {
      trueMain(argv);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  // filter a ResponseLinking store against the responses in a systemOutput store
  // params:
  // fileFormat
  // kbpEvents.systemOutput
  // kbpEvents.linking.inputStore
  // kbpEvents.linking.outputStore
  private static void trueMain(String[] argv) throws IOException {
  
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    //final Parameters kbpParams = params.copyNamespace("kbpEvents");
    
    final AssessmentSpecFormats.Format fileFormat = params.getEnum("fileFormat", AssessmentSpecFormats.Format.class);
    
    final SystemOutputStore systemOutputStore = AssessmentSpecFormats.openSystemOutputStore(params.getExistingDirectory("systemOutput"), fileFormat);
     
    final LinkingStore inputLinkingStore = LinkingSpecFormats.openOrCreateLinkingStore(params.getExistingDirectory("linking.inputStore"));
    final LinkingStore outputLinkingStore = LinkingSpecFormats.openOrCreateLinkingStore(params.getCreatableDirectory("linking.outputStore"));
    
    for(final Symbol docId : systemOutputStore.docIDs()) {
      final SystemOutput systemOutput = systemOutputStore.readOrEmpty(docId);
      final ResponseLinking inputLinking = ((DirectoryLinkingStore)inputLinkingStore).readWithFiltering(systemOutput).get();
      
      //final Predicate<Response> retainedResponses = Predicates.in(systemOutput.responses());
      //final ResponseLinking outputLinking = inputLinking.copyWithFilteredResponses(retainedResponses);
      
      //outputLinkingStore.write(outputLinking);
      outputLinkingStore.write(inputLinking);
      
    }
    
    systemOutputStore.close();
    inputLinkingStore.close();
    outputLinkingStore.close();
  }
  
}