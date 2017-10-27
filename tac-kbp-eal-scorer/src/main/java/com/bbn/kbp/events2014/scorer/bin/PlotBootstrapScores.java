package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.io.GZIPByteSource;
import com.bbn.bue.gnuplot.*;

import com.carrotsearch.hppc.DoubleArrayList;
import com.google.common.base.Charsets;
import com.google.common.collect.*;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes box plots of the bootstrap scores produced by the open-source scorer.
 */
public final class PlotBootstrapScores {

  private static final Logger log = LoggerFactory.getLogger(PlotBootstrapScores.class);

  private PlotBootstrapScores() {
    throw new UnsupportedOperationException();
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

  public static final String RAW_DIR_SUFFIX = ".bootstrapped.raw";

  enum Measure {
    P {
      @Override
      public String extension() {
        return "precision";
      }
    }, R {
      @Override
      public String extension() {
        return "recall";
      }
    }, F {
      @Override
      public String extension() {
        return "f";
      }
    };

    public abstract String extension();
  }

  private static final Axis X_AXIS = Axis.xAxis().setLabel("Participant").rotateLabels().build();
  private static final Color MEDIUM_GREY = Color.fromHexString("#777777");
  private static final LineStyle GREY_DASHED =
      LineStyle.builder().setColor(MEDIUM_GREY).setLineType(LineType.DASHED)
          .setWidth(2.0).build();

  private static final Grid GRID = NormalGrid.builder().sendToBack()
      .hideMinorXLines().hideMinorXLines()
      .setMajorLineStyle(GREY_DASHED).build();

  private static void trueMain(String[] argv) throws IOException, InterruptedException {
    final File rootDir = new File(argv[0]);
    final File outdir = new File(argv[1]);
    final File pathToGnuPlot = new File(argv[2]);

    final GnuPlotRenderer renderer = GnuPlotRenderer.createForGnuPlotExecutable(pathToGnuPlot);

    // plotting box and whiskers by DocID is silly and time consuming
    final Set<String> breakdownTypes = Sets.difference(gatherBreakdownTypes(rootDir),
        ImmutableSet.of("DocId"));
    log.info("Got breakdown breakdownTypes: {}", breakdownTypes);

    // output directories have the structure
    // participant/Standard/BreakdownType.bootstrapped.raw/breakdownValue_Present.{precisions,recalls,f}.txt
    for (final String breakdownType : breakdownTypes) {
      log.info("Processing breakdown type {}", breakdownType);
      final File breakdownOutDir = new File(outdir, breakdownType);
      breakdownOutDir.mkdirs();

      final DefaultMapBuilder<String, DefaultMapBuilder<Measure, List<BoxPlot.Dataset>>> data =
          DefaultMapBuilder.fromSupplier(MEASURE_MAP_PROVIDER);

      final Set<String> participantDrNames = FluentIterable
          .from(Arrays.asList(rootDir.listFiles()))
          .transform(FileUtils.ToAbsolutePath)
          .toSortedSet(Ordering.natural());

      for (final String participantDirName : participantDrNames) {
        final File participantDir = new File(participantDirName);
        final String participantName = participantDir.getName();
        log.info("\tProcessing participant {}", participantName);

        final File participantBreakdownDir = new File(new File(participantDir, "Standard"),
            breakdownType + RAW_DIR_SUFFIX);
        final Set<String> breakDownValues = gatherBreakdownValues(participantBreakdownDir);

        for (final String breakdownValue : breakDownValues) {
          log.info("\t\tProcessing breakdown value {}", breakdownValue);
          for (final Measure measure : Measure.values()) {
            log.info("\t\t\tProcessing measure {}", measure.extension());
            data.getOrDefault(breakdownValue).getOrDefault(measure).add(
                BoxPlot.Dataset.createAdoptingData(participantName, readArray(
                    new File(participantBreakdownDir,
                        breakdownValue + "_Present." + measure.extension() + "s.txt"))));
          }
        }
      }

      for (final Map.Entry<String, DefaultMapBuilder<Measure, List<BoxPlot.Dataset>>> entry : data
          .asMap()
          .entrySet()) {
        final String breakdownValue = entry.getKey();
        log.info("Rendering for breakdownValue {}", breakdownValue);

        for (final Map.Entry<Measure, List<BoxPlot.Dataset>> measureEntry : entry.getValue()
            .asMap().entrySet()) {
          final String measureName = measureEntry.getKey().extension();
          final List<BoxPlot.Dataset> datasets = measureEntry.getValue();
          log.info("\tRendering for measure {}", measureName);

          final BoxPlot.Whiskers whiskers = BoxPlot.Whiskers.builder()
              .setExtentMode(BoxPlot.Whiskers.Fraction.of(0.95)).build();

          final Axis yAxis =
              Axis.yAxis().setLabel(measureName).setRange(Range.closed(0.0, 60.0)).build();
          final BoxPlot.Builder plot = BoxPlot.builder().hideKey()
              .setTitle(breakdownType + " - " + breakdownValue + " - " + measureName)
              .setXAxis(X_AXIS)
              .setYAxis(yAxis)
              .setGrid(GRID)
              .setWhiskers(whiskers);
          for (final BoxPlot.Dataset dataset : datasets) {
            plot.addDataset(dataset);
          }
          final File outputDirectory = new File(new File(breakdownOutDir, breakdownValue),
              measureName);
          final BoxPlot finalPlot = plot.build();

          renderer.render(finalPlot.renderToEmptyDirectory(outputDirectory),
              new File(outputDirectory, "plot.png"));
        }
      }
    }
  }

  private static Set<String> gatherBreakdownValues(File participantBreakdownDir) {
    final ImmutableSet.Builder<String> ret = ImmutableSet.builder();
    for (final File f : participantBreakdownDir.listFiles()) {
      if (f.getName().endsWith("precisions.txt")) {
        ret.add(f.getName().substring(0, f.getName().lastIndexOf("_Present.precisions.txt")));
      }
    }
    return ret.build();
  }

  private static Set<String> gatherBreakdownTypes(File rootDir) throws IOException {
    final ImmutableSet.Builder<String> ret = ImmutableSet.builder();
    for (final File participantDir : rootDir.listFiles()) {
      final File participantStandardDir = new File(participantDir, "Standard");
      if (!participantStandardDir.isDirectory()) {
        throw new IOException("Missing directory " + participantStandardDir);
      }
      for (final File scoringFile : participantStandardDir.listFiles()) {
        if (scoringFile.isDirectory() && scoringFile.getAbsolutePath()
            .endsWith(".bootstrapped.raw")) {
          // it has two extensions, so we strip it twice
          ret.add(
              Files.getNameWithoutExtension(Files.getNameWithoutExtension(scoringFile.getPath())));
        }
      }
    }
    return ret.build();
  }

  private static double[] readArray(File file) throws IOException {
    final Iterable<String> lines = GZIPByteSource.fromCompressed(
        Files.asByteSource(file)).asCharSource(Charsets.UTF_8).readLines();

    final DoubleArrayList ret = new DoubleArrayList();
    for (final String line : lines) {
      ret.add(100.0 * Double.parseDouble(line));
    }
    return ret.toArray();
  }

  private static final DefaultMapBuilder.DefaultValueSupplier<Measure, List<BoxPlot.Dataset>>
      LIST_PROVIDER = new DefaultMapBuilder.DefaultValueSupplier<Measure, List<BoxPlot.Dataset>>() {
    @Override
    public List<BoxPlot.Dataset> initialValueFor(Measure k) {
      return Lists.newArrayList();
    }
  };

  private static final DefaultMapBuilder.DefaultValueSupplier<String, DefaultMapBuilder<Measure, List<BoxPlot.Dataset>>>
      MEASURE_MAP_PROVIDER =
      new DefaultMapBuilder.DefaultValueSupplier<String, DefaultMapBuilder<Measure, List<BoxPlot.Dataset>>>() {
        @Override
        public DefaultMapBuilder<Measure, List<BoxPlot.Dataset>> initialValueFor(
            String k) {
          return DefaultMapBuilder.fromSupplier(LIST_PROVIDER);
        }
      };
}
