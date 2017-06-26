package com.bbn.kbp.events2014.io;

import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by rgabbard on 6/26/17.
 */
class KBPSpec2015LinkingLoader extends AbstractKBPSpecLinkingLoader {

  @Override
  protected void handleResponseSetIDs(final ResponseLinking.Builder responseLinking,
      final ImmutableSet<ResponseSet> responseSets,
      final ImmutableMap<String, ResponseSet> responseIDs,
      final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException {
    if (!responseIDs.isEmpty()) {
      throw new IOException("IDs not allowed in 2014 linking format");
    }
  }

  @Override
  protected ImmutableLinkingLine parseResponseSetLine(final List<String> parts,
      final Optional<ImmutableMap<String, String>> foreignIDToLocal,
      final Map<String, Response> responsesByUID)
      throws IOException {
    return ImmutableLinkingLine
        .of(ResponseSet.of(parseResponses(parts, foreignIDToLocal, responsesByUID)),
            Optional.<String>absent());
  }
}
