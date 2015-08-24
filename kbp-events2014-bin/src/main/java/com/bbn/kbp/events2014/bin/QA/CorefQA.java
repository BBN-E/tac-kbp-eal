package com.bbn.kbp.events2014.bin.QA;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.bin.QA.Warnings.CASOverlapWarningRule;
import com.bbn.kbp.events2014.bin.QA.Warnings.CorefWarningRule;
import com.bbn.kbp.events2014.bin.QA.Warnings.Warning;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.transformers.MakeAllRealisActual;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.base.Functions.compose;

/**
 * Created by jdeyoung on 6/30/15.
 */
public class CorefQA {

  private static final Logger log = LoggerFactory.getLogger(CorefQA.class);

  public static void trueMain(String... args) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(args[0]));
    final AnnotationStore store =
        AssessmentSpecFormats.openAnnotationStore(params.getExistingDirectory("annotationStore"),
            AssessmentSpecFormats.Format.KBP2015);
    final File outputDir = params.getCreatableDirectory("outputDir");

    final ImmutableList<CorefWarningRule<Integer>> warnings =
        ImmutableList.<CorefWarningRule<Integer>>of(CASOverlapWarningRule.create());
    final CorefDocumentRenderer
        htmlRenderer = CorefDocumentRenderer.createWithDefaultOrdering(warnings);

    for (Symbol docID : store.docIDs()) {
      log.info("processing document {}", docID.asString());
      final AnswerKey answerKey = MakeAllRealisActual.neutralizeAssessedResponsesAndAssessment(
          store.read(docID));
      log.info("serializing {}", docID.asString());

      final ImmutableSet<RenderableCorefEAType> CASesWithWarnings = generateWarnings(answerKey,
          warnings);
      final ImmutableSet<RenderableCorefEAType> CASesWithoutWarnings =
          findCASesWithoutWarnings(CASesWithWarnings, answerKey);
      htmlRenderer.renderTo(
          Files
              .asCharSink(new File(outputDir, docID.asString() + ".coref.html"),
                  Charset.defaultCharset()),
          answerKey, CASesWithWarnings, CASesWithoutWarnings);
    }
  }

  private static ImmutableSet<RenderableCorefEAType> findCASesWithoutWarnings(
      final ImmutableSet<RenderableCorefEAType> caSesWithWarnings, final AnswerKey answerKey) {
    final ImmutableSet.Builder<RenderableCorefEAType> withoutWarning = ImmutableSet.builder();
    final ImmutableSet<AssessedResponse> responsesWithWarningss =
        FluentIterable.from(caSesWithWarnings)
            .transformAndConcat(RenderableCorefEAType.renderableCASGroupsAsFunction())
            .transformAndConcat(RenderableCASGroup.renderableResponsesAsFunction())
            .transformAndConcat(RenderableCorefResponse.sourceResponsesFunction()).toSet();
    final ImmutableSet<AssessedResponse> responsesWithoutWarnings =
        Sets.difference(answerKey.annotatedResponses(), responsesWithWarningss).immutableCopy();
    final ImmutableMultimap<Integer, AssessedResponse> CASesWithoutWarnings =
        Multimaps.index(responsesWithoutWarnings,
            compose(Functions.forMap(answerKey.corefAnnotation().CASesToIDs()),
                compose(Response.CASFunction(), AssessedResponse.Response)));
    final ImmutableMultimap<Integer, TypeRoleTupleWorkAround> CASesWithoutWarningsToTypes =
        ImmutableMultimap.copyOf(
            Multimaps.transformValues(CASesWithoutWarnings,
                TypeRoleTupleWorkAround.createFromAssessedResponse()));
    for (final Integer noWarn : CASesWithoutWarnings.keySet()) {
      for (final TypeRoleTupleWorkAround responseType : CASesWithoutWarningsToTypes.get(noWarn)) {
        withoutWarning.add(
            RenderableCorefEAType.create(responseType.type, responseType.role, ImmutableSet.of(
                RenderableCASGroup
                    .createFromResponsesWithoutWarnings(noWarn, ImmutableSet.copyOf(
                        CASesWithoutWarnings.get(noWarn))))));
      }
    }
    return withoutWarning.build();
  }

  private static ImmutableSet<RenderableCorefEAType> generateWarnings(final AnswerKey answerKey,
      final ImmutableList<CorefWarningRule<Integer>> warnings) {
    final ImmutableSet.Builder<RenderableCorefEAType> result = ImmutableSet.builder();

    final ImmutableMultimap<TypeRoleTupleWorkAround, Response> typeRoleToResponse =
        Multimaps.index(answerKey.allResponses(), TypeRoleTupleWorkAround.createfromResponse());

    for (final CorefWarningRule<Integer> w : warnings) {
      for (final TypeRoleTupleWorkAround key : typeRoleToResponse.keySet()) {
        final ImmutableSetMultimap<Integer, Warning> warningMap =
            ImmutableSetMultimap.copyOf(w.applyWarning(answerKey,
                ImmutableSet.copyOf(typeRoleToResponse.get(key))));
        final RenderableCorefEAType corefEAType =
            RenderableCorefEAType.createFromAnswerKeyAndWarnings(
                key.type, key.role, answerKey, warningMap);
        result.add(corefEAType);
      }
    }
    return result.build();
  }


  public static void main(String... args) {
    try {
      trueMain(args);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  // another work around for want of a table, or tuples.
  private static class TypeRoleTupleWorkAround {

    final Symbol type;
    final Symbol role;

    private TypeRoleTupleWorkAround(final Symbol type, final Symbol role) {
      this.type = type;
      this.role = role;
    }

    public static Function<AssessedResponse, TypeRoleTupleWorkAround> createFromAssessedResponse() {
      return Functions.compose(createfromResponse(), AssessedResponse.Response);
    }

    public static Function<Response, TypeRoleTupleWorkAround> createfromResponse() {
      return new Function<Response, TypeRoleTupleWorkAround>() {
        @Override
        public TypeRoleTupleWorkAround apply(final Response input) {
          return new TypeRoleTupleWorkAround(input.type(), input.role());
        }
      };
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final TypeRoleTupleWorkAround that = (TypeRoleTupleWorkAround) o;

      if (!type.equals(that.type)) {
        return false;
      }
      return role.equals(that.role);

    }

    @Override
    public int hashCode() {
      int result = type.hashCode();
      result = 31 * result + role.hashCode();
      return result;
    }
  }
}
