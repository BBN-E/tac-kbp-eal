package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.gnuplot.GnuPlotRenderer;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.bbn.bue.common.StringUtils.startsWith;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;
import static com.google.common.collect.Iterables.concat;

public final class PlotOverlap {

  private static final Logger log = LoggerFactory.getLogger(PlotOverlap.class);

  private PlotOverlap() {
    throw new UnsupportedOperationException();
  }

  public enum PlotType {
    TruePositives {
      @Override
      public String fileName() {
        return "TruePositives";
      }
    },
    FalsePositives {
      @Override
      public String fileName() {
        return "FalsePositive";
      }
    };

    public abstract String fileName();
  }

  public static void main(String[] argv) {
    // we wrap the main method in this way to
    // ensure a non-zero return value on failure
    try {
      trueMain(argv);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void trueMain(String[] argv) throws IOException, InterruptedException {
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final File inputDir = params.getExistingDirectory("inputDir");
    final File outputDir = params.getCreatableDirectory("outputDir");
    final Set<String> humanSystems = params.getStringSet("humanSystems");
    final Set<String> referenceSystems = params.getStringSet("referenceSystems");
    final Optional<Integer> excludeSharedPrefixSize =
        params.getOptionalInteger("excludeSharedPrefixSize");
    final GnuPlotRenderer renderer = GnuPlotRenderer.createForGnuPlotExecutable(
        params.getExistingFile("gnuPlotBinary"));
    final Set<String> stackedPlotSystems = params.getStringSet("stackedPlotSystems");

    final Map<PlotType, Map<String, ImmutableSet<String>>> plotTypeToSystemToIdsMap =
        gatherData(inputDir);
    log.info("Loaded equivalence class IDs for {} systems", plotTypeToSystemToIdsMap.size());

    for (final Map.Entry<PlotType, Map<String, ImmutableSet<String>>> plotTypeEntry : plotTypeToSystemToIdsMap
        .entrySet()) {
      final PlotType plotType = plotTypeEntry.getKey();

      final StackedOverlapGraphBuilder stackedOverlapGraph =
          new StackedOverlapGraphBuilder(plotType);

      // Ryan-style
      final Map<String, ImmutableSet<String>> systemToEquivIDs = plotTypeEntry.getValue();
      final ImmutableSet<String> humanIDs = gatherIDs(systemToEquivIDs, humanSystems);

      final OverlapGraphRenderer overlapGraphRenderer =
          new OverlapGraphRenderer(renderer, systemToEquivIDs,
              humanSystems, referenceSystems, excludeSharedPrefixSize);

      for (final String systemName : Ordering.natural().sortedCopy(systemToEquivIDs.keySet())) {
        final Set<String> systemsSharingPrefix =
            getSystemsSharingPrefix(systemName, systemToEquivIDs.keySet(),
                excludeSharedPrefixSize);
        log.info("For system {}, ignoring systems {} as probable variants", systemName,
            systemsSharingPrefix);
        log.info("Plotting {}/{}", plotType.name(), systemName);

        final ImmutableSet<String> systemIDs = systemToEquivIDs.get(systemName);

        overlapGraphRenderer
            .renderPlot("Overlap of " + plotType.name() + " for system " + systemName,
                systemIDs, humanIDs, systemsSharingPrefix,
                new File(new File(outputDir, systemName), plotType.name()));

        if (stackedPlotSystems.contains(systemName)) {
          stackedOverlapGraph
              .addBar(systemToEquivIDs, systemName, systemIDs, systemsSharingPrefix, humanSystems,
                  humanIDs);
        }
      }

      stackedOverlapGraph.renderTo(renderer, new File(outputDir, "marjorieStyleChart"));

      final File systemCountHistogramDir =
          new File(new File(outputDir, "systemCountHistogram"), plotType.name());
      systemCountHistogramDir.mkdirs();
      renderer
          .render((new SystemCountHistogramBuilder()).createSystemCountHistogram(systemToEquivIDs,
                  excludeSharedPrefixSize.get(), humanSystems)
                  .renderToEmptyDirectory(systemCountHistogramDir),
              new File(systemCountHistogramDir, "countHistogram.png"));
    }
  }

  public static ImmutableSet<String> getSystemsSharingPrefix(Set<String> referenceSystems,
      Set<String> systemNames,
      Optional<Integer> excludeSharedPrefixSize) {
    final ImmutableSet.Builder<String> ret = ImmutableSet.builder();

    for (final String referenceSystem : referenceSystems) {
      ret.addAll(getSystemsSharingPrefix(referenceSystem, systemNames, excludeSharedPrefixSize));
    }

    return ret.build();
  }

  private static Set<String> getSystemsSharingPrefix(String systemName, Set<String> systemNames,
      Optional<Integer> excludeSharedPrefixSize) {
    if (excludeSharedPrefixSize.isPresent()) {
      final String bannedPrefix = systemName.substring(0, excludeSharedPrefixSize.get());
      return FluentIterable.from(systemNames)
          .filter(startsWith(bannedPrefix))
          .toSet();
    } else {
      return ImmutableSet.of(systemName);
    }
  }


  private static Map<PlotType, Map<String, ImmutableSet<String>>> gatherData(File inputDir)
      throws IOException {
    log.info("Gathering information....");
    final DefaultMapBuilder<PlotType, Map<String, ImmutableSet<String>>> plotTypeToSystemToIdsMapB =
        DefaultMapBuilder.fromSupplier(
            PlotOverlap.<PlotType, String, ImmutableSet<String>>getHashMapSupplier());

    for (final File systemDir : inputDir.listFiles()) {
      final File standardOutputDir = new File(systemDir, "Standard");
      final String systemName = systemDir.getName();

      for (final PlotType plotType : PlotType.values()) {
        final ImmutableSet<String> equivClassIDs = ImmutableSet.copyOf(
            Files.asCharSource(new File(standardOutputDir, plotType.fileName() + ".ids.txt"),
                Charsets.UTF_8).readLines());
        plotTypeToSystemToIdsMapB.getOrDefault(plotType).put(systemName, equivClassIDs);
      }
    }

    return plotTypeToSystemToIdsMapB.asMap();
  }

  /**
   * ***************** Utility methods **********************
   */

  public static ImmutableSet<String> gatherIDs(Map<String, ImmutableSet<String>> systemToEquivIDs,
      Set<String> selectedSystems) {
    return ImmutableSet.copyOf(concat(valuesAsIterable(systemToEquivIDs, selectedSystems)));
  }

  private static <K, V> Iterable<V> valuesAsIterable(Map<K, V> map, Collection<K> keysToLookup) {
    final Predicate<K> isDesiredKey = in(keysToLookup);
    final Predicate<Map.Entry<K, V>> entryHasDesiredKey = compose(
        isDesiredKey, MapUtils.<K, V>getEntryKey());

    return FluentIterable.from(map.entrySet())
        .filter(entryHasDesiredKey)
        .transform(MapUtils.<K, V>getEntryValue());
  }

  private static <OuterK, K, V> DefaultMapBuilder.DefaultValueSupplier<OuterK, Map<K, V>> getHashMapSupplier() {
    return new DefaultMapBuilder.DefaultValueSupplier<OuterK, Map<K, V>>() {
      @Override
      public Map<K, V> initialValueFor(Object k) {
        return Maps.newHashMap();
      }
    };
  }

}
