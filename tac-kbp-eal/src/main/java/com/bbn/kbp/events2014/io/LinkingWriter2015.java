package com.bbn.kbp.events2014.io;

import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.base.Joiner;

import static com.google.common.collect.Iterables.transform;

/**
 * Created by rgabbard on 6/26/17.
 */
class LinkingWriter2015 extends AbstractKBPSpecLinkingWriter {

  @Override
  String renderLine(final ResponseSet responseSet, final ResponseLinking responseLinking) {
    return Joiner.on("\t").join(
        transform(responseSet.asSet(), ResponseFunctions.uniqueIdentifier()));
  }
}
