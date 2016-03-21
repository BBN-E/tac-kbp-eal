package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.ArgumentOutput;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;

/**
 * Represents something (e.g. a file, a database) which can store system responses for the KBP event
 * argument task. Stores should be closed when you are done with them to prevent data loss.
 *
 * @author rgabbard
 */
public interface ArgumentStore {

  /**
   * The document IDs with stored responses.
   */
  public ImmutableSet<Symbol> docIDs() throws IOException;

  /**
   * Gets the system output for the specified document or throws an exception if it is not
   * available.
   */
  public ArgumentOutput read(Symbol docId) throws IOException;

  /**
   * Writes the provided system output to this store.
   */
  public void write(ArgumentOutput argumentOutput) throws IOException;

  public void close() throws IOException;

  /**
   * Like {@code read}, but it returns an empty system output if no data is available for a
   * document.
   */
  public ArgumentOutput readOrEmpty(Symbol docid) throws IOException;
}
