package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;

public interface LinkingStrategy {

  ResponseLinking linkResponses(final SystemOutput systemOutput);

  LinkingStore wrap(SystemOutputStore argumentStore);
}

