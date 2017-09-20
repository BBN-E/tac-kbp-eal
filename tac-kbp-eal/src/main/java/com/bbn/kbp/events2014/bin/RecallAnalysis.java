package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.OptionalUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.AssessedResponseFunctions;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessmentFunctions;
import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.SystemOutputLayout;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Created by jdeyoung on 10/20/15.
 */
public final class RecallAnalysis {

  private static final Logger log = LoggerFactory.getLogger(RecallAnalysis.class);

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
    final AnnotationStore annotationStore = AssessmentSpecFormats.openAnnotationStore(
        params.getExistingDirectory("annotationStore"),
        AssessmentSpecFormats.Format.KBP2015);
    final ImmutableList<File> systemOutputStoresLocations = ImmutableList.copyOf(
        Iterables.transform(
            params.getStringList("systemOutputStoresList"), new Function<String, File>() {
              @Override
              public File apply(final String input) {
                return new File(input);
              }
            }));
    final SystemOutputLayout outputFormat =
        SystemOutputLayout.ParamParser.fromParamVal(params.getString("systemOutputFormat"));
    final ImmutableMap.Builder<String, SystemOutputStore> outputStoreByNameB =
        ImmutableMap.builder();
    for (final File f : systemOutputStoresLocations) {
      outputStoreByNameB.put(f.getName(), outputFormat.open(f));
    }
    final ImmutableMap<String, SystemOutputStore> outputStoreByName = outputStoreByNameB.build();

    // same order as specified
    final ImmutableList<String> systemOrdering =
        FluentIterable.from(systemOutputStoresLocations).transform(
            FileUtils.ToName).toList();

    final File outputFile = params.getCreatableFile("outputCSV");
    final PrintWriter output = new PrintWriter(outputFile, Charsets.UTF_8.name());
    output.println(Row.header(systemOrdering));

    for (final Symbol docID : annotationStore.docIDs()) {
      final ImmutableList<Row> rows =
          extractRowFromOutputs(docID, annotationStore, outputStoreByName);
      for (final Row row : rows) {
        output.println(row.toRow(systemOrdering));
      }
    }
    output.close();

  }

  private static ImmutableList<Row> extractRowFromOutputs(final Symbol docID,
      final AnnotationStore annotationStore,
      final ImmutableMap<String, SystemOutputStore> systemOutputStores) throws IOException {
    final AnswerKey answerKey = annotationStore.read(docID).filter(new AnswerKey.Filter() {
      @Override
      public Predicate<AssessedResponse> assessedFilter() {
        return AssessedResponse.IsCorrectUpToInexactJustifications;
      }

      @Override
      public Predicate<Response> unassessedFilter() {
        return Predicates.alwaysFalse();
      }
    });
    final CorefAnnotation coref = answerKey.corefAnnotation();
    final ImmutableMultimap<Integer, AssessedResponse> CASGroupToResponses = Multimaps.index(
        answerKey.annotatedResponses(), new Function<AssessedResponse, Integer>() {
          @Override
          public Integer apply(final AssessedResponse input) {
            return coref.CASesToIDs().get(input.response().canonicalArgument());
          }
        });

    final ImmutableList.Builder<Row> rows = ImmutableList.builder();
    // first by CASGroup
    for (final int CASGroup : CASGroupToResponses.keySet()) {
      final String CASGroupAsString = String.format("CASGroup-%d", CASGroup);
      final ImmutableMultimap<Symbol, AssessedResponse> byEventType =
          Multimaps.index(CASGroupToResponses.get(CASGroup),
              Functions.compose(ResponseFunctions.type(), AssessedResponseFunctions.response()));
      // then by event type
      for (final Symbol type : byEventType.keySet()) {
        final ImmutableMultimap<Symbol, AssessedResponse> byEventRole = Multimaps
            .index(byEventType.get(type),
                Functions.compose(ResponseFunctions.role(), AssessedResponseFunctions.response()));
        // then by event role
        for (final Symbol role : byEventRole.keySet()) {
          final ImmutableMultimap<KBPRealis, AssessedResponse> byAssessedRealis = Multimaps
              .index(byEventRole.get(role),
                  Functions.compose(OptionalUtils.<KBPRealis>getFunction(), Functions
                      .compose(ResponseAssessmentFunctions.realis(),
                          AssessedResponseFunctions.assessment())));
          // then by assessed realis
          for (final KBPRealis assessedRealis : byAssessedRealis.keySet()) {
            final ImmutableMultimap<String, Response> systemToResponse =
                systemOutputToResponses(docID, systemOutputStores,
                    byAssessedRealis.get(assessedRealis), coref);
            final ImmutableMultimap<String, KBPRealis> systemToRealis = ImmutableMultimap.copyOf(
                Multimaps.transformValues(systemToResponse, ResponseFunctions.realis()));
            final ImmutableSet<String> cases =
                FluentIterable.from(systemToResponse.values())
                    .transform(ResponseFunctions.canonicalArgument())
                    .transform(
                        new Function<KBPString, String>() {
                          @Override
                          public String apply(final KBPString input) {
                            return input.string();
                          }
                        }).toSet();
            rows.add(new Row(docID.asString(), CASGroupAsString, systemToRealis, type, role,
                assessedRealis, cases));
            if (cases.size() == 0) {
              final ImmutableSet<String> responseIDs =
                  FluentIterable.from(byAssessedRealis.get(assessedRealis)).transform(
                      AssessedResponseFunctions.response())
                      .transform(ResponseFunctions.uniqueIdentifier()).toSet();
              log.info(
                  "warning - for {} {}/{} there were no system responses, annotation store has: {}",
                  docID, CASGroupAsString, assessedRealis, responseIDs);
            }
          }
        }
      }
    }

    return rows.build();
  }

  private static ImmutableMultimap<String, Response> systemOutputToResponses(
      final Symbol docID, final ImmutableMap<String, SystemOutputStore> systemOutputStores,
      final ImmutableCollection<AssessedResponse> assessedResponses,
      final CorefAnnotation corefAnnotation) throws IOException {
    final ImmutableMultimap.Builder<String, Response> ret = ImmutableMultimap.builder();

    final ImmutableMultimap<TRC, Response> typeRoleCASAssessed = Multimaps
        .index(Iterables.transform(assessedResponses, AssessedResponseFunctions.response()),
            TRC.fromResponse(corefAnnotation));

    for (final String systemName : systemOutputStores.keySet()) {

      final ImmutableMultimap<TRC, Response> systemTRCs = Multimaps
          .index(systemOutputStores.get(systemName).read(docID).arguments().responses(),
              TRC.fromResponseSafely(corefAnnotation));
      for (final TRC overlap : Sets
          .intersection(typeRoleCASAssessed.keySet(), systemTRCs.keySet())) {
        ret.putAll(systemName, systemTRCs.get(overlap));
      }
    }
//    final ImmutableSet<String> assessedResponsesIDs =
//        FluentIterable.from(assessedResponses).transform(
//            AssessedResponse.Response).transform(
//            Response.uniqueIdFunction()).toSet();
//    for (final String systemOutputStoreName : systemOutputStores.keySet()) {
//      final ImmutableSet<Response> responses = ImmutableSet.copyOf(
//          systemOutputStores.get(systemOutputStoreName).read(docID).arguments().responses());
//      final Set<String> overlapsWithAnnotated =
//          Sets.intersection(FluentIterable.from(responses).transform(
//              Response.uniqueIdFunction()).toSet(), assessedResponsesIDs);
//      for (final Response r : responses) {
//        if (overlapsWithAnnotated.contains(r.uniqueIdentifier())) {
//          ret.put(systemOutputStoreName, r);
//        }
//      }
//    }
    return ret.build();
  }

  private static class TRC {

    final Symbol eventType;
    final Symbol role;
    final int CASGroup;

    private TRC(final Symbol eventType, final Symbol role, final int casGroup) {
      this.eventType = eventType;
      this.role = role;
      CASGroup = casGroup;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final TRC trc = (TRC) o;

      if (CASGroup != trc.CASGroup) {
        return false;
      }
      if (!eventType.equals(trc.eventType)) {
        return false;
      }
      return role.equals(trc.role);

    }

    @Override
    public int hashCode() {
      int result = eventType.hashCode();
      result = 31 * result + role.hashCode();
      result = 31 * result + CASGroup;
      return result;
    }

    public static Function<Response, TRC> fromResponse(final CorefAnnotation corefAnnotation) {
      return new Function<Response, TRC>() {
        @Override
        public TRC apply(final Response input) {
          final int cas = corefAnnotation.CASesToIDs().get(
              input.canonicalArgument());
          return new TRC(input.type(), input.role(), cas);
        }
      };
    }

    public static Function<Response, TRC> fromResponseSafely(
        final CorefAnnotation corefAnnotation) {
      return new Function<Response, TRC>() {
        @Override
        public TRC apply(final Response input) {
          Optional<Integer> CAS = Optional.fromNullable(corefAnnotation.CASesToIDs().get(
              input.canonicalArgument()));
          final int cas;
          if (CAS.isPresent()) {
            cas = CAS.get();
          } else {
            log.info("missing CASgroup for {}", input);
            cas = -1;
          }
          return new TRC(input.type(), input.role(), cas);
        }
      };
    }
  }

  private static class Row {

    final String document;
    final String casgroup;
    final ImmutableMultimap<String, KBPRealis> systemToRealis;
    final Symbol type;
    final Symbol role;
    final KBPRealis actualRealis;
    final ImmutableSet<String> CASes;

    public Row(final String document, final String casgroup,
        final ImmutableMultimap<String, KBPRealis> systemToRealis, final Symbol type,
        final Symbol role, final KBPRealis actualRealis, final ImmutableSet<String> caSes) {
      this.document = document;
      this.casgroup = casgroup;
      this.systemToRealis = systemToRealis;
      this.type = type;
      this.role = role;
      this.actualRealis = actualRealis;
      CASes = caSes;
    }

    public String toRow(final ImmutableList<String> systemOrdering) {
      checkArgument(ImmutableSet.copyOf(systemOrdering).containsAll(systemToRealis.keySet()),
          "warning, we have an extra system");
      final Joiner comma = Joiner.on(",");
      final Joiner tab = Joiner.on("\t");
      final ImmutableList.Builder<String> parts = ImmutableList.builder();
      parts.add(document);
      parts.add(casgroup);
      for (final String system : systemOrdering) {
        final Collection<KBPRealis> realises = systemToRealis.get(system);
        if (realises.size() == 0) {
          parts.add("N");
        } else {
          parts.add(comma.join(ImmutableSet.copyOf(realises)));
        }
      }
      parts.add(type + "/" + role);
      parts.add(actualRealis.name());
      parts.addAll(CASes);
      return tab.join(parts.build());
    }

    public static String header(final ImmutableList<String> systemOrdering) {
      final Joiner tab = Joiner.on("\t");
      final ImmutableList.Builder<String> h = ImmutableList.builder();
      h.add("DOCID");
      h.add("CASGroup");
      h.addAll(systemOrdering);
      h.add("type/role");
      h.add("Annotated Realis");
      h.add("many CASes");
      return tab.join(h.build());
    }
  }
}
