package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@link LinkingStore} implementations which uses a directory with one
 * file per doc ID.  Each file contains a {@link com.bbn.kbp.events2014.ResponseSet} on each line
 * in the form of response {@link com.bbn.kbp.events2014.Response#uniqueIdentifier()}s separated
 * by spaces. Finally, there is a line at the bottom in the same format except prefixed by
 * "INCOMPLETE " which lists responses which have neither been linked nor marked as singletons.
 * The incomplete set is for the use of the annotation tool only.
 *
 * Both the incomplete set and the response set lines are sorted within themselves by response ID.
 * The response set lines are sorted among themselves lexicographically by response ID.
 *
 * Blank lines are allowed, as are comment lines (the first character on the line must be #).
 */
final class DirectoryLinkingStore implements LinkingStore {

  private final File directory;
  private final LinkingFileLoader linkingLoader;
  private final LinkingFileWriter linkingWriter;
  private boolean closed = false;

  DirectoryLinkingStore(File directory, LinkingFileLoader linkingLoader,
      LinkingFileWriter linkingWriter) {
    checkArgument(directory.isDirectory(), "Specified directory %s for "
        + "linking store is not a directory", directory);
    this.directory = checkNotNull(directory);
    this.linkingLoader = checkNotNull(linkingLoader);
    this.linkingWriter = checkNotNull(linkingWriter);
  }

  @Override
  public ImmutableSet<Symbol> docIDs() throws IOException {
    checkNotClosed();

    return FluentIterable.from(Arrays.asList(directory.listFiles()))
        .transform(FileUtils.toNameFunction())
        .transform(Symbol.FromString)
        .toSet();
  }

  @Override
  public Optional<ResponseLinking> read(AnswerKey answerKey) throws IOException {
    return read(answerKey.docId(), answerKey.allResponses());
  }

  @Override
  public Optional<ResponseLinking> read(ArgumentOutput argumentOutput) throws IOException {
    return read(argumentOutput.docId(), argumentOutput.responses());
  }

  public Optional<ResponseLinking> read(Symbol docID, Set<Response> responses)
      throws IOException {
    return read(docID, responses, Optional.<ImmutableMap<String, String>>absent(),
        Optional.<ImmutableMap.Builder<String, String>>absent());
  }

  private static final ImmutableSet<String> ACCEPTABLE_SUFFIXES = ImmutableSet.of("linking");

  private Optional<ResponseLinking> read(Symbol docID, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignResponseIDToLocal,
      Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException {
    checkNotClosed();

    final File f;
    try {
      f = AssessmentSpecFormats.bareOrWithSuffix(directory, docID.toString(),
          ACCEPTABLE_SUFFIXES);
    } catch (FileNotFoundException ioe) {
      return Optional.absent();
    }

    return Optional.of(linkingLoader.read(docID, Files.asCharSource(f, UTF_8), responses,
        foreignResponseIDToLocal, foreignLinkingIdToLocal));
  }

  @Override
  public Optional<ResponseLinking> readTransformingIDs(Symbol docID, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignResponseIDToLocal,
      Optional<ImmutableMap.Builder<String, String>> foreignLinkingIDToLocal)
      throws IOException {
    return read(docID, responses, foreignResponseIDToLocal, foreignLinkingIDToLocal);
  }

  @Override
  public void write(ResponseLinking responseLinking) throws IOException {
    checkNotClosed();
    final File f = new File(directory, responseLinking.docID().toString());
    linkingWriter.write(responseLinking, Files.asCharSink(f, Charsets.UTF_8));
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

  @Override
  public String toString() {
    return "DirectoryLinkingStore(" + directory + ")";
  }
}
