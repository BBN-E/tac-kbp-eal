package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.validation.TypeAndRoleValidator;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bbn.bue.common.files.FileUtils.loadSymbolToFileMap;
import static com.google.common.base.Preconditions.checkNotNull;

public final class ValidateSystemOutput {

  private static final Logger log = LoggerFactory.getLogger(ValidateSystemOutput.class);

  private final TypeAndRoleValidator typeAndRoleValidator;


  private ValidateSystemOutput(TypeAndRoleValidator typeAndRoleValidator) {
    this.typeAndRoleValidator = checkNotNull(typeAndRoleValidator);
  }

  public static ValidateSystemOutput create(TypeAndRoleValidator typeAndRoleValidator) {
    return new ValidateSystemOutput(typeAndRoleValidator);
  }

  private static void usage() {
    log.error(
        "Checks a system output store can be loaded and then dumps it in a human-readable way, resolving \n"
            +
            " offset spans against the original text.\n" +
            "usage: validateSystemOutput parameterFile\n" +
            "Parameter files are lines of key : value pairs\n" +
            "Parameters:\n\tsystemOutputStore: the system output to be validated \n" +
            "\tdump: whether to dump a human-readable form of the input to standard output\n" +
            "\tdocIDMap: (only if dump is true) a list of tab-separated pairs of doc ID and path to original text.\n"
            +
            "\tvalidRoles: is data/2014.types.txt (for KBP 2014)\n" +
            "\talwaysValidRoles: is 'Time, Place' (for KBP 2014)\n");
    System.exit(1);
  }


  /**
   * Returns the first exception encountered in each document when validating the supplied system
   * output store.  If the returned list is empty, the supplied output store is valid. Processing
   * will stop early if {@code maxErrors} errors are encountered.
   */
  public Result validateOnly(File systemOutputStoreFile, int maxErrors,
      Map<Symbol, File> docIDMap,
      SystemOutputLayout layout) throws IOException {
    return validate(systemOutputStoreFile, maxErrors, docIDMap, layout, false);
  }

  /**
   * Like {@link #validateOnly(File, int, Map, SystemOutputLayout)} except it also logs the response
   * in human readable format, using the supplied {@code docIDMap} to resolve offsets to strings.
   */
  public Result validateAndDump(File systemOutputStoreFile, int maxErrors,
      Map<Symbol, File> docIDMap, SystemOutputLayout layout)
      throws IOException {
    return validate(systemOutputStoreFile, maxErrors, docIDMap, layout, true);
  }


  private Result validate(File systemOutputStoreFile, int maxErrors,
      Map<Symbol, File> docIDMap, SystemOutputLayout outputLayout,
      boolean dump) throws IOException {
    final List<String> warnings = Lists.newArrayList();
    final List<Throwable> errors = Lists.newArrayList();

    log.info("Validating system output store {} with max errors {}", systemOutputStoreFile,
        maxErrors);

    // these are only non-final because the compiler isn't clever enough
    // to figure out they cannot fail to be initialized
    SystemOutputStore outputStore = null;
    Set<Symbol> docIDs = null;
    try {
      outputStore = outputLayout.open(systemOutputStoreFile);
      docIDs = outputStore.docIDs();
    } catch (Exception e) {
      errors.add(e);
      return new Result(errors, warnings);
    }

    try {
      assertAllOffsetsValid(outputStore, docIDMap);
    } catch (Exception e) {
      // we can recover from invalid offsets and find more errors
      // this can cause some errors to be repeated more than once
      errors.add(e);
    }

    int numErrors = 0;
    for (final Symbol docID : docIDs) {
      try {
        final ArgumentOutput docOutput = outputStore.read(docID).arguments();
        log.info("For document {} got {} responses", docID, docOutput.size());

        for (final Response response : docOutput.responses()) {
          assertValidTypes(response);
        }

        if (docOutput.size() > 0 && dump) {
          dumpResponses(docIDMap, docOutput);
        }
      } catch (Exception e) {
        errors.add(e);
        ++numErrors;
        if (numErrors > maxErrors) {
          return new Result(errors, warnings);
        }
      }
    }

    // this might not get called, but for read-only use with the default
    // implementation this is not a problem
    outputStore.close();
    return new Result(errors, warnings);
  }

  private void assertNoDocumentsOutsideCorpus(SystemOutputStore outputStore,
      Set<Symbol> docsInCorpus) throws IOException {
    final Set<Symbol> extraDocs = Sets.difference(outputStore.docIDs(), docsInCorpus);
    if (!extraDocs.isEmpty()) {
      throw new RuntimeException(
          String.format(
              "The output store contains the following documents which are not in the target corpus: %s",
              extraDocs));
    }
  }

  private void assertAllOffsetsValid(SystemOutputStore outputStore, Map<Symbol, File> docIdMap)
      throws IOException {
    assertNoDocumentsOutsideCorpus(outputStore, docIdMap.keySet());

    for (final Symbol docId : outputStore.docIDs()) {
      final String originalText = Files.asCharSource(docIdMap.get(docId), Charsets.UTF_8).read();
      final int maxOffset = originalText.length() - 1;
      for (final Response response : outputStore.read(docId).arguments().responses()) {
        assertValidCharOffsetSpan(response.canonicalArgument().charOffsetSpan(),
            "canonical argument string", docId, maxOffset);
        assertValidCharOffsetSpan(response.baseFiller(), "base filler", docId, maxOffset);
        for (final CharOffsetSpan span : response.additionalArgumentJustifications()) {
          assertValidCharOffsetSpan(span, "additional argument justification", docId, maxOffset);
        }
        for (final CharOffsetSpan span : response.predicateJustifications()) {
          assertValidCharOffsetSpan(span, "predicate justification", docId, maxOffset);
        }
      }
    }
  }

  private void assertValidCharOffsetSpan(CharOffsetSpan span, String type, Symbol docId,
      int maxOffset) {
    // CharOffsetSpan constructor validates start >=0, start>=end, so we don't need to do it here
    if (span.endInclusive() > maxOffset) {
      throw new RuntimeException(String.format(
          "For document %s, the %s span %s exceeds the last offset in the document (%d)",
          docId, type, span, maxOffset));
    }
  }


  private void assertValidTypes(Response response) {
    if (typeAndRoleValidator.isValidEventType(response)) {
      if (!typeAndRoleValidator.isValidArgumentRole(response)) {
        throw new RuntimeException(String.format(
            "Invalid role %s for event type %s used in document %s. Valid roles for this event are %s",
            response.role(), response.type(), response.docID(),
            StringUtils.CommaSpaceJoiner
                .join(typeAndRoleValidator.validRolesFor(response.type()))));
      }

    } else {
      throw new RuntimeException(String.format(
          "Invalid event type %s used in document %s. Valid event types are: %s", response.type(),
          response.docID(),
          StringUtils.CommaSpaceJoiner.join(typeAndRoleValidator.validEventTypes())));
    }
  }

  private static void dumpResponses(Map<Symbol, File> docIDMap, ArgumentOutput docOutput)
      throws IOException {
    final String originalText = getOriginalText(docOutput.docId(), docIDMap);
    final StringBuilder msg = new StringBuilder();
    // more readable if we skip a line after the log stamp
    msg.append("\n");
    for (final Response response : docOutput.responses()) {
      msg.append(renderResponse(response, originalText));
    }
    log.info(msg.toString());
  }


  private static String renderResponse(Response response, String originalText) {
    final StringBuilder sb = new StringBuilder();

    sb.append("\t");
    sb.append(response.type()).append("-").append(response.role()).append("-")
        .append(response.realis()).append("\n");
    final String CASFromOriginalText =
        resolveCharOffsets(response.canonicalArgument().charOffsetSpan(),
            response.docID(), originalText);

    if (CASFromOriginalText.equals(response.canonicalArgument().string())) {
      sb.append("\t\tCAS: ").append(CASFromOriginalText)
          .append(" [EXACT MATCH WITH TEXT FROM OFFSETS]");
    } else {
      sb.append("\t\tCAS: ").append(response.canonicalArgument().string())
          .append(" [TEXT FROM OFFSETS: ")
          .append(CASFromOriginalText).append("]");
    }

    sb.append("\n\t\tPredicate justification(s): ");
    for (final CharOffsetSpan pjSpan : response.predicateJustifications()) {
      sb.append("\t\t\t").append(resolveCharOffsets(pjSpan, response.docID(), originalText));
    }
    sb.append("\n\t\tBase filler: ")
        .append(resolveCharOffsets(response.baseFiller(), response.docID(), originalText));
    if (!response.additionalArgumentJustifications().isEmpty()) {
      sb.append("\n\t\tAdditional argument justification(s): ");
      for (final CharOffsetSpan ajSpan : response.additionalArgumentJustifications()) {
        sb.append("\t\t\t").append(resolveCharOffsets(ajSpan, response.docID(), originalText));
      }
    }

    sb.append("\n\n");

    return sb.toString();
  }

  private static String resolveCharOffsets(final CharOffsetSpan span, Symbol docID,
      String originalText) {
    try {
      return originalText.substring(span.startInclusive(), span.endInclusive() + 1);
    } catch (IndexOutOfBoundsException iobe) {
      log.error(
          "Offsets {} out of bounds for response in document {}. Document is {} characters long",
          span, docID, originalText.length());
      throw iobe;
    }
  }

  private static String getOriginalText(Symbol docID, Map<Symbol, File> docIDMap)
      throws IOException {
    // can't actually return null
    String originalText = null;
    final File originalTextFile = docIDMap.get(docID);
    if (originalTextFile != null) {
      originalText = Files.asCharSource(originalTextFile, Charsets.UTF_8).read();
    } else {
      log.error("No original text found for document ID {} in supplied mapping", docID);
      System.exit(1);
    }
    return originalText;
  }

  public static void main(String[] argv) throws IOException {
    if (argv.length != 1) {
      usage();
    }

    try {
      final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
      log.info(params.dump());

      final TypeAndRoleValidator typeAndRoleValidator =
          TypeAndRoleValidator.createFromParameters(params);
      final ValidateSystemOutput validator = create(typeAndRoleValidator);

      final File systemOutputStoreFile = params.getExistingFileOrDirectory("systemOutputStore");
      final SystemOutputLayout layout = params.getEnum("outputLayout", SystemOutputLayout.class);

      final File docIDMappingFile = params.getExistingFile("docIDMap");
      log.info("Using map from document IDs to original text: {}", docIDMappingFile);
      final Map<Symbol, File> docIDMap = loadSymbolToFileMap(docIDMappingFile);

      final ValidateSystemOutput.Result validationResult;
      if (params.getBoolean("dump")) {
        validationResult = validator
            .validateAndDump(systemOutputStoreFile, Integer.MAX_VALUE, docIDMap, layout);
      } else {
        validationResult =
            validator.validateOnly(systemOutputStoreFile, Integer.MAX_VALUE, docIDMap, layout);
      }
      if (!validationResult.wasSuccessful()) {
        throw validationResult.errors().get(0);
      }
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }

  public static final class Result {

    private final ImmutableList<Throwable> errors;
    private final ImmutableList<String> warnings;

    private Result(final Iterable<? extends Throwable> errors,
        final Iterable<String> warnings) {
      this.errors = ImmutableList.copyOf(errors);
      this.warnings = ImmutableList.copyOf(warnings);
    }

    public static Result forErrors(Iterable<? extends Throwable> errors) {
      return new Result(errors, ImmutableList.<String>of());
    }

    public ImmutableList<Throwable> errors() {
      return errors;
    }

    public ImmutableList<String> warnings() {
      return warnings;
    }

    public boolean wasSuccessful() {
      return errors.isEmpty();
    }
  }
}
