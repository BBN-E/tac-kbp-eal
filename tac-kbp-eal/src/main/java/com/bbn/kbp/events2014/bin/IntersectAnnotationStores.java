package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.ArgumentStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.LinkingStoreSource;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;

/**
 * Created by jdeyoung on 9/24/15.
 */
public final class IntersectAnnotationStores {


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
    final String format = params.getString("format");
    final AnnotationStore base = AssessmentSpecFormats.openAnnotationStore(
        params.getExistingDirectory("baseAnnotationStore"),
        AssessmentSpecFormats.Format.valueOf(format));
    final ArgumentStore baseResponsesMustBeIn = AssessmentSpecFormats
        .openSystemOutputStore(params.getExistingDirectory("annotationStoreForFilter"),
            AssessmentSpecFormats.Format.valueOf(format));
    final LinkingStore sourceStore = LinkingStoreSource.createFor2015()
        .openOrCreateLinkingStore(params.getCreatableDirectory("baseLinkingStore"));
    final LinkingStore filteredStore = LinkingStoreSource.createFor2015()
        .openOrCreateLinkingStore(params.getCreatableDirectory("resultLinkingStore"));
    final AnnotationStore result = AssessmentSpecFormats
        .openAnnotationStore(params.getCreatableDirectory("resultArgumentsDirectory"),
            AssessmentSpecFormats.Format.valueOf(format));
    for (final Symbol docID : base.docIDs()) {
      final ImmutableSet<String> responseIDsToKeep =
          FluentIterable.from(baseResponsesMustBeIn.read(docID).responses()).transform(
              ResponseFunctions.uniqueIdentifier()).toSet();
      final AnswerKey originalAnswerKey = base.readOrEmpty(docID);
      final AnswerKey filteredAnswerKey = originalAnswerKey.filter(new AnswerKey.Filter() {
        @Override
        public Predicate<AssessedResponse> assessedFilter() {
          return new Predicate<AssessedResponse>() {
            @Override
            public boolean apply(final AssessedResponse input) {
              return responseIDsToKeep.contains(input.response().uniqueIdentifier());
            }
          };
        }

        @Override
        public Predicate<Response> unassessedFilter() {
          return new Predicate<Response>() {
            @Override
            public boolean apply(final Response input) {
              return responseIDsToKeep.contains(input.uniqueIdentifier());
            }
          };
        }
      });
      result.write(filteredAnswerKey);
      final Optional<ResponseLinking> originalLinking = sourceStore.read(originalAnswerKey);
      if (originalLinking.isPresent()) {
        final ResponseLinking newLinking =
            originalLinking.get().copyWithFilteredResponses(Predicates
                .compose(Predicates.in(responseIDsToKeep), ResponseFunctions.uniqueIdentifier()));
        filteredStore.write(newLinking);
      }
    }
    result.close();
  }
}
