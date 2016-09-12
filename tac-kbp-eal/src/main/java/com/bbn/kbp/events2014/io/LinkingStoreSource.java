package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.collections.LaxImmutableMapBuilder;
import com.bbn.bue.common.collections.MapUtils;
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
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Iterables.skip;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Ordering.usingToString;

public final class LinkingStoreSource {

  private final LinkingFileLoader linkingFileLoader;
  private final LinkingFileWriter linkingFileWriter;

  private LinkingStoreSource(final LinkingFileLoader linkingFileLoader,
      final LinkingFileWriter linkingFileWriter) {
    this.linkingFileLoader = checkNotNull(linkingFileLoader);
    this.linkingFileWriter = checkNotNull(linkingFileWriter);
  }

  public static LinkingStoreSource createFor2015() {
    return new LinkingStoreSource(new KBPSpec2015LinkingLoader(), new LinkingWriter2015());
  }

  public static LinkingStoreSource createFor2016() {
    return new LinkingStoreSource(new KBPSpec2016LinkingLoader(), new LinkingWriter2016());
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
      Optional<ImmutableMap<String, String>> foreignResponseIDToLocal,
      final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException;
}

@Value.Immutable
abstract class AbstractLinkingLine {

  @Value.Parameter
  abstract ResponseSet responses();

  @Value.Parameter
  abstract Optional<String> id();
}

abstract class AbstractKBPSpecLinkingLoader implements LinkingFileLoader {

  public ResponseLinking read(Symbol docID, CharSource source, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignResponseIDToLocal,
      final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException {
    final Map<String, Response> responsesByUID = Maps.uniqueIndex(responses,
        ResponseFunctions.uniqueIdentifier());

    final ImmutableSet.Builder<ResponseSet> responseSetsB = ImmutableSet.builder();
    Optional<ImmutableSet<Response>> incompleteResponses = Optional.absent();
    ImmutableMap.Builder<String, ResponseSet> responseSetIds = ImmutableMap.builder();

    int lineNo = 0;
    try {
      for (final String line : source.readLines()) {
        ++lineNo;
        // empty lines are allowed, and comments on lines
        // beginning with '#'
        if (line.isEmpty() || line.charAt(0) == '#') {
          continue;
        }
        final List<String> parts = StringUtils.OnTabs.splitToList(line);
        if (line.startsWith("INCOMPLETE")) {
          if (!incompleteResponses.isPresent()) {
            incompleteResponses = Optional.of(parseResponses(skip(parts, 1),
                foreignResponseIDToLocal, responsesByUID));
          } else {
            throw new IOException("Cannot have two INCOMPLETE lines");
          }
        } else {
          final ImmutableLinkingLine linkingLine = parseResponseSetLine(parts,
              foreignResponseIDToLocal, responsesByUID);
          responseSetsB.add(linkingLine.responses());
          if (linkingLine.id().isPresent()) {
            responseSetIds.put(linkingLine.id().get(), linkingLine.responses());
          }
        }
      }
    } catch (Exception e) {
      throw new IOException("While reading " + docID + ", on line " + lineNo + ": ", e);
    }

    final ImmutableSet<ResponseSet> responseSets = responseSetsB.build();
    final ResponseLinking.Builder responseLinking =
        ResponseLinking.builder().docID(docID).responseSets(responseSets)
            .incompleteResponses(incompleteResponses.or(ImmutableSet.<Response>of()));
    handleResponseSetIDs(responseLinking, responseSets, responseSetIds.build(),
        foreignLinkingIdToLocal);
    return responseLinking.build();
  }

  protected abstract void handleResponseSetIDs(final ResponseLinking.Builder responseLinking,
      final ImmutableSet<ResponseSet> responseSets,
      final ImmutableMap<String, ResponseSet> responseIDs,
      final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException;

  protected abstract ImmutableLinkingLine parseResponseSetLine(List<String> parts,
      Optional<ImmutableMap<String, String>> foreignIDToLocal, Map<String, Response> responsesByUID)
      throws IOException;

  @Nonnull
  protected final ImmutableSet<Response> parseResponses(final Iterable<String> parts,
      final Optional<ImmutableMap<String, String>> foreignIDToLocal,
      final Map<String, Response> responsesByUID) throws IOException {
    final ImmutableSet.Builder<Response> responseSetB = ImmutableSet.builder();
    for (String idString : parts) {
      responseSetB.add(responseForID(idString, foreignIDToLocal, responsesByUID));
    }
    return responseSetB.build();
  }

  @Nonnull
  protected final Response responseForID(final String idString,
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

class KBPSpec2015LinkingLoader extends AbstractKBPSpecLinkingLoader {

  @Override
  protected void handleResponseSetIDs(final ResponseLinking.Builder responseLinking,
      final ImmutableSet<ResponseSet> responseSets,
      final ImmutableMap<String, ResponseSet> responseIDs,
      final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException {
    if (!responseIDs.isEmpty()) {
      throw new IOException("IDs not allowed in 2014 linking format");
    }
  }

  @Override
  protected ImmutableLinkingLine parseResponseSetLine(final List<String> parts,
      final Optional<ImmutableMap<String, String>> foreignIDToLocal,
      final Map<String, Response> responsesByUID)
      throws IOException {
    return ImmutableLinkingLine
        .of(ResponseSet.of(parseResponses(parts, foreignIDToLocal, responsesByUID)),
            Optional.<String>absent());
  }
}

class KBPSpec2016LinkingLoader extends AbstractKBPSpecLinkingLoader {

  private static final Logger log = LoggerFactory.getLogger(KBPSpec2016LinkingLoader.class);

  @Override
  protected void handleResponseSetIDs(final ResponseLinking.Builder responseLinking,
      final ImmutableSet<ResponseSet> responseSets,
      final ImmutableMap<String, ResponseSet> responseIDs,
      final Optional<ImmutableMap.Builder<String, String>> foreignLinkingIdToLocal)
      throws IOException {
    if (responseSets.size() == responseIDs.size()) {
      responseLinking.responseSetIds(ImmutableBiMap.copyOf(responseIDs));
      responseLinking.build();
    } else if (responseSets.size() > responseIDs.size() || !foreignLinkingIdToLocal.isPresent()) {
      throw new IOException("Read " + responseSets.size() + " response sets but "
          + responseIDs.size() + " ID assignments");
    } else {
      log.warn(
          "Warning - converting ResponseSet IDs and saving them, this is almost definitely an error!");
      final ImmutableMultimap<ResponseSet, String> responseSetToIds =
          responseIDs.asMultimap().inverse();
      final LaxImmutableMapBuilder<String, ResponseSet> idsMapB =
          MapUtils.immutableMapBuilderAllowingSameEntryTwice();

      for (final Map.Entry<ResponseSet, Collection<String>> setAndIds : responseSetToIds.asMap()
          .entrySet()) {

        final Collection<String> ids = setAndIds.getValue();
        final String selectedID =
            checkNotNull(getFirst(usingToString().immutableSortedCopy(ids), null));
        for (final String oldId : ids) {
          log.debug("Selecting id {} for cluster {}", selectedID, oldId);
          foreignLinkingIdToLocal.get().put(oldId, selectedID);
          idsMapB.put(selectedID, responseIDs.get(oldId));
        }
      }
      responseLinking.responseSetIds(ImmutableBiMap.copyOf(idsMapB.build()));
    }
  }

  @Override
  protected ImmutableLinkingLine parseResponseSetLine(List<String> parts,
      final Optional<ImmutableMap<String, String>> foreignIDToLocal,
      final Map<String, Response> responsesByUID)
      throws IOException {
    if (parts.size() > 2) {
      log.warn(
          "IDs provided using tabs! This is contrary to the guidelines for the 2016 eval and may be changed!");
    }
    if (parts.size() == 2 && parts.get(1).contains(" ")) {
      final List<String> responseIDs = StringUtils.onSpaces().splitToList(parts.get(1));
      parts = ImmutableList.copyOf(Iterables.concat(ImmutableList.of(parts.get(0)), responseIDs));
    }
    if (parts.size() >= 2) {
      return ImmutableLinkingLine.of(ResponseSet.of(parseResponses(parts.subList(1, parts.size()),
          foreignIDToLocal, responsesByUID)), Optional.of(parts.get(0)));
    } else {
      throw new IOException("Line must have at least two fields");
    }
  }
}

interface LinkingFileWriter {
  void write(ResponseLinking linking, CharSink sink) throws IOException;
}

abstract class AbstractKBPSpecLinkingWriter implements LinkingFileWriter {
  @Override
  public void write(ResponseLinking responseLinking, CharSink sink) throws IOException {
    final List<String> lines = Lists.newArrayList();
    for (final ResponseSet responseSet : responseLinking.responseSets()) {
      lines.add(renderLine(responseSet, responseLinking));
    }

    // incompletes last
    lines.add("INCOMPLETE\t" + Joiner.on("\t").join(
        transform(responseLinking.incompleteResponses(), ResponseFunctions.uniqueIdentifier())));

    sink.writeLines(lines, "\n");
  }

  abstract String renderLine(ResponseSet responseSet, ResponseLinking responseLinking)
      throws IOException;
}

class LinkingWriter2015 extends AbstractKBPSpecLinkingWriter {

  @Override
  String renderLine(final ResponseSet responseSet, final ResponseLinking responseLinking) {
    return Joiner.on("\t").join(
        transform(responseSet.asSet(), ResponseFunctions.uniqueIdentifier()));
  }
}

class LinkingWriter2016 extends AbstractKBPSpecLinkingWriter {

  @Override
  String renderLine(final ResponseSet responseSet, final ResponseLinking responseLinking)
      throws IOException {
    return getEventFrameID(responseSet, responseLinking) + "\t" + StringUtils.spaceJoiner().join(
        transform(responseSet.asSet(), ResponseFunctions.uniqueIdentifier()));
  }

  // inefficient, but the number of frames in each document should be small
  private String getEventFrameID(final ResponseSet responseSet,
      final ResponseLinking responseLinking) throws IOException {
    checkArgument(responseLinking.responseSetIds().isPresent(), "Linking does not assign frame "
        + "IDs. These are required for writing in 2016 format.");
    final ImmutableSet<String> ids =
        responseLinking.responseSetIds().get().asMultimap().inverse().get(responseSet);
    if (ids.size() == 1) {
      return ids.asList().get(0);
    } else if (ids.isEmpty()) {
      throw new IOException("No ID found for event frame " + responseSet);
    } else {
      throw new IOException("Multiple IDs found for event frame, should be impossible: "
          + responseSet);
    }
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
