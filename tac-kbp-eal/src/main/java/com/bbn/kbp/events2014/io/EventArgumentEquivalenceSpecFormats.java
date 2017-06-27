package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.TypeRoleFillerRealisSet;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.bbn.kbp.events2014.TypeRoleFillerRealisFunctions.uniqueIdentifier;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;

public final class EventArgumentEquivalenceSpecFormats {

  private EventArgumentEquivalenceSpecFormats() {
    throw new UnsupportedOperationException();
  }

  public static EventArgumentEquivalenceStore openOrCreateEquivalenceStore(File directory) {
    directory.mkdirs();
    return new DirectoryEquivalenceStore(directory);
  }

  private static final class DirectoryEquivalenceStore implements EventArgumentEquivalenceStore {

    private final File directory;

    private DirectoryEquivalenceStore(File directory) {
      checkArgument(directory.isDirectory(),
          "Specified directory %s for equivalence store is not a directory", directory);
      this.directory = checkNotNull(directory);
    }

    @Override
    public ImmutableSet<Symbol> docIDs() throws IOException {
      return FluentIterable.from(Arrays.asList(directory.listFiles()))
          .transform(FileUtils.toNameFunction())
          .transform(Symbol.FromString)
          .toSet();
    }

    private static final Joiner TAB_JOINER = Joiner.on("\t");

    @Override
    public void write(EventArgumentLinking toWrite) throws IOException {
      final List<String> lines = Lists.newArrayList();

      for (final TypeRoleFillerRealisSet trfrSet : toWrite.eventFrames()) {
        lines.add(TAB_JOINER.join(
            transform(trfrSet.asSet(), uniqueIdentifier())));
      }

      lines.add("INCOMPLETE\t" + TAB_JOINER.join(
          transform(toWrite.incomplete(), uniqueIdentifier())));

      final File f = new File(directory, toWrite.docID().toString());
      Files.asCharSink(f, Charsets.UTF_8).writeLines(lines, "\n");
    }


  }
}
