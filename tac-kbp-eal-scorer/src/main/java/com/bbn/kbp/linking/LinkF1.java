package com.bbn.kbp.linking;

import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.collections.CollectionUtils;
import com.bbn.bue.common.evaluation.FMeasureCounts;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;

public class LinkF1 {

  private static final Logger log = LoggerFactory.getLogger(LinkF1.class);

  private LinkF1() {
  }

  public static LinkF1 create() {
    return new LinkF1();
  }

  public <T> ExplicitFMeasureInfo score(final Iterable<? extends Set<T>> predicted,
      final Iterable<? extends Set<T>> gold) {
    double linkF1Sum = 0.0;
    double linkPrecisionSum = 0.0;
    double linkRecallSum = 0.0;

    final Multimap<T, ? extends Set<T>> predictedItemToGroup =
        CollectionUtils.makeSetElementsToContainersMultimap(predicted);
    final Multimap<T, ? extends Set<T>> goldItemToGroup =
        CollectionUtils.makeSetElementsToContainersMultimap(gold);

    final ImmutableSet<T> keyItems = ImmutableSet.copyOf(Iterables.<T>concat(gold));
    final ImmutableSet<T> predictedItems = ImmutableSet.copyOf(Iterables.<T>concat(predicted));
    checkArgument(keyItems.containsAll(predictedItems),
        "Predicted linking has items the gold linking lacks: %s", Sets
            .difference(predictedItems, keyItems));
    if (keyItems.isEmpty()) {
      if (predictedItemToGroup.isEmpty()) {
        log.info("Key and predicted are empty; returning score of 1");
        return new ExplicitFMeasureInfo(1.0, 1.0, 1.0);
      } else {
        log.info("Key is empty but predicted is not; returning score of 0");
        return new ExplicitFMeasureInfo(0.0, 0.0, 0.0);
      }
    } else if (predictedItems.isEmpty()) {
      log.info("Predicted is empty but key is not; returning score of 0");
      return new ExplicitFMeasureInfo(0.0, 0.0, 0.0);
    }

    for (final T keyItem : keyItems) {
      // withouts are to ensure an element is not counted as its own neighbor
      final Set<T> predictedNeighbors = ImmutableSet.copyOf(
          without(Iterables.<T>concat(predictedItemToGroup.get(keyItem)),
              keyItem));
      final Set<T> goldNeighbors = ImmutableSet.copyOf(without(
          Iterables.<T>concat(goldItemToGroup.get(keyItem)),
          keyItem));

      if (predictedItems.contains(keyItem)) {
        final boolean predictedIsSingleton = predictedNeighbors.isEmpty();
        final boolean goldIsSingleton = goldNeighbors.isEmpty();

        if (!(predictedIsSingleton && goldIsSingleton)) {
          int truePositiveLinks = Sets.intersection(predictedNeighbors, goldNeighbors).size();
          int falsePositiveLinks = Sets.difference(predictedNeighbors, goldNeighbors).size();
          int falseNegativeLinks = Sets.difference(goldNeighbors, predictedNeighbors).size();

          final FMeasureCounts fMeasureCounts =
              FMeasureCounts.from(truePositiveLinks, falsePositiveLinks, falseNegativeLinks);
          linkF1Sum += fMeasureCounts.F1();
          linkPrecisionSum += fMeasureCounts.precision();
          linkRecallSum += fMeasureCounts.recall();

          if (predictedIsSingleton) {
            log.info(
                "For {}, gold neighbors are {} but predicted is singleton. Item f-measure is {}",
                keyItem, goldNeighbors, fMeasureCounts.F1());
          } else {
            log.info(
                "For {}, gold neighbors are {} and predicted neighbors are {}. Item f-measure is {}",
                keyItem, goldNeighbors, predictedNeighbors, fMeasureCounts.F1());
          }
        } else {
          final boolean appearsInPredicted = predictedItems.contains(keyItem);
          if (appearsInPredicted) {
            // arguments which are correctly linked to nothing (singletons)
            // count as having perfect links
            linkF1Sum += 1.0;
            linkPrecisionSum += 1.0;
            linkRecallSum += 1.0;
            log.info("{} is a singleton in both key and predicted. Score of 1.0", keyItem);
          } else {
            log.info("{} is a singleton in key but does not appear in predicted. Score of 0.0",
                keyItem);
          }
        }
      } else {
        log.info("{} is present only in the gold linking. Item F-measure is 0.0", keyItem);
      }
    }
    // note we divide linkPrecisionSum by the number of predicted items,
    // but the others by the number of gold items. This is because missing items
    // hurt recall but not precision
    final ExplicitFMeasureInfo explicitFMeasureInfo =
        new ExplicitFMeasureInfo(linkPrecisionSum / predictedItems.size(),
            linkRecallSum / keyItems.size(), linkF1Sum / keyItems.size());
    log.info("Final document linking score: {}", explicitFMeasureInfo);
    return explicitFMeasureInfo;
  }

  @MoveToBUECommon
  private static <T, S extends T> Iterable<T> without(Iterable<T> input, S itemToExclude) {
    return filter(input, not(Predicates.<T>equalTo(itemToExclude)));
  }

  @MoveToBUECommon
  private static <T> Iterable<T> withoutAnyOf(Iterable<T> input,
      Collection<? extends T> itemsToExclude) {
    return filter(input, not(in(itemsToExclude)));
  }
}
