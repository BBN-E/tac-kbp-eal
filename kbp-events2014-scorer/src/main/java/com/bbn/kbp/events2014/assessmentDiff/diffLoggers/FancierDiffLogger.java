package com.bbn.kbp.events2014.assessmentDiff.diffLoggers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.Response;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import org.apache.commons.lang.StringEscapeUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public final class FancierDiffLogger implements DiffLogger {
    private final PlainDocCache cache;
    
    public FancierDiffLogger(final PlainDocCache plainDocCache) {
        this.cache = checkNotNull(plainDocCache);
    }

    @Override
    public void logDifference(Response response, Symbol leftKey, Symbol rightKey, StringBuilder out) {
        //this.logBegin(out);
        this.logLeftRightDecisions(out, leftKey, rightKey);
        this.logIdTypeRoleCAS(response, out);
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
            this.logRealis(response, out);
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
    
    private void logIdTypeRoleCAS(Response response, StringBuilder out) {
        out.append(String.format("<h2>Response Id: %s (old-style: %s)</h2>", response.uniqueIdentifier(),
                response.old2014ResponseID()));
        out.append(String.format("<h2>(Docid: %s, Type: %s, Role: %s, CAS: %s)</h2>", response.docID(),
                response.type(), response.role(), response.canonicalArgument()));
    }
    
    private void logRealis(Response response, StringBuilder out) {
        out.append("<h3>Realis:</h3>");
        out.append(response.realis());
        out.append("<br>");
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
            
             out.append(StringEscapeUtils.escapeHtml(originalDocText.substring(predSpan.startInclusive(),
                     predSpan.endInclusive()+1)));
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
                out.append(StringEscapeUtils.escapeHtml(originalDocText.substring(argSpan.startInclusive(),
                        argSpan.endInclusive()+1)));
                out.append("<br>");
            }
        }
        out.append("</div>");
    }
    
    private void logDocumentContext(final String originalDocText, final Response response, StringBuilder out) {
        out.append("<h3>Context:</h3>");
        out.append("<div>");
        out.append(StringEscapeUtils.escapeHtml(this.context(originalDocText, response)));
        out.append("</div>");
        out.append("<br>");        
    }
    
    private String context(final String originalDocText, final Response response) {
        // [1,3], [2,5], [8,10] => [1,5], [8,10]
        final List<CharOffsetSpan> charSpans = justificationSpans(response);
        final List<CharOffsetSpan> unitedSpans = Lists.newArrayList();
        
        // use RangeSet to do this
        final RangeSet<Integer> disconnected = TreeRangeSet.create();
        for (CharOffsetSpan charSpan : charSpans) { 
            int startInclusive = charSpan.startInclusive();
            int endInclusive = charSpan.endInclusive();
            startInclusive = (startInclusive - 100) >= 0 ? startInclusive - 100 : 0;
            endInclusive = (endInclusive + 100) < originalDocText.length()? endInclusive+100 : endInclusive;
            disconnected.add(Range.closed(startInclusive, endInclusive));
        }
        for (Range<Integer> range : disconnected.asRanges()) {
            unitedSpans.add(CharOffsetSpan.fromOffsetsOnly(range.lowerEndpoint(), range.upperEndpoint()));
        }
        Collections.sort(unitedSpans);
        String justificationsString = "";
        if (unitedSpans.get(0).startInclusive() != 0) {
            justificationsString += "[.....]";
        }
        for (CharOffsetSpan span : unitedSpans) {
            justificationsString += originalDocText.substring(span.startInclusive(), span.endInclusive()+1);
            justificationsString += "[.....]";
        }
        return justificationsString;
    }
    
    public static class Builder {
        public FancierDiffLogger build() {
            return new FancierDiffLogger(plainDocCache);
        }
    }
    
    

}
