package com.bbn.kbp;

import com.google.common.io.CharSink;

import java.io.IOException;

public interface KnowledgeBaseWriter {

  void write(final KnowledgeBase kb, final CharSink sink) throws IOException;

}
