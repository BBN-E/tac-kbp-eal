package com.bbn.kbp;

import com.google.common.io.CharSource;

import java.io.IOException;

public interface KnowledgeBaseLoader {

  KnowledgeBase load(final CharSource input) throws IOException;

}
