package com.bbn.kbp.events2014.io;

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

import com.bbn.kbp.events2014.*;
import com.google.common.base.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ResponseAssessment.MentionType;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

import static com.google.common.base.Charsets.UTF_8;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Handles file formats defined in the KBP 2014 Event Argument Task assessment specifications.

 * These formats represent each event argument as a single line of tab-separated fields.
 * Blank lines and lines beginning with "#" are ignored as comments.  No fields may contain tabs.
 * The columns are as described in the Event Argument Attachment task assessment specification. They are
 * not duplicated here to prevent out-of-sync documentation.
 *
 * @author Ryan Gabbard
 *
 */
public final class AssessmentSpecFormats {
    private static Logger log = LoggerFactory.getLogger(AssessmentSpecFormats.class);
	private AssessmentSpecFormats() {
		throw new UnsupportedOperationException();
	}

    /**
     * Creates a directory-based assessment store in the specified directory. Will throw an
     * {@link java.io.IOException} if the directory exists and is non-empty.
     * @param directory
     * @return
     * @throws IOException
     */
	public static AnnotationStore createAnnotationStore(final File directory) throws IOException {
		if (directory.exists() && !FileUtils.isEmptyDirectory(directory)) {
			throw new IOException(String.format(
				"Non-empty output directory %s when attempting to create assessment store", directory));
		}
		directory.mkdirs();
		return new DirectoryAnnotationStore(directory);
	}

    /**
     * Opens an existing assessment store stored in the given directory.  The implementation makes some
     * attempt to avoid having multiple assessment store objects writing to the same directory,
     * which can result in data corruption, but this is not guaranteed.
     *
     * @param directory
     * @return
     * @throws IOException
     */
	public static AnnotationStore openAnnotationStore(final File directory) throws IOException {
		if (!directory.exists() || !directory.isDirectory()) {
            throw new IOException(String.format("Annotation store directory %s either does not exist or is not a directory", directory));
        }
		return new DirectoryAnnotationStore(directory);
	}

    public static AnnotationStore openOrCreateAnnotationStore(final File directory) throws IOException {
        directory.mkdirs();
        return new DirectoryAnnotationStore(directory);
    }

    /**
     * Creates a new system output store in the specified directory. If the directory is
     * non-empty, its contents are deleted.
     * @param directory
     * @return
     */
	public static SystemOutputStore createSystemOutputStore(final File directory) {
		if (directory.exists()) {
            log.warn("Deleting existing contents of system output store {}", directory);
			directory.delete();
		}
		directory.mkdirs();
		return new DirectorySystemOutputStore(directory);
	}

    /**
     * Opens an existing system output store.
     * @param directory
     * @return
     */
	public static SystemOutputStore openSystemOutputStore(final File directory) {
		checkArgument(directory.exists() && directory.isDirectory() );
		return new DirectorySystemOutputStore(directory);
	}

	private static final class DirectorySystemOutputStore implements SystemOutputStore {
		private static Logger log = LoggerFactory.getLogger(DirectorySystemOutputStore.class);

		private final File directory;

		private DirectorySystemOutputStore(final File directory) {
			checkArgument(directory.isDirectory());
			this.directory = checkNotNull(directory);
		}

		@Override
		public SystemOutput read(final Symbol docid) throws IOException {
			final File f = new File(directory, docid.toString());
			if (!f.exists()) {
				throw new FileNotFoundException(String.format("%s not found", f.getAbsolutePath()));
			}

			final ImmutableList.Builder<Scored<Response>> ret = ImmutableList.builder();

			for (final String line : Files.asCharSource(f, UTF_8).readLines()) {
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				final List<String> parts = ImmutableList.copyOf(StringUtils.OnTabs.split(line));
				try {
                    // we ignore the first field because input system IDs are currently not preserved
					final double confidence = Double.parseDouble(parts.get(10));
					ret.add(Scored.from(parseArgumentFields(parts.subList(1, parts.size())), confidence));
				} catch (final Exception e) {
					throw new RuntimeException(String.format("Invalid line: %s", line), e);
				}
			}

			return SystemOutput.from(docid, ret.build());
		}

		@Override
		public ImmutableSet<Symbol> docIDs() throws IOException {
			return FluentIterable.from(Arrays.asList(directory.listFiles()))
				.transform(FileUtils.ToName)
				.transform(Symbol.FromString)
				.toSet();
		}


		@Override
		public void write(final SystemOutput output) throws IOException {
			final File f = new File(directory, output.docId().toString());
			final PrintWriter out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(f), Charsets.UTF_8)));

			try {
                for (final Response response : Response.ById.sortedCopy(output.responses())) {
					out.print(response.responseID());
					out.print("\t");
					final double confidence = output.confidence(response);
					out.println(argToString(response, confidence));
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
			addArgumentParts(arg, parts, confidence);
			return Joiner.on('\t').join(parts);
		}

		@Override
		public SystemOutput readOrEmpty(final Symbol docid) throws IOException {
			if (docIDs().contains(docid)) {
				return read(docid);
			} else {
				return SystemOutput.from(docid, ImmutableList.<Scored<Response>>of());
			}
		}
	}

	private static void addArgumentParts(final Response arg, final List<String> parts,
			final double confidence)
	{
		parts.add(arg.docID().toString());
		parts.add(arg.type().toString());
		parts.add(arg.role().toString());
		parts.add(cleanString(arg.canonicalArgument().string()));
		parts.add(offsetString(arg.canonicalArgument().charOffsetSpan()));
		parts.add(offsetString(arg.predicateJustifications()));
		parts.add(offsetString(arg.baseFiller()));
		parts.add(offsetString(arg.additionalArgumentJustifications()));
		parts.add(arg.realis().toString());
		parts.add(Double.toString(confidence));
	}

    /**
     * This naively just synchronizes everything to deal with concurrency issues. If it becomes
     * a performance issue we can do something more fine-grained. ~ rgabbard
     */
	private static final class DirectoryAnnotationStore implements AnnotationStore {
		private final File directory;
        private final File lockFile;
        private final LoadingCache<Symbol, AnswerKey> cache;
        private boolean closed = false;
        private final Set<Symbol> docIDs;

		private DirectoryAnnotationStore(final File directory) throws IOException {
			checkArgument(directory.exists());
            // this is a half-hearted attempt at preventing multiple assessment stores
            //  being opened on the same directory at once.  There is a race condition,
            // but we don't anticipate this class being used concurrently enough to justify
            // dealing with it.
            lockFile = new File(directory, "__lock");
            if (lockFile.exists()) {
                throw new IOException(String.format("Directory %s for assessment store is locked; if this is due to a crash, delete %s",
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
		}

        @Override
        public synchronized AnswerKey read(final Symbol docid) throws IOException {
            assertNotClosed();
            try {
                return cache.get(docid);
            } catch (ExecutionException e) {
                log.info("Caught exception {}", e);
                if (e.getCause() instanceof IOException) {
                    throw (IOException)e.getCause();
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
                    .transform(FileUtils.ToName)
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
				for (final AsssessedResponse arg : AsssessedResponse.ById.sortedCopy(answerKey.annotatedResponses())) {
					final List<String> parts = Lists.newArrayList();
					parts.add(Integer.toString(arg.response().responseID()));
					addArgumentParts(arg.response(), parts, 1.0);
					addAnnotationParts(arg.assessment(), parts);
					out.println(Joiner.on("\t").join(parts));
				}
                // then unannotated responses, sorted by reponseID
				for (final Response unannotated : Response.ById.sortedCopy(answerKey.unannotatedResponses())) {
					final List<String> parts = Lists.newArrayList();
					parts.add(Integer.toString(unannotated.responseID()));
					addArgumentParts(unannotated, parts, 1.0);
					addUnannotatedAnnotationParts(parts);
					out.println(Joiner.on("\t").join(parts));
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
				return AnswerKey.from(docid, ImmutableList.<AsssessedResponse>of(),
					ImmutableList.<Response>of());
			}
		}

        private synchronized AnswerKey uncachedRead(final Symbol docid) throws IOException {
            final ImmutableList.Builder<AsssessedResponse> annotated = ImmutableList.builder();
            final ImmutableList.Builder<Response> unannotated = ImmutableList.builder();

            final CharSource source = Files.asCharSource(new File(directory, docid.toString()), UTF_8);
            for (final String line : source.readLines()) {
                try {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    final String[] parts = line.split("\t");
                    final List<String> argumentParts = Arrays.asList(parts).subList(1, 11);
                    final List<String> annotationParts = Arrays.asList(parts).subList(11, parts.length);

                    if (annotationParts.isEmpty()) {
                        throw new IOException(String.format(
                                "The assessment store file for document ID %s appears to be a system "+
                                        "output file with no assessment columns.", docid));
                    }

                    final Response response = parseArgumentFields(argumentParts);
                    final Optional<ResponseAssessment> annotation =
                            parseAnnotation(annotationParts);

                    if (annotation.isPresent()) {
                        annotated.add(AsssessedResponse.from(response, annotation.get()));
                    } else {
                        unannotated.add(response);
                    }
                } catch (Exception e) {
                    throw new IOException(String.format(
                            "While reading answer key for document %s, error on line %s", docid, line), e);
                }
            }

            return AnswerKey.from(docid, annotated.build(), unannotated.build());
        }

        private static Optional<ResponseAssessment> parseAnnotation(final List<String> parts) {
            checkArgument(parts.size() == 7, "Expected parts of size 7, but got %s", parts);

            if (parts.contains("UNANNOTATED")) {
                return Optional.absent();
            }

            final FieldAssessment AET = FieldAssessment.parse(parts.get(0));
            final Optional<FieldAssessment> AER = FieldAssessment.parseOptional(parts.get(1));
            final Optional<FieldAssessment> casAssessment = FieldAssessment.parseOptional(parts.get(2));
            final Optional<FieldAssessment> baseFillerAssessment = FieldAssessment.parseOptional(parts.get(3));
            final Optional<Integer> coreference = parts.get(4).equals("NIL")?Optional.<Integer>absent():Optional.of(Integer.parseInt(parts.get(4)));
            final Optional<KBPRealis> realis = KBPRealis.parseOptional(parts.get(5));
            final Optional<MentionType> mentionTypeOfCAS = MentionType.parseOptional(parts.get(6));

            return Optional.of(ResponseAssessment.create(AET, AER, casAssessment,
                    realis, baseFillerAssessment, coreference, mentionTypeOfCAS));
        }

        private static void addAnnotationParts(final ResponseAssessment ann, final List<String> parts) {
			parts.add(ann.justificationSupportsEventType().asCharacter());
			parts.add(FieldAssessment.asCharacterOrNil(ann.justificationSupportsRole()));
			parts.add(FieldAssessment.asCharacterOrNil(ann.entityCorrectFiller()));
			parts.add(FieldAssessment.asCharacterOrNil(ann.baseFillerCorrect()));
			if (ann.coreferenceId().isPresent()) {
				parts.add(Integer.toString(ann.coreferenceId().get()));
			} else {
				parts.add("NIL");
			}
			parts.add(KBPRealis.asString(ann.realis()));
			parts.add(MentionType.stringOrNil(ann.mentionTypeOfCAS()));
		}

		private static void addUnannotatedAnnotationParts(final List<String> parts) {
			parts.addAll(Collections.nCopies(7, "UNANNOTATED"));
		}

        private synchronized void assertNotClosed() {
            if (closed) {
                throw new RuntimeException("Illegal attempt to use a closed assessment store.");
            }
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
		return StringUtils.CommaJoiner.join(ret);
	}

	private static String offsetString(final CharOffsetSpan span) {
		return String.format("%d-%d", span.startInclusive(), span.endInclusive());
	}

	private static String cleanString(String s) {
		s = s.replace('\t', ' ');
		s = s.replace("\r\n", " ");
		s = s.replace('\n', ' ');
		return s;
	}

	public static Response parseArgumentFields(final List<String> parts) {
		return Response.createFrom(Symbol.from(parts.get(0)),
			Symbol.from(parts.get(1)), Symbol.from(parts.get(2)),
			KBPString.from(parts.get(3), parseCharOffsetSpan(parts.get(4))),
			parseCharOffsetSpan(parts.get(6)),
			parseCharOffsetSpans(parts.get(7)),
			parseCharOffsetSpans(parts.get(5)),
			KBPRealis.valueOf(parts.get(8)));
	}

	private static CharOffsetSpan parseCharOffsetSpan(final String s) {
		final String[] parts = s.split("-");
		if (parts.length != 2) {
			throw new RuntimeException(String.format("Invalid span %s", s));
		}
		return CharOffsetSpan.fromOffsetsOnly(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
	}

    private static final Splitter onCommas =
            Splitter.on(",").trimResults().omitEmptyStrings();
	private static ImmutableSet<CharOffsetSpan> parseCharOffsetSpans(final String s) {
        if ("NIL".equals(s)) {
            return ImmutableSet.of();
        }

		final ImmutableSet.Builder<CharOffsetSpan> ret = ImmutableSet.builder();

		for (final String span : onCommas.split(s)) {
			ret.add(parseCharOffsetSpan(span));
		}

		final ImmutableSet<CharOffsetSpan> spans = ret.build();

        if (spans.isEmpty()) {
            throw new RuntimeException(String.format("Empty spans sets must be indicated by NIL"));
        }

        return spans;
	}


}