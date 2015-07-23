package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.ArgumentStore;

public interface LinkingStrategy {

  ResponseLinking linkResponses(final ArgumentOutput argumentOutput);

  LinkingStore wrap(ArgumentStore argumentStore);
}

