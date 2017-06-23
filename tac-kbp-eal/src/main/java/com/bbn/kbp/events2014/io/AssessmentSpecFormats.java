package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.collections.LaxImmutableMapBuilder;
import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.FillerMentionType;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.io.assessmentCreators.AssessmentCreator;
import com.bbn.kbp.events2014.io.assessmentCreators.RecoveryAssessmentCreator;
import com.bbn.kbp.events2014.io.assessmentCreators.StrictAssessmentCreator;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.bbn.kbp.events2014.AssessedResponseFunctions.response;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Handles file formats defined in the KBP 2014 Event Argument Task assessment specifications.
 *
 * These formats represent each event argument as a single line of tab-separated fields. Blank lines
 * and lines beginning with "#" are ignored as comments.  No fields may contain tabs. The columns
 * are as described in the Event Argument Attachment task assessment specification. They are not
 * duplicated here to prevent out-of-sync documentation.
 *
 * @author Ryan Gabbard
 */
public final class AssessmentSpecFormats {

  private static final Logger log = LoggerFactory.getLogger(AssessmentSpecFormats.class);
  private static Character METADATA_MARKER = '#';

  private AssessmentSpecFormats() {
    throw new UnsupportedOperationException();
  }

  public enum Format {
    KBP2014 {
      @Override
      public String identifierField(Response response) {
        return Integer.toString(response.old2014ResponseID());
      }

      @Override
      protected Ordering<Response> responseOrdering() {
        return Response.ByOld2014Id;
      }

      @Override
      protected ColumnSpec columnSpec() {
        return ColumnSpec.KBP2014;
      }
    }, KBP2015 {
      @Override
      public String identifierField(Response response) {
        return response.uniqueIdentifier2015();
      }

      @Override
      protected Ordering<Response> responseOrdering() {
        return Response.byUniqueIdOrdering2015();
      }

      @Override
      protected ColumnSpec columnSpec() {
        return ColumnSpec.KBP2014;
      }
    }, KBP2017 {
      @Override
      protected String identifierField(final Response response) {
        return response.uniqueIdentifier();
      }

      @Override
      protected Ordering<Response> responseOrdering() {
        return Response.byUniqueIdOrdering();
      }

      @Override
      protected ColumnSpec columnSpec() {
        return ColumnSpec.KBP2017;
      }
    };

    protected abstract String identifierField(Response response);

    protected abstract Ordering<Response> responseOrdering();

    protected abstract ColumnSpec columnSpec();
  }

  public enum ColumnSpec {
    KBP2014 {
      @Override
      protected int responseID() {
        return 0;
      }

      @Override
      protected int docID() {
        return 1;
      }

      @Override
      protected int eventType() {
        return 2;
      }

      @Override
      protected int role() {
        return 3;
      }

      @Override
      protected int CAS() {
        return 4;
      }

      @Override
      protected int casOffsets() {
        return 5;
      }

      @Override
      protected int predicateJustifications() {
        return 6;
      }

      @Override
      protected int baseFiller() {
        return 7;
      }

      @Override
      protected int additionalArgumentJustifications() {
        return 8;
      }

      @Override
      protected int realis() {
        return 9;
      }

      @Override
      protected int confidence() {
        return 10;
      }

      @Override
      protected Optional<Integer> xdocEntityID() {
        return Optional.absent();
      }
    }, KBP2017 {
      @Override
      protected int responseID() {
        return 0;
      }

      @Override
      protected int docID() {
        return 1;
      }

      @Override
      protected int eventType() {
        return 2;
      }

      @Override
      protected int role() {
        return 3;
      }

      @Override
      protected int CAS() {
        return 4;
      }


      @Override
      protected Optional<Integer> xdocEntityID() {
        return Optional.of(5);
      }

      @Override
      protected int casOffsets() {
        return 6;
      }

      @Override
      protected int predicateJustifications() {
        return 7;
      }

      @Override
      protected int baseFiller() {
        return 8;
      }

      @Override
      protected int additionalArgumentJustifications() {
        return 9;
      }

      @Override
      protected int realis() {
        return 10;
      }

      @Override
      protected int confidence() {
        return 11;
      }

    };

    protected abstract int responseID();
    protected abstract int docID();
    protected abstract int eventType();
    protected abstract int role();
    protected abstract int CAS();
    protected abstract int casOffsets();
    protected abstract int predicateJustifications();
    protected abstract int baseFiller();
    protected abstract int additionalArgumentJustifications();
    protected abstract int realis();
    protected abstract int confidence();
    protected abstract Optional<Integer> xdocEntityID();
  }

  /**
   * Creates a directory-based assessment store in the specified directory. Will throw an {@link
   * java.io.IOException} if the directory exists and is non-empty.
   */
  public static AnnotationStore createAnnotationStore(final File directory, Format format)
      throws IOException {
    if (directory.exists() && !FileUtils.isEmptyDirectory(directory)) {
      throw new IOException(String.format(
          "Non-empty output directory %s when attempting to create assessment store", directory));
    }
    directory.mkdirs();
    return new DirectoryAnnotationStore(directory, StrictAssessmentCreator.create(), false, format);
  }

  /**
   * Opens an existing assessment store stored in the given directory.  The implementation makes
   * some attempt to avoid having multiple assessment store objects writing to the same directory,
   * which can result in data corruption, but this is not guaranteed.
   */
  public static AnnotationStore openAnnotationStore(final File directory, Format format)
      throws IOException {
    if (!directory.exists() || !directory.isDirectory()) {
      throw new IOException(String
          .format("Annotation store directory %s either does not exist or is not a directory",
              directory));
    }
    return new DirectoryAnnotationStore(directory, StrictAssessmentCreator.create(), false, format);
  }

  public static AnnotationStore recoverPossiblyBrokenAnnotationStore(File directory,
      RecoveryAssessmentCreator assessmentCreator, Format format) throws IOException {
    if (!directory.exists() || !directory.isDirectory()) {
      throw new IOException(String.format(
          "Annotation store directory %s either does not exist or is not a directory",
          directory));
    }
    return new DirectoryAnnotationStore(directory, assessmentCreator, false, format);
  }


  public static AnnotationStore openOrCreateAnnotationStore(final File directory,
      Format format) throws IOException {
    directory.mkdirs();
    return new DirectoryAnnotationStore(directory, StrictAssessmentCreator.create(), false, format);
  }

  /**
   * Creates a new system output store in the specified directory. If the directory is non-empty, an
   * exception is thrown.
   */
  public static ArgumentStore createSystemOutputStore(final File directory,
      Format format) throws IOException {
    if (directory.exists() && !FileUtils.isEmptyDirectory(directory)) {
      throw new IOException(String.format(
          "Cannot create system output store: directory is non-empty: %s", directory));
    }
    directory.mkdirs();
    return new DirectorySystemOutputStore(directory, format);
  }

  /**
   * Opens an existing system output store.
   */
  public static ArgumentStore openSystemOutputStore(final File directory, Format format) {
    checkArgument(directory.exists() && directory.isDirectory(),
        "Directory to open as annotation store %s either does not exist or is not a directory",
        directory);
    return new DirectorySystemOutputStore(directory, format);
  }

  public static ArgumentStore openOrCreateSystemOutputStore(final File directory, Format format)
      throws IOException {
    if (directory.exists()) {
      return openSystemOutputStore(directory, format);
    } else {
      return createSystemOutputStore(directory, format);
    }
  }

  /* package-private */
  static ArgumentOutput uncachedReadFromArgumentStoreCachingOldIDS(
      final ArgumentStore directoryArgumentStore, final Symbol docID,
      final ImmutableMap.Builder<String, String> originalIDToSystem) throws IOException {
    if (directoryArgumentStore instanceof DirectorySystemOutputStore) {
      return ((DirectorySystemOutputStore) directoryArgumentStore).readAndCacheIDs(docID,
          originalIDToSystem);
    } else {
      throw new RuntimeException(
          "Invalid annotation store type, got " + directoryArgumentStore.getClass()
              + " but expected DirectorySystemOutputStore");
    }
  }

  private static final class DirectorySystemOutputStore implements ArgumentStore {

    private static final Logger log = LoggerFactory.getLogger(DirectorySystemOutputStore.class);

    private final File directory;
    private final Format format;

    private DirectorySystemOutputStore(final File directory, final Format format) {
      checkArgument(directory.isDirectory(),
          "Specified directory %s for system output store is not a directory", directory);
      this.directory = checkNotNull(directory);
      this.format = checkNotNull(format);
    }

    private static final Splitter OnTabs = Splitter.on('\t').trimResults();

    @Override
    public ArgumentOutput read(final Symbol docid) throws IOException {
      return readAndCacheIDs(docid, ImmutableMap.<String, String>builder());
    }

    /* package-private */ ArgumentOutput readAndCacheIDs(final Symbol docid,
        final ImmutableMap.Builder<String, String> idMap) throws IOException {
      final File f = bareOrWithSuffix(directory, docid.asString(), ACCEPTABLE_SUFFIXES);

      final ImmutableList.Builder<Scored<Response>> ret = ImmutableList.builder();

      final LaxImmutableMapBuilder<Response, String> responseToMetadata =
          MapUtils.immutableMapBuilderAllowingSameEntryTwice();

      int lineNo = 0;
      String lastLine = ArgumentOutput.DEFAULT_METADATA;
      for (final String line : Files.asCharSource(f, UTF_8).readLines()) {
        ++lineNo;
        if (line.isEmpty() || line.startsWith("#")) {
          lastLine = line.trim();
          continue;
        }
        final List<String> parts = ImmutableList.copyOf(OnTabs.split(line));
        try {
          // input system IDs are currently not preserved
          try {
            final double confidence = Double.parseDouble(parts.get(format.columnSpec().confidence()));
            final Response response = parseArgumentFields(format, parts);
            // do not require a # to be put in the metadata beforehand
            if (lastLine.length() > 0 && lastLine.charAt(0) == METADATA_MARKER
                && lastLine.length() > 1) {
              final String metadata = lastLine.substring(1);
              responseToMetadata.put(response, metadata);
            } else {
              responseToMetadata.put(response, ArgumentOutput.DEFAULT_METADATA);
            }

            idMap.put(parts.get(format.columnSpec().responseID()), response.uniqueIdentifier());
            ret.add(Scored.from(response, confidence));
            lastLine = line;
          } catch (IndexOutOfBoundsException iobe) {
            throw new RuntimeException(
                String.format("Expected 11 tab-separated columns, but got %d", parts.size()), iobe);
          }
        } catch (final Exception e) {
          throw new RuntimeException(
              String.format("For doc ID %s, Invalid line %d: %s", docid, lineNo, line), e);
        }
      }

      return ArgumentOutput.from(docid, ret.build(), responseToMetadata.build());
    }

    @Override
    public ImmutableSet<Symbol> docIDs() throws IOException {
      return FluentIterable.from(Arrays.asList(directory.listFiles()))
          .transform(FileUtils.toNameFunction())
          .transform(Symbol.FromString)
          .toSet();
    }


    @Override
    public void write(final ArgumentOutput output) throws IOException {
      final File f = new File(directory, output.docId().toString());
      final PrintWriter out = new PrintWriter(new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(f), Charsets.UTF_8)));

      try {
        for (final Response response : format.responseOrdering().sortedCopy(output.responses())) {
          final String metadata = output.metadata(response);
          if (!metadata.equals(ArgumentOutput.DEFAULT_METADATA)) {
            out.print(METADATA_MARKER + metadata + "\n");
          }
          //out.print(response.responseID());
          out.print(format.identifierField(response));
          out.print("\t");
          final double confidence = output.confidence(response);
          out.print(argToString(response, confidence) + "\n");
        }
      } finally {
        out.close();
      }
    }

    @Override
    public void close() {
      // pass
    }

    private String argToString(final Response arg, final double confidence) {
      final List<String> parts = Lists.newArrayList();
      addArgumentParts(format, arg, parts, confidence);
      return Joiner.on('\t').join(parts);
    }

    @Override
    public ArgumentOutput readOrEmpty(final Symbol docid) throws IOException {
      if (docIDs().contains(docid)) {
        return read(docid);
      } else {
        return ArgumentOutput.from(docid, ImmutableList.<Scored<Response>>of(),
            ImmutableMap.<Response, String>of());
      }
    }

    @Override
    public String toString() {
      return "ArgumentStore <-- " + directory;
    }
  }

  private static void addArgumentParts(final Format format,
      final Response arg, final List<String> parts,
      final double confidence) {
    // TODO generalize this for the {@link ColumnSpec}
    parts.add(arg.docID().toString());
    parts.add(arg.type().toString());
    parts.add(arg.role().toString());
    parts.add(cleanString(arg.canonicalArgument().string()));
    if(format.columnSpec().xdocEntityID().isPresent()) {
      if(arg.xdocEntity().isPresent()) {
        parts.add(arg.xdocEntity().get().asString());
      } else {
        parts.add("");
      }
    }
    parts.add(offsetString(arg.canonicalArgument().charOffsetSpan()));
    parts.add(offsetString(arg.predicateJustifications()));
    parts.add(offsetString(arg.baseFiller()));
    parts.add(offsetString(arg.additionalArgumentJustifications()));
    parts.add(arg.realis().toString());
    parts.add(Double.toString(confidence));
  }

  /**
   * This naively just synchronizes everything to deal with concurrency issues. If it becomes a
   * performance issue we can do something more fine-grained. ~ rgabbard
   */
  private static final class DirectoryAnnotationStore implements AnnotationStore {

    private final File directory;
    private final File lockFile;
    private final LoadingCache<Symbol, AnswerKey> cache;
    private final boolean doCaching;
    private boolean closed = false;
    private final Set<Symbol> docIDs;
    // object which actually creates ResponseAssessments
    // can be used to control how strict we are about
    // the input
    private final AssessmentCreator assessmentCreator;
    private final Format format;

    private DirectoryAnnotationStore(final File directory, AssessmentCreator assessmentCreator,
        final boolean doCaching, final Format format) throws IOException {
      checkArgument(directory.exists(), "Directory %s for annotation store does not exist",
          directory);
      // this is a half-hearted attempt at preventing multiple assessment stores
      //  being opened on the same directory at once.  There is a race condition,
      // but we don't anticipate this class being used concurrently enough to justify
      // dealing with it.
      lockFile = new File(directory, "__lock");
      if (lockFile.exists()) {
        throw new IOException(String.format(
            "Directory %s for assessment store is locked; if this is due to a crash, delete %s",
            directory, lockFile));
      }

      this.directory = checkNotNull(directory);
      this.cache = CacheBuilder.newBuilder().maximumSize(50)
          .build(new CacheLoader<Symbol, AnswerKey>() {
            @Override
            public AnswerKey load(Symbol key) throws Exception {
              return DirectoryAnnotationStore.this.uncachedRead(key);
            }
          });
      this.docIDs = loadInitialDocIds();
      this.assessmentCreator = checkNotNull(assessmentCreator);
      this.doCaching = doCaching;
      this.format = checkNotNull(format);
    }

    @Override
    public synchronized AnswerKey read(final Symbol docid) throws IOException {
      assertNotClosed();
      try {
        if (doCaching) {
          return cache.get(docid);
        } else {
          return uncachedRead(docid);
        }
      } catch (ExecutionException e) {
        log.info("Caught exception {}", e);
        if (e.getCause() instanceof IOException) {
          throw (IOException) e.getCause();
        } else {
          throw new RuntimeException(e.getCause());
        }
      }
    }

    @Override
    public synchronized Set<Symbol> docIDs() throws IOException {
      assertNotClosed();
      return Collections.unmodifiableSet(docIDs);
    }

    private Set<Symbol> loadInitialDocIds() throws IOException {
      return Sets.newHashSet(FluentIterable.from(Arrays.asList(directory.listFiles()))
          .transform(FileUtils.toNameFunction())
          .transform(Symbol.FromString)
          .toSet());
    }

    @Override
    public synchronized void write(final AnswerKey answerKey) throws IOException {
      assertNotClosed();
      cache.invalidate(answerKey.docId());
      docIDs.add(answerKey.docId());

      final File f = new File(directory, answerKey.docId().toString());
      log.info("Writing assessment for doc ID {}", answerKey.docId());
      final PrintWriter out = new PrintWriter(new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(f), Charsets.UTF_8)));

      try {
        // first annotated responses, sorted by response ID
        final Ordering<AssessedResponse> assessedResponseOrdering =
            format.responseOrdering().onResultOf(response());
        for (final AssessedResponse arg : assessedResponseOrdering
            .sortedCopy(answerKey.annotatedResponses())) {
          final List<String> parts = Lists.newArrayList();
          parts.add(format.identifierField(arg.response()));
          addArgumentParts(format, arg.response(), parts, 1.0);
          addAnnotationParts(arg, answerKey.corefAnnotation(), parts);
          out.print(Joiner.on("\t").join(parts) + "\n");
        }
        // then unannotated responses, sorted by reponseID
        for (final Response unannotated : format.responseOrdering()
            .sortedCopy(answerKey.unannotatedResponses())) {
          final List<String> parts = Lists.newArrayList();
          parts.add(format.identifierField(unannotated));
          addArgumentParts(format, unannotated, parts, 1.0);
          addUnannotatedAnnotationParts(unannotated, answerKey.corefAnnotation(), parts);
          out.print(Joiner.on("\t").join(parts) + "\n");
        }
      } finally {
        out.close();
      }
    }

    @Override
    public synchronized void close() {
      closed = true;
      lockFile.delete();
    }

    @Override
    public synchronized AnswerKey readOrEmpty(final Symbol docid) throws IOException {
      assertNotClosed();
      if (docIDs().contains(docid)) {
        return read(docid);
      } else {
        return AnswerKey.createEmpty(docid);
      }
    }

    private synchronized AnswerKey uncachedRead(final Symbol docid) throws IOException {
      final ImmutableList.Builder<AssessedResponse> annotated = ImmutableList.builder();
      final ImmutableList.Builder<Response> unannotated = ImmutableList.builder();
      final CorefAnnotation.Builder corefBuilder = assessmentCreator.corefBuilder(docid);

      final File f = bareOrWithSuffix(directory, docid.asString(), ACCEPTABLE_SUFFIXES);

      final CharSource source = Files.asCharSource(f, UTF_8);
      for (final String line : source.readLines()) {
        try {
          if (line.isEmpty() || line.startsWith("#")) {
            continue;
          }
          final String[] parts = line.split("\t");
          final List<String> annotationParts = Arrays.asList(parts).subList(11, parts.length);

          if (annotationParts.isEmpty()) {
            throw new IOException(String.format(
                "The assessment store file for document ID %s appears to be a system " +
                    "output file with no assessment columns.", docid));
          }

          final Response response = parseArgumentFields(format, ImmutableList.copyOf(parts));
          final AssessmentCreator.AssessmentParseResult annotation =
              parseAnnotation(annotationParts);

          if (annotation.assessment().isPresent()) {
            annotated.add(AssessedResponse.of(response, annotation.assessment().get()));
          } else {
            unannotated.add(response);
          }

          if (annotation.corefId().isPresent()) {
            corefBuilder.corefCAS(response.canonicalArgument(), annotation.corefId().get());
          } else {
            corefBuilder.addUnannotatedCAS(response.canonicalArgument());
          }
        } catch (Exception e) {
          throw new IOException(String.format(
              "While reading answer key for document %s, error on line %s", docid, line), e);
        }
      }

      return assessmentCreator.createAnswerKey(docid, annotated.build(), unannotated.build(),
          corefBuilder.build());
    }

    private static final Set<String> emptyCorefEncodings =
        ImmutableSet.of("NIL", "", "UNANNOTATED");

    private AssessmentCreator.AssessmentParseResult parseAnnotation(final List<String> parts) {
      checkArgument(parts.size() == 7, "Expected parts of size 7, but got %s", parts);

      final Optional<Integer> coreference = emptyCorefEncodings.contains(parts.get(4))
                                            ? Optional.<Integer>absent()
                                            : Optional.of(Integer.parseInt(parts.get(4)));

      if (parts.contains("UNANNOTATED")) {
        return AssessmentCreator.AssessmentParseResult.fromCorefOnly(coreference);
      }

      final Optional<FieldAssessment> AET = FieldAssessment.parseOptional(parts.get(0));
      final Optional<FieldAssessment> AER = FieldAssessment.parseOptional(parts.get(1));
      final Optional<FieldAssessment> casAssessment = FieldAssessment.parseOptional(parts.get(2));
      final Optional<FieldAssessment> baseFillerAssessment =
          FieldAssessment.parseOptional(parts.get(3));

      final Optional<KBPRealis> realis = KBPRealis.parseOptional(parts.get(5));
      final Optional<FillerMentionType> mentionTypeOfCAS = FillerMentionType
          .parseOptional(parts.get(6));

      return assessmentCreator.createAssessmentFromFields(AET, AER, casAssessment,
          realis, baseFillerAssessment, coreference, mentionTypeOfCAS);
    }

    private static void addAnnotationParts(final AssessedResponse assessedResponse,
        final CorefAnnotation corefAnnotation, final List<String> parts) {
      final ResponseAssessment ann = assessedResponse.assessment();
      parts.add(FieldAssessment.asCharacterOrNil(ann.justificationSupportsEventType()));
      parts.add(FieldAssessment.asCharacterOrNil(ann.justificationSupportsRole()));
      parts.add(FieldAssessment.asCharacterOrNil(ann.entityCorrectFiller()));
      parts.add(FieldAssessment.asCharacterOrNil(ann.baseFillerCorrect()));

      final Optional<Integer> corefId = corefAnnotation.corefId(
          assessedResponse.response().canonicalArgument());
      if (corefId.isPresent()) {
        parts.add(Integer.toString(corefId.get()));
      } else {
        parts.add("NIL");
      }
      parts.add(KBPRealis.asString(ann.realis()));
      parts.add(FillerMentionType.stringOrNil(ann.mentionTypeOfCAS()));
    }

    private static final String UNANNOTATED = "UNANNOTATED";

    private static void addUnannotatedAnnotationParts(Response unannotated,
        CorefAnnotation corefAnnotation,
        final List<String> parts) {
      parts.addAll(Collections.nCopies(4, UNANNOTATED));
      final Optional<Integer> corefId = corefAnnotation.corefId(unannotated.canonicalArgument());
      if (corefId.isPresent()) {
        parts.add(corefId.get().toString());
      } else {
        parts.add(UNANNOTATED);
      }
      parts.addAll(Collections.nCopies(2, UNANNOTATED));
    }

    private synchronized void assertNotClosed() {
      if (closed) {
        throw new RuntimeException("Illegal attempt to use a closed assessment store.");
      }
    }

    @Override
    public String toString() {
      return "AnnStore[" + directory + "]";
    }
  }

  private static String offsetString(final Set<CharOffsetSpan> spans) {
    if (spans.isEmpty()) {
      return "NIL";
    }

    final List<String> ret = Lists.newArrayList();

    for (final CharOffsetSpan span : spans) {
      ret.add(offsetString(span));
    }
    return StringUtils.commaJoiner().join(ret);
  }

  private static String offsetString(final CharOffsetSpan span) {
    return String.format("%d-%d", span.startInclusive(), span.endInclusive());
  }

  private static String cleanString(String s) {
    return s.replace('\t', ' ')
        .replace("\r\n", " ")
        .replace('\n', ' ');
  }

  public static Response parseArgumentFields(final Format format, final List<String> parts) {
    final ColumnSpec columnSpec = format.columnSpec();
    // We represent no xdoc id as Optional.absent() and blank on disk
    final Optional<Symbol> readXdocEntityID = columnSpec.xdocEntityID().isPresent() ? Optional
        .of(Symbol.from(parts.get(columnSpec.xdocEntityID().get()))) : Optional.<Symbol>absent();
    final Optional<Symbol> xdocEntity;
    if(readXdocEntityID.isPresent() && readXdocEntityID.get().asString().isEmpty()) {
      xdocEntity = Optional.absent();
    } else {
      xdocEntity = readXdocEntityID;
    }
    return Response.of(Symbol.from(parts.get(columnSpec.docID())),
        Symbol.from(parts.get(columnSpec.eventType())),
        Symbol.from(parts.get(columnSpec.role())),
        KBPString.from(parts.get(columnSpec.CAS()),
            TACKBPEALIOUtils.parseCharOffsetSpan(parts.get(columnSpec.casOffsets()))),
        TACKBPEALIOUtils.parseCharOffsetSpan(parts.get(columnSpec.baseFiller())),
        parseCharOffsetSpans(parts.get(columnSpec.additionalArgumentJustifications())),
        parseCharOffsetSpans(parts.get(columnSpec.predicateJustifications())),
        KBPRealis.parse(parts.get(columnSpec.realis())),
        xdocEntity);
  }

  /**
   * Parses Responses from the 2014, 2015, and 2016 evaluations. Prefer {@link
   * AssessmentSpecFormats#parseArgumentFields(Format, List<String>)}
   */
  @Deprecated
  public static Response parseArgumentFields(final List<String> parts) {
    final Optional<Symbol> xdocEntity = Optional.absent();
    return Response.of(Symbol.from(parts.get(0)),
        Symbol.from(parts.get(1)), Symbol.from(parts.get(2)),
        KBPString.from(parts.get(3), TACKBPEALIOUtils.parseCharOffsetSpan(parts.get(4))),
        TACKBPEALIOUtils.parseCharOffsetSpan(parts.get(6)),
        parseCharOffsetSpans(parts.get(7)),
        parseCharOffsetSpans(parts.get(5)),
        KBPRealis.parse(parts.get(8)), xdocEntity);
  }


  private static final Splitter onCommas =
      Splitter.on(",").trimResults().omitEmptyStrings();

  private static ImmutableSet<CharOffsetSpan> parseCharOffsetSpans(final String s) {
    if ("NIL".equals(s)) {
      return ImmutableSet.of();
    }

    final ImmutableSet.Builder<CharOffsetSpan> ret = ImmutableSet.builder();

    for (final String span : onCommas.split(s)) {
      ret.add(TACKBPEALIOUtils.parseCharOffsetSpan(span));
    }

    final ImmutableSet<CharOffsetSpan> spans = ret.build();

    if (spans.isEmpty()) {
      throw new RuntimeException(String.format("Empty spans sets must be indicated by NIL"));
    }

    return spans;
  }

  private static final ImmutableSet<String> ACCEPTABLE_SUFFIXES = ImmutableSet.of("tab", "tsv");

  public static File bareOrWithSuffix(final File directory, final String filename,
      ImmutableSet<String> suffixes)
      throws FileNotFoundException {
    final List<File> attempts = Lists.newArrayList();
    final List<File> successfulAttempts = Lists.newArrayList();

    final File bareAttempt = new File(directory, filename);
    attempts.add(bareAttempt);
    if (bareAttempt.isFile()) {
      successfulAttempts.add(bareAttempt);
    }

    for (final String suffix : suffixes) {
      final File attempt = new File(directory, filename + "." + suffix);
      attempts.add(attempt);

      if (attempt.isFile()) {
        successfulAttempts.add(attempt);
      }
    }

    if (!successfulAttempts.isEmpty()) {
      if (successfulAttempts.size() == 1) {
        return attempts.get(0);
      } else {
        throw new FileNotFoundException("Multiple alternative files exist: " + successfulAttempts);
      }
    } else {
      throw new FileNotFoundException("None of " + attempts + " exist");
    }
  }
}
