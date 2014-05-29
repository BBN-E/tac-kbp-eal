package com.bbn.kbp.events2014.assessmentDiff.diffLoggers;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

public class PlainDocCache {
    
    //private final LoadingCache<Symbol, String> innerCache;
    private final Map<Symbol, File> tempMapHack; // get rid of this eventually
//    
//    private PlainDocCache(final LoadingCache<Symbol, String> innerCache) {
//        this.innerCache = checkNotNull(innerCache);
//    }
    
    public PlainDocCache(final Map<Symbol, File> fileMap) {
        this.tempMapHack = fileMap;
    }
    public String getPlainDoc(final Symbol docid) throws IOException {
//        try {
//            return innerCache.get(docid);
//        }
//        catch (final ExecutionException e) {
//            if (e.getCause() instanceof IOException) {
//                throw (IOException)e.getCause();
//            } else {
//                throw new RuntimeException(e);
//            }
//        }
        try {
            String plainDoc = Files.toString(this.tempMapHack.get(docid), Charsets.UTF_8);
            // /nfs/mercury-04/u10/kbp/pilot/all.list
            return plainDoc;
        }
        catch (IOException e) {
            throw new IOException(e);
        }
    }

    public static Builder createFromDocIdMap(final Map<Symbol, File> docidToTextFile) {
        return Builder.fromDocIdMap(docidToTextFile);
    }

    public static class Builder {
        private Builder() {}

//        public String build() throws IOException {
//            return new PlainDocCache(
//                    CacheBuilder.newBuilder()
//                    .maximumSize(maxElements>=0?maxElements:10)
//                    .<Symbol,String>build(DocIDMapCacheLoader.from(
//                            docidMap,
//                            loader != null ? loader : SerifXMLLoader.fromStandardACETypes())));
//        }

        private static Builder fromDocIdMap(final Map<Symbol, File> docidToPlainText) {
            final Builder ret = new Builder();
            ret.docidMap = ImmutableMap.copyOf(docidToPlainText);
            return ret;
        }
    
        private Map<Symbol, File> docidMap = null;
        //private int maxElements = 20;
    }
}
