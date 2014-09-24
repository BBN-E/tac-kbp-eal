package com.bbn.kbp.events2014.assessmentDiff.diffLoggers;

import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class PlainDocCache {
    private final Map<Symbol, File> docIDToFileMap;
    
    private PlainDocCache(final Map<Symbol, File> fileMap) {
        docIDToFileMap = ImmutableMap.copyOf(fileMap);
    }

    public static PlainDocCache createFromDocIDToFileMap(final Map<Symbol, File> fileMap) {
        return new PlainDocCache(fileMap);
    }

    // despite the name, this does not appear to actually do any caching? I've cleaned it up
    // a little, but I'm not sure this is actually even used, so I won't bother with it
    // more for now...
    public String getPlainDoc(final Symbol docid) throws IOException {
        return Files.toString(this.docIDToFileMap.get(docid), Charsets.UTF_8);
    }
}
