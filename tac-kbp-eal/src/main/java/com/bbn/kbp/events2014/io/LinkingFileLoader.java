package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;

import java.io.IOException;
import java.util.Set;

/**
 * Created by rgabbard on 6/26/17.
 */
interface LinkingFileLoader {

  ResponseLinking read(Symbol docID, CharSource linkingFile, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignResponseIDToLocal,
      final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException;
}
