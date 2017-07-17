package com.bbn.kbp;

import com.google.common.io.CharSink;

import java.io.IOException;
import java.util.Random;

public interface KnowledgeBaseWriter {

  void write(final KnowledgeBase kb, final Random random, final CharSink sink) throws IOException;

}
