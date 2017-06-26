package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.transform;

/**
 * Created by rgabbard on 6/26/17.
 */
class LinkingWriter2016 extends AbstractKBPSpecLinkingWriter {

  @Override
  String renderLine(final ResponseSet responseSet, final ResponseLinking responseLinking)
      throws IOException {
    return getEventFrameID(responseSet, responseLinking) + "\t" + StringUtils.spaceJoiner().join(
        transform(responseSet.asSet(), ResponseFunctions.uniqueIdentifier()));
  }

  // inefficient, but the number of frames in each document should be small
  private String getEventFrameID(final ResponseSet responseSet,
      final ResponseLinking responseLinking) throws IOException {
    checkArgument(responseLinking.responseSetIds().isPresent(), "Linking does not assign frame "
        + "IDs. These are required for writing in 2016 format.");
    final ImmutableSet<String> ids =
        responseLinking.responseSetIds().get().asMultimap().inverse().get(responseSet);
    if (ids.size() == 1) {
      return ids.asList().get(0);
    } else if (ids.isEmpty()) {
      throw new IOException("No ID found for event frame " + responseSet);
    } else {
      throw new IOException("Multiple IDs found for event frame, should be impossible: "
          + responseSet);
    }
  }
}
