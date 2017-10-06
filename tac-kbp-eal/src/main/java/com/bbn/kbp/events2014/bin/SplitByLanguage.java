package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.TACKBPEALException;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;

import com.google.common.collect.ImmutableMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Given a directory of containing multiple system outputs,
 * reproduces the same directory structure three times, each filtered
 * to contain one of English, Spanish, and Chinese.
 *
 * This is a utility to support the 2017 evaluation.  It was applied to the participant
 * submission directory and each resulting language-specific submissions directory was
 * scored separately.
 *
 */
public final class SplitByLanguage {
  private static final Logger log = LoggerFactory.getLogger(SplitByLanguage.class);

  private static void trueMain(String[] argv) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final File inputDir = params.getExistingDirectory("inputDir");
    final File baseOutputDir = params.getCreatableDirectory("baseOutputDir");

    for (final File inputStoreDirectory : inputDir.listFiles()) {
      if (inputStoreDirectory.isDirectory()) {
        try (final SystemOutputStore2016 input = SystemOutputStore2016.open(inputStoreDirectory)) {
          // to ensure nothing gets lost, we want to fail loudly if any documents
          // match no filter
          assertAllMatchSomeFilter(input, LANGUAGE_TO_PATTERN.values());

          for (final Map.Entry<String, Pattern> e : LANGUAGE_TO_PATTERN.entrySet()) {
            final String language = e.getKey();
            final Pattern filterPattern = e.getValue();

            final File outputDir = new File(new File(baseOutputDir, language), inputStoreDirectory.getName());
            final boolean anythingMadeItThrough;
            try (final SystemOutputStore2016 output = SystemOutputStore2016.openOrCreate(outputDir)) {
              anythingMadeItThrough = filter(input, output, filterPattern);
            }
            if (!anythingMadeItThrough) {
              log.warn("Input store from {} contained nothing for language {}; no output store "
                  + "written", inputStoreDirectory, outputDir);
              FileUtils.recursivelyDeleteDirectory(outputDir);
            }
          }
        }
      }
    }
  }

  /**
   * Returns true if anything made it through the filter.
   */
  private static boolean filter(final SystemOutputStore2016 input, final SystemOutputStore2016 output,
      final Pattern filterPattern) throws IOException {
    boolean anythingMadeItThrough = false;
    for (final Symbol docId : input.docIDs()) {
      if (filterPattern.matcher(docId.asString()).find()) {
        output.write(input.read(docId));
        anythingMadeItThrough = true;
      }
    }
    return anythingMadeItThrough;
  }

  private static void assertAllMatchSomeFilter(final SystemOutputStore2016 store,
      final Collection<Pattern> patterns) throws IOException {
    for (final Symbol docId : store.docIDs()) {
      boolean matched = false;
      for (final Pattern pattern : patterns) {
        if (pattern.matcher(docId.asString()).find()) {
          matched = true;
          break;
        }
      }
      if  (!matched) {
        throw new TACKBPEALException("No pattern matched " + docId.asString()
         + ". Available patterns are " + patterns);
      }
    }
  }

  private static final ImmutableMap<String, Pattern> LANGUAGE_TO_PATTERN =
    ImmutableMap.of(
        "English", Pattern.compile("(^ENG_|_ENG_)"),
        "Spanish", Pattern.compile("(^SPA_|_SPA_)"),
        "Chinese", Pattern.compile("(^CMN|_CMN_)"));

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
}
