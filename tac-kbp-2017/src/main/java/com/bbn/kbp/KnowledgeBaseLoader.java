package com.bbn.kbp;

import com.google.common.io.CharSource;

import java.io.IOException;

/**
 * A class to load a knowledge-base from a file. The file must have one assertion per line, and each
 * column of that line must be separated by a tab character. The first line of the file must give
 * the run ID. Blank lines (lines that contain only white spaces or nothing) are ignored. Double
 * quotes and backslashes must be escaped with a preceding backslash. Comments begin with "#" and
 * continue to the end of the line (we are assuming that a "#" within a quoted string is not
 * considered the start of a comment). All tabs and newline characters within a quoted string
 * should be converted to a single space character and this should be the only way they differ from
 * the string extracted from the provenance span (See "Column 5" on page 13 of TAC KBP 2016
 * ColdStart task description).
 */
public interface KnowledgeBaseLoader {

  KnowledgeBase load(final CharSource input) throws IOException;

}
