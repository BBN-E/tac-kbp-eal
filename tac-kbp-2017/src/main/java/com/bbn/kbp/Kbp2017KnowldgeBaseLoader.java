package com.bbn.kbp;

import com.google.common.io.CharSink;
import com.google.common.io.CharSource;

/**
 * - one assertion per line
 * - tab-separated format
 * - first line must give run ID
 * - comments begin #
 * - blank lines are ignored
 * - escape double quotes and backslash with preceding backslash
 * - comments begin with # continue to end of line (assumption: # in a quoted string is not a comment)
 * - page 13, column 5, assuming that in all strings tabs and newline characters should be
 *   converted to a single space
 */
interface Kbp2017KnowledgeBaseLoader {
  Kbp2017KnowledgeBase load(CharSource input);
  void write(Kbp2017KnowledgeBase knowledgeBase, CharSink output);
}
