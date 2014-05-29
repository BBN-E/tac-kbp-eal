package com.bbn.kbp.events2014.assessmentDiff.diffLoggers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.Response;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import static com.google.common.base.Preconditions.checkNotNull;

public final class FancierDiffLogger implements DiffLogger {
    private final PlainDocCache cache;
    
    public FancierDiffLogger(final PlainDocCache plainDocCache) {
        this.cache = checkNotNull(plainDocCache);
    }

    @Override
    public void logDifference(Response response, Symbol leftKey,
            Symbol rightKey, StringBuilder out) {
        //this.logBegin(out);
        this.logLeftRightDecisions(out, leftKey, rightKey);
        this.logTypeRoleCAS(response, out);
//        try {
//            String originalDocText = this.cache.getPlainDoc(response.docID());
//            this.logBaseFiller(originalDocText, response, out);
//        } catch (IOException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
        
        //this.logEnd(out);
        
        final String originalDocText;
        try {
            originalDocText = this.cache.getPlainDoc(response.docID());
            this.logBaseFiller(originalDocText, response, out);
            this.logPredicateJustifications(originalDocText, response, out);
            this.logArgumentJustications(originalDocText, response, out);
            this.logDocumentContext(originalDocText, response, out);
        } catch (final IOException ioe) {
            out.append("Error: failed to load document");
            out.append("<hr>");
            return;
            //return String.format("Cannot load text file for doc ID %s",  response.docID());
        }
        out.append("<hr>");
        

    }

    
    private void logLeftRightDecisions(StringBuilder out, Symbol leftKey, Symbol rightKey) {
        out.append(String.format("<h2>%s vs %s</h2>", leftKey, rightKey));
    }
    
    private void logTypeRoleCAS(Response response, StringBuilder out) {
        out.append(String.format("<h2>(Docid: %s, Type: %s, Role: %s, CAS: %s</h2>", response.docID(),
                response.type(), response.role(), response.canonicalArgument()));

//        out.append(String.format("<h3>Type: %s</h3>", response.type()));
//        out.append(String.format("<h3>Role: %s</h3>", response.role()));
//        out.append(String.format("<h3>CAS: %s</h3>", response.canonicalArgument()));
    }
    
    private void logBaseFiller(String originalDocText, Response response, StringBuilder out) {
        
        //System.out.println("The base filler is: " + 
        //originalDocText.substring(response.baseFiller().startInclusive(), response.baseFiller().endInclusive()+1));
        out.append("<h3>Base Filler:</h3>");
        out.append(originalDocText.substring(response.baseFiller().startInclusive(),
                response.baseFiller().endInclusive()+1));
        //out.append(this.unifiedJustifications(originalDocText, response));        
    }
    
    private List<CharOffsetSpan> justificationSpans(Response response) {
        final List<CharOffsetSpan> offsetSpans = Lists.newArrayList();
        offsetSpans.add(response.canonicalArgument().charOffsetSpan());
        offsetSpans.addAll(response.predicateJustifications());
        offsetSpans.addAll(response.additionalArgumentJustifications());
        offsetSpans.add(response.baseFiller());
        return offsetSpans;
    }
    
    private void logPredicateJustifications(final String originalDocText, final Response response, StringBuilder out) {
        out.append("<h3>Predicate Justifications:</h3>");
        final List<CharOffsetSpan> offsetSpans = Lists.newArrayList();
        offsetSpans.addAll(response.predicateJustifications());
        out.append("<div>");
        for (CharOffsetSpan predSpan : offsetSpans) {
             out.append(originalDocText.substring(predSpan.startInclusive(),
                     predSpan.endInclusive()+1));
             out.append("<br>");
        }
        out.append("</div>");
    }
    
    private void logArgumentJustications(final String originalDocText, final Response response, StringBuilder out) {
        out.append("<h3>Argument Justifications:</h3>");
        out.append("<div>");
        final List<CharOffsetSpan> offsetSpans = Lists.newArrayList();
        offsetSpans.addAll(response.additionalArgumentJustifications());
        if (offsetSpans.isEmpty()) {
           out.append("(N/A)"); 
        } else {
        for (CharOffsetSpan argSpan : offsetSpans) {
            out.append(originalDocText.substring(argSpan.startInclusive(),
                    argSpan.endInclusive()+1));
            out.append("<br>");
        }
        out.append("</div>");
        }
    }
    
    private void logDocumentContext(final String originalDocText, final Response response, StringBuilder out) {
        String context = this.unifiedJustifications(originalDocText, response);
        out.append("<h3>Context:</h3>");
        out.append("<div>");
        out.append(context);
        out.append("<br>");
        out.append("</div>");
                
    }
    
    private String unifiedJustifications(final String originalDocText, final Response response) {
        final List<CharOffsetSpan> offsetSpans = justificationSpans(response);
        Collections.sort(offsetSpans);
        int startInclusive = offsetSpans.get(0).startInclusive();
        int endInclusive = offsetSpans.get(offsetSpans.size()-1).endInclusive();
        startInclusive = (startInclusive - 100) >= 0 ? startInclusive - 100 : 0;
        endInclusive = (endInclusive + 100) < originalDocText.length()? endInclusive+100 : originalDocText.length()-1;
        String context = originalDocText.substring(startInclusive, endInclusive+1);
        if (context.length() > 1000) {
            context = context.substring(1000);
        }
                
        return "...." + context + " ....";
    }
    
    /*
    public String unifiedJustifications(final Response response) {
        final List<String> plainDoc;
        try {
            plainDoc = cache.getPlainDoc(response.docID());
        } catch (final IOException ioe) {
            return String.format("Cannot load text file for doc ID %s",
                    response.docID());
        }
        
        response.predicateJustifications().
        
        

        final List<OffsetSpan> offsetSpans = justificationSpans(response);

        final Set<SentenceTheory> sentencesToRender = Sets.newHashSet();
        for (final OffsetSpan span : offsetSpans) {
            sentencesToRender.addAll(OffsetRange.sentenceTheoriesContaining(dt, span.range()));
        }

        final List<SentenceTheory> adjacentSentences = Lists.newArrayList();
        for (final SentenceTheory st : sentencesToRender) {
            if (st.sentenceNumber() > 0) {
                adjacentSentences.add(dt.sentenceTheory(st.sentenceNumber()-1));
            }
            if (st.sentenceNumber() +1 != dt.numSentences()) {
                adjacentSentences.add(dt.sentenceTheory(st.sentenceNumber()+1));
            }
        }
        sentencesToRender.addAll(adjacentSentences);
        final List<SentenceTheory> toRenderList= Lists.newArrayList(sentencesToRender);

        Collections.sort(toRenderList, SentenceTheory.BySentenceNumber);

        final StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"kbp-unified-justs-snippet\">");
        final XMLStyleAnnotationFormatter formatter = XMLStyleAnnotationFormatter.create();
        int lastIdx = -1;
        for (final SentenceTheory st : toRenderList) {
            if (!st.span().empty()) {
                if (lastIdx +1 != st.sentenceNumber()) {
                    sb.append("... ");
                }
                sb.append("<span class=\"sentence\">");
                sb.append(formatter.format(dt, st.span().offsetRange(), offsetSpans));
                sb.append("</span>");
            }
            lastIdx = st.sentenceNumber();
        }
        sb.append("</div>");

        return sb.toString();
    }
    */
    
    public static class Builder {
        private PlainDocCache plainDocCache;
        
        public FancierDiffLogger build() {
            return new FancierDiffLogger(plainDocCache);
        }
    }
    
    

}
