package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;

import java.io.IOException;
import java.util.Set;

/**
 * An assessment store contains annotator assessments of pooled answers for the KBP 2014 Event
 * Argument Extraction task. Should be closed when you are done with it to prevent data loss.
 */
public interface AnnotationStore {

  /**
   * The IDs of documents whose responses are contained in this assessment store.
   */
  public Set<Symbol> docIDs() throws IOException;

  /**
   * Gets the answer key for the provided document. Throws an exception if the specified doc ID has
   * no entry in this assessment store.
   */
  public AnswerKey read(Symbol docId) throws IOException;

  /**
   * Writes the answer key provided to this assessment store.
   */
  public void write(AnswerKey answerKey) throws IOException;

  /**
   * Closes the assessment store.
   */
  public void close();

  /**
   * Like {@code read}, but it will return an empty answer key if the provided document ID is not
   * present in this store.
   */
  public AnswerKey readOrEmpty(Symbol docid) throws IOException;
}
