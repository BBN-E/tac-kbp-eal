package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.skip;
import static com.google.common.collect.Iterables.transform;

public final class LinkingStoreSource {
  private final LinkingFileLoader linkingFileLoader;
  private final LinkingFileWriter linkingFileWriter;

  private LinkingStoreSource(final LinkingFileLoader linkingFileLoader,
      final LinkingFileWriter linkingFileWriter) {
    this.linkingFileLoader = checkNotNull(linkingFileLoader);
    this.linkingFileWriter = checkNotNull(linkingFileWriter);
  }

  public static LinkingStoreSource createFor2015() {
    return new LinkingStoreSource(new KBPSpecLinkingLoader(false), new KBPSpecLinkingWriter());
  }

  public LinkingStore openOrCreateLinkingStore(File directory) {
    directory.mkdirs();
    return new DirectoryLinkingStore(directory, linkingFileLoader, linkingFileWriter);
  }

  public LinkingStore openLinkingStore(final File directory) throws FileNotFoundException {
    if (!directory.isDirectory()) {
      throw new FileNotFoundException("Not a directory: " + directory);
    }
    return new DirectoryLinkingStore(directory, linkingFileLoader, linkingFileWriter);
  }
}

interface LinkingFileLoader {
  ResponseLinking read(Symbol docID, CharSource linkingFile, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignIDToLocal) throws IOException;
}

final class KBPSpecLinkingLoader implements LinkingFileLoader {
  private final boolean expectIDs;

  public KBPSpecLinkingLoader(final boolean expectIDs) {
    this.expectIDs = expectIDs;
  }

  public ResponseLinking read(Symbol docID, CharSource source, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignIDToLocal) throws IOException {
    final Map<String, Response> responsesByUID = Maps.uniqueIndex(responses,
        ResponseFunctions.uniqueIdentifier());

    final ImmutableSet.Builder<ResponseSet> ret = ImmutableSet.builder();
    Optional<ImmutableSet<Response>> incompleteResponses = Optional.absent();

    int lineNo = 0;
    try {
      for (final String line : source.readLines()) {
        ++lineNo;
        // empty lines are allowed, and comments on lines
        // beginning with '#'
        if (line.isEmpty() || line.charAt(0) == '#') {
          continue;
        }
        final Iterable<String> parts = StringUtils.OnTabs.split(line);
        if (line.startsWith("INCOMPLETE")) {
          if (!incompleteResponses.isPresent()) {
            incompleteResponses = Optional.of(parseResponses(skip(parts, 1),
                foreignIDToLocal, responsesByUID));
          } else {
            throw new IOException("Cannot have two INCOMPLETE lines");
          }
        } else {
          ret.add(ResponseSet.of(parseResponses(StringUtils.OnTabs.split(line), foreignIDToLocal, responsesByUID)));
        }
      }
    } catch (Exception e) {
      throw new IOException("While reading " + docID + ", on line " + lineNo + ": ", e);
    }

    return ResponseLinking.builder().docID(docID).responseSets(ret.build())
        .incompleteResponses(incompleteResponses.or(ImmutableSet.<Response>of())).build();
  }

  @Nonnull
  private ImmutableSet<Response> parseResponses(final Iterable<String> parts,
      final Optional<ImmutableMap<String, String>> foreignIDToLocal,
      final Map<String, Response> responsesByUID) throws IOException {
    final ImmutableSet.Builder<Response> responseSetB = ImmutableSet.builder();
    for (String idString : parts) {
        responseSetB.add(responseForID(idString, foreignIDToLocal, responsesByUID));
    }
    return responseSetB.build();
  }

  @Nonnull
  private Response responseForID(final String idString,
      final Optional<ImmutableMap<String, String>> foreignIDToLocal,
      final Map<String, Response> responsesByUID) throws IOException {
    final String newID;
    // for translating a foreign id
    if (foreignIDToLocal.isPresent()) {
      if (!foreignIDToLocal.get().containsKey(idString)) {
        throw new RuntimeException(
            "Could not find a new id for " + idString + " have id mappings "
                + foreignIDToLocal.get());
      }
      newID = foreignIDToLocal.get().get(idString);
    } else {
      newID = idString;
    }
    final Response responseForIDString = responsesByUID.get(newID);
    if (responseForIDString == null) {
      throw new IOException("ID " + newID + "(original ID) " + idString
              + " cannot be resolved using provided response store. Known"
              + "response IDs are " + responsesByUID.keySet()
              + "transformed response ids are " + foreignIDToLocal);
    }
    return responseForIDString;
  }
}

interface LinkingFileWriter {
  void write(ResponseLinking linking, CharSink sink) throws IOException;
}

final class KBPSpecLinkingWriter implements LinkingFileWriter {
  private static final Joiner TAB_JOINER = Joiner.on("\t");

  @Override
  public void write(ResponseLinking responseLinking, CharSink sink) throws IOException {
    final List<String> lines = Lists.newArrayList();
    for (final ResponseSet responseSet : responseLinking.responseSets()) {
      lines.add(TAB_JOINER.join(
          transform(responseSet.asSet(), ResponseFunctions.uniqueIdentifier())));
    }

    // incompletes last
    lines.add("INCOMPLETE\t" + TAB_JOINER.join(
        transform(responseLinking.incompleteResponses(), ResponseFunctions.uniqueIdentifier())));

    sink.writeLines(lines, "\n");
  }
}

/**
 * {@link com.bbn.kbp.events2014.io.LinkingStore} implementations which uses a directory with one
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
        .transform(FileUtils.ToName)
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
    return read(docID, responses, Optional.<ImmutableMap<String, String>>absent());
  }

  private static final ImmutableSet<String> ACCEPTABLE_SUFFIXES = ImmutableSet.of("linking");

  private Optional<ResponseLinking> read(Symbol docID, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignIDToLocal)
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
        foreignIDToLocal));
  }

  @Override
  public Optional<ResponseLinking> readTransformingIDs(Symbol docID, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignIDToLocal)
      throws IOException {
    return read(docID, responses, foreignIDToLocal);
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
