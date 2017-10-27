package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Created by jdeyoung on 10/30/15.
 */
public final class RestrictAnnotationStoreToDocumentsAndTypes {

  private static final Logger log = LoggerFactory
      .getLogger(RestrictAnnotationStoreToDocumentsAndTypes.class);

  public static void main(String... args) {
    try {
      trueMain(args);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void trueMain(final String[] args) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(args[0]));
    final AnnotationStore sourceStore = AssessmentSpecFormats
        .openAnnotationStore(params.getExistingDirectory("sourceStore"),
            params.getEnum("format", AssessmentSpecFormats.Format.class));
    final AnnotationStore targetStore = AssessmentSpecFormats
        .openAnnotationStore(params.getEmptyDirectory("targetStore"),
            params.getEnum("format", AssessmentSpecFormats.Format.class));
    final ImmutableSet<Symbol> docids =
        ImmutableSet.copyOf(FileUtils.loadSymbolList(params.getExistingFile("docids")));
    final ImmutableSet<Symbol> eventTypes =
        ImmutableSet.copyOf(FileUtils.loadSymbolList(params.getExistingFile("eventTypes")));
    final AnswerKey.Filter filterByEventTypes = new AnswerKey.Filter() {
      @Override
      public Predicate<AssessedResponse> assessedFilter() {
        return new Predicate<AssessedResponse>() {
          @Override
          public boolean apply(final AssessedResponse input) {
            throw new RuntimeException("should not be trying to look at any AssessedResponses");
          }
        };
      }

      @Override
      public Predicate<Response> unassessedFilter() {
        return new Predicate<Response>() {
          @Override
          public boolean apply(final Response input) {
            return eventTypes.contains(input.type());
          }
        };
      }
    };

    int docsWritten = 0;
    int responsesWritten = 0;
    int responsesDiscarded = 0;
    int docsDiscarded = 0;
    for(final Symbol docid: docids) {
      final AnswerKey source = sourceStore.read(docid);
      // skip documents with any assessment
      if(source.annotatedResponses().size() > 0) {
        docsDiscarded++;
        continue;
      }
      final AnswerKey filtered = source.filter(filterByEventTypes);
      responsesDiscarded += source.allResponses().size() - filtered.allResponses().size();
      if(filtered.allResponses().size() > 0) {
        responsesWritten += filtered.allResponses().size();
        docsWritten++;
        targetStore.write(filtered);
      } else {
        docsDiscarded++;
      }
    }
    log.info("{} docs to start, {} written, {} discarded", sourceStore.docIDs().size(), docsWritten, docsDiscarded);
    log.info("{} responses to assess, {} responses discarded", responsesWritten, responsesDiscarded);
  }

}
