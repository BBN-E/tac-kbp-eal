package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.collections.LaxImmutableMapBuilder;
import com.bbn.bue.common.collections.MapUtils;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Ordering.usingToString;

/**
 * Created by rgabbard on 6/26/17.
 */
class KBPSpec2016LinkingLoader extends AbstractKBPSpecLinkingLoader {

  private static final Logger log = LoggerFactory.getLogger(KBPSpec2016LinkingLoader.class);

  @Override
  protected void handleResponseSetIDs(final ResponseLinking.Builder responseLinking,
      final ImmutableSet<ResponseSet> responseSets,
      final ImmutableMap<String, ResponseSet> responseIDs,
      final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException {
    if (responseSets.size() == responseIDs.size()) {
      responseLinking.responseSetIds(ImmutableBiMap.copyOf(responseIDs));
      responseLinking.build();
    } else if (responseSets.size() > responseIDs.size() || !foreignLinkingIdToLocal.isPresent()) {
      throw new IOException("Read " + responseSets.size() + " response sets but "
          + responseIDs.size() + " ID assignments");
    } else {
      log.warn(
          "Warning - converting ResponseSet IDs and saving them, this is almost definitely an error!");
      final ImmutableMultimap<ResponseSet, String> responseSetToIds =
          responseIDs.asMultimap().inverse();
      final LaxImmutableMapBuilder<String, ResponseSet> idsMapB =
          MapUtils.immutableMapBuilderAllowingSameEntryTwice();

      for (final Map.Entry<ResponseSet, Collection<String>> setAndIds : responseSetToIds.asMap()
          .entrySet()) {

        final Collection<String> ids = setAndIds.getValue();
        final String selectedID =
            checkNotNull(getFirst(usingToString().immutableSortedCopy(ids), null));
        for (final String oldId : ids) {
          log.debug("Selecting id {} for cluster {}", selectedID, oldId);
          foreignLinkingIdToLocal.get().put(oldId, selectedID);
          idsMapB.put(selectedID, responseIDs.get(oldId));
        }
      }
      responseLinking.responseSetIds(ImmutableBiMap.copyOf(idsMapB.build()));
    }
  }

  @Override
  protected ImmutableLinkingLine parseResponseSetLine(List<String> parts,
      final Optional<ImmutableMap<String, String>> foreignIDToLocal,
      final Map<String, Response> responsesByUID)
      throws IOException {
    if (parts.size() > 2) {
      log.warn(
          "IDs provided using tabs! This is contrary to the guidelines for the 2016 eval and may be changed!");
    }
    if (parts.size() == 2 && parts.get(1).contains(" ")) {
      final List<String> responseIDs = StringUtils.onSpaces().splitToList(parts.get(1));
      parts = ImmutableList.copyOf(Iterables.concat(ImmutableList.of(parts.get(0)), responseIDs));
    }
    if (parts.size() >= 2) {
      return ImmutableLinkingLine.of(ResponseSet.of(parseResponses(parts.subList(1, parts.size()),
          foreignIDToLocal, responsesByUID)), Optional.of(parts.get(0)));
    } else {
      throw new IOException("Line must have at least two fields");
    }
  }
}
