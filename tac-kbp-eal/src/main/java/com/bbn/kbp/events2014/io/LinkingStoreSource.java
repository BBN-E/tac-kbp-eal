package com.bbn.kbp.events2014.io;

import java.io.File;
import java.io.FileNotFoundException;

import static com.google.common.base.Preconditions.checkNotNull;

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

