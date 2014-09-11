package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.*;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.*;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

public final class LinkingSpecFormats {
    private LinkingSpecFormats() {
        throw new UnsupportedOperationException();
    }

    public static LinkingStore openOrCreateLinkingStore(File directory) {
        directory.mkdirs();
        return new DirectoryLinkingStore(directory);
    }

    /**
     * {@link com.bbn.kbp.events2014.io.LinkingStore} implementations which uses a directory
     * with one file per doc ID.  Each file contains a {@link com.bbn.kbp.events2014.ResponseSet}
     * on each line in the form of response {@link com.bbn.kbp.events2014.Response#uniqueIdentifier()}s
     * separated by spaces. Finally, there is a line at the bottom in the same format except
     * prefixed by "INCOMPLETE " which lists responses which have neither been linked
     * nor marked as singletons.  The incomplete set is for the use of the annotation tool only.
     *
     * Both the incomplete set and the response set lines are sorted within themselves by
     * response ID.  The response set lines are sorted among themselves lexicographically
     * by response ID.
     *
     * Blank lines are allowed, as are comment lines (the first character on the line
     * must be #).
     *
     */
    private static final class DirectoryLinkingStore implements LinkingStore {
        private final File directory;
        private boolean closed = false;

        private DirectoryLinkingStore(File directory) {
            checkArgument(directory.isDirectory(), "Specified directory %s for "
                    + "linking store is not a directory", directory);
            this.directory = checkNotNull(directory);
        }

        @Override
        public ImmutableSet<Symbol> docIDs() throws IOException {
            checkNotClosed();

            return FluentIterable.from(Arrays.asList(directory.listFiles()))
                    .transform(FileUtils.ToName)
                    .transform(Symbol.FromString)
                    .toSet();
        }

        @Override
        public ResponseLinking read(AnswerKey answerKey) throws IOException {
            return read(answerKey.docId(), answerKey.allResponses());
        }

        @Override
        public ResponseLinking read(SystemOutput systemOutput) throws IOException {
            return read(systemOutput.docId(), systemOutput.responses());
        }

        private static final Splitter ON_TABS = Splitter.on('\t').trimResults();
        public ResponseLinking read(Symbol docID, ImmutableSet<Response> responses) throws IOException {
            checkNotClosed();

            final File f = new File(directory, docID.toString());
            if (!f.exists()) {
                throw new FileNotFoundException(String.format("File %s for doc ID %s not found", f.getAbsolutePath(),
                        docID));
            }

            final Map<String, Response> responsesByUID = Maps.uniqueIndex(responses,
                    Response.uniqueIdFunction());

            final ImmutableSet.Builder<ResponseSet> ret = ImmutableSet.builder();
            ImmutableSet<Response> incompleteResponses = ImmutableSet.of();
            
            int lineNo = 0;
            for (final String line : Files.asCharSource(f, UTF_8).readLines()) {
                ++lineNo;
                // empty lines are allowed, and comments on lines
                // beginning with '#'
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                boolean incomplete;
                final Iterable<String> parts;
                if (line.startsWith("INCOMPLETE")) {
                    incomplete = true;
                    parts = Iterables.skip(ON_TABS.split(line), 1);
                } else {
                    incomplete = false;
                    parts = ON_TABS.split(line);
                }

                final ImmutableSet.Builder<Response> responseSet = ImmutableSet.builder();
                for (String idString : parts) {
                    final Response responseForIDString = responsesByUID.get(idString);
                    if (responseForIDString == null) {
                        throw new IOException("On line " + lineNo +", ID " + idString
                            + " cannot be resolved using provided response store. Known"
                            + "response IDs are " + responsesByUID.keySet());
                    }
                    responseSet.add(responseForIDString);
                }
                if (incomplete) {
                    incompleteResponses = responseSet.build();
                } else {
                    ret.add(ResponseSet.from(responseSet.build()));
                }
            }

            return ResponseLinking.from(docID, ret.build(), incompleteResponses);
        }

        @Override
        public ResponseLinking readOrEmpty(SystemOutput systemOutput) throws IOException {
            checkNotClosed();

            final File f = new File(directory, systemOutput.docId().toString());
            if (!f.exists()) {
                return ResponseLinking.createEmpty(systemOutput.docId());
            } else {
                return read(systemOutput);
            }
        }


        @Override
        public void write(ResponseLinking responseLinking) throws IOException {
            checkNotClosed();

            final List<String> lines = Lists.newArrayList();
            for (final ResponseSet responseSet : responseLinking.responseSets()) {
                lines.add(StringUtils.SpaceJoiner.join(
                        transform(responseSet.asSet(), Response.uniqueIdFunction())));
            }

            // incompletes last
            lines.add(StringUtils.SpaceJoiner.join(
                    transform(responseLinking.incompleteResponses(), Response.uniqueIdFunction())));

            final File f = new File(directory, responseLinking.docID().toString());
            Files.asCharSink(f, Charsets.UTF_8).writeLines(lines);
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }

        private void checkNotClosed() throws IOException {
            if (closed) {
                throw new IOException("Cannot perform I/O operations on a closed output store");
            }
        }
    }
}
