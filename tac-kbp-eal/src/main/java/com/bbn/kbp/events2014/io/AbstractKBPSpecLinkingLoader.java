package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.CharSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import static com.google.common.collect.Iterables.skip;

/**
 * Created by rgabbard on 6/26/17.
 */
abstract class AbstractKBPSpecLinkingLoader implements LinkingFileLoader {

  public ResponseLinking read(Symbol docID, CharSource source, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignResponseIDToLocal,
      final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException {
    final Map<String, Response> responsesByUID = Maps.uniqueIndex(responses,
        ResponseFunctions.uniqueIdentifier());

    final ImmutableSet.Builder<ResponseSet> responseSetsB = ImmutableSet.builder();
    Optional<ImmutableSet<Response>> incompleteResponses = Optional.absent();
    ImmutableMap.Builder<String, ResponseSet> responseSetIds = ImmutableMap.builder();

    int lineNo = 0;
    try {
      for (final String line : source.readLines()) {
        ++lineNo;
        // empty lines are allowed, and comments on lines
        // beginning with '#'
        if (line.isEmpty() || line.charAt(0) == '#') {
          continue;
        }
        final List<String> parts = StringUtils.onTabs().splitToList(line);
        if (line.startsWith("INCOMPLETE")) {
          if (!incompleteResponses.isPresent()) {
            incompleteResponses = Optional.of(parseResponses(skip(parts, 1),
                foreignResponseIDToLocal, responsesByUID));
          } else {
            throw new IOException("Cannot have two INCOMPLETE lines");
          }
        } else {
          final ImmutableLinkingLine linkingLine = parseResponseSetLine(parts,
              foreignResponseIDToLocal, responsesByUID);
          responseSetsB.add(linkingLine.responses());
          if (linkingLine.id().isPresent()) {
            responseSetIds.put(linkingLine.id().get(), linkingLine.responses());
          }
        }
      }
    } catch (Exception e) {
      throw new IOException("While reading " + docID + ", on line " + lineNo + ": ", e);
    }

    final ImmutableSet<ResponseSet> responseSets = responseSetsB.build();
    final ResponseLinking.Builder responseLinking =
        ResponseLinking.builder().docID(docID).responseSets(responseSets)
            .incompleteResponses(incompleteResponses.or(ImmutableSet.<Response>of()));
    handleResponseSetIDs(responseLinking, responseSets, responseSetIds.build(),
        foreignLinkingIdToLocal);
    return responseLinking.build();
  }

  protected abstract void handleResponseSetIDs(final ResponseLinking.Builder responseLinking,
      final ImmutableSet<ResponseSet> responseSets,
      final ImmutableMap<String, ResponseSet> responseIDs,
      final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException;

  protected abstract ImmutableLinkingLine parseResponseSetLine(List<String> parts,
      Optional<ImmutableMap<String, String>> foreignIDToLocal, Map<String, Response> responsesByUID)
      throws IOException;

  @Nonnull
  protected final ImmutableSet<Response> parseResponses(final Iterable<String> parts,
      final Optional<ImmutableMap<String, String>> foreignIDToLocal,
      final Map<String, Response> responsesByUID) throws IOException {
    final ImmutableSet.Builder<Response> responseSetB = ImmutableSet.builder();
    for (String idString : parts) {
      responseSetB.add(responseForID(idString, foreignIDToLocal, responsesByUID));
    }
    return responseSetB.build();
  }

  @Nonnull
  protected final Response responseForID(final String idString,
      final Optional<ImmutableMap<String, String>> foreignIDToLocal,
      final Map<String, Response> responsesByUID) throws IOException {
    final String newID;
    // for translating a foreign id
    if (foreignIDToLocal.isPresent()) {
      if (!foreignIDToLocal.get().containsKey(idString)) {
        throw new RuntimeException(
            "Could not find a new id for " + idString + " have id mappings "
                + foreignIDToLocal.get());
      }
      newID = foreignIDToLocal.get().get(idString);
    } else {
      newID = idString;
    }
    final Response responseForIDString = responsesByUID.get(newID);
    if (responseForIDString == null) {
      throw new IOException("ID " + newID + "(original ID) " + idString
          + " cannot be resolved using provided response store. Known"
          + "response IDs are " + responsesByUID.keySet()
          + "transformed response ids are " + foreignIDToLocal);
    }
    return responseForIDString;
  }
}
