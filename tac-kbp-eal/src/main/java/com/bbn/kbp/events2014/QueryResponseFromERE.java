package com.bbn.kbp.events2014;


import com.bbn.bue.common.collections.LaxImmutableMapBuilder;
import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events2014.io.DefaultCorpusQueryLoader;
import com.bbn.kbp.events2014.io.SingleFileQueryStoreWriter;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;
import com.bbn.nlp.corpora.ere.ERELoader;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

/**
 * Extracts QueryResponses from LDC queries that are specified by: a QueryID, a document ID, an
 * event hopper, a role, and an entity within that hopper.
 */
public final class QueryResponseFromERE {

  private static final Logger log = LoggerFactory.getLogger(QueryResponseFromERE.class);

  private QueryResponseFromERE() {
    throw new UnsupportedOperationException();
  }

  public static void main(String[] argv) {
    // we wrap the main method in this way to
    // ensure a non-zero return value on failure
    try {
      trueMain(argv);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void trueMain(String[] argv) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final File queryFile = params.getExistingFile("com.bbn.tac.eal.queryFile");
    final ImmutableMap<Symbol, File> ereMap =
        FileUtils.loadSymbolToFileMap(params.getExistingFile("com.bbn.tac.eal.eremap"));
    final CorpusQuerySet2016 queries = DefaultCorpusQueryLoader.create().loadQueries(
        Files.asCharSource(queryFile, Charsets.UTF_8));
    final int slack = params.getNonNegativeInteger("com.bbn.tac.eal.slack");

    final ImmutableMap<String, SystemOutputStore2016> outputStores =
        loadStores(params.getExistingDirectory("com.bbn.tac.eal.storeDir"),
            params.getStringList("com.bbn.tac.eal.storesToProcess"));
    final File outputFile = params.getCreatableFile("com.bbn.tac.eal.outputFile");

    final CorpusQueryAssessments.Builder corpusQueryAssessmentsB = CorpusQueryAssessments.builder();
    final SingleFileQueryStoreWriter queryStoreWriter =
        SingleFileQueryStoreWriter.builder().build();

    final CorpusQueryExecutor2016 queryExecutor =
        EREBasedCorpusQueryExecutor.createDefaultFor2016(ereMap, ERELoader.builder().build(),
            EREToKBPEventOntologyMapper.create2016Mapping(), slack);

    final LaxImmutableMapBuilder<QueryResponse2016, QueryAssessment2016> assessmentsB =
        MapUtils.immutableMapBuilderAllowingSameEntryTwice();

    for (final Map.Entry<String, SystemOutputStore2016> store : outputStores.entrySet()) {
      for (final CorpusQuery2016 query : queries.queries()) {
        final ImmutableSet<DocEventFrameReference> docEventFrameReferences =
            queryExecutor.queryEventFrames(store.getValue(), query);
        final ImmutableMultimap<Symbol, DocEventFrameReference> refsByDocID =
            FluentIterable.from(docEventFrameReferences)
                .index(DocEventFrameReferenceFunctions.docID());
        for (final Symbol docID : refsByDocID.keys()) {
          final Optional<ImmutableBiMap<String, ResponseSet>> responseSetMap =
              store.getValue().read(docID).linking().responseSetIds();
          checkState(responseSetMap.isPresent());
          final ImmutableSet.Builder<CharOffsetSpan> offsetsB = ImmutableSet.builder();
          for (final DocEventFrameReference docEventFrameReference : refsByDocID.get(docID)) {
            final ImmutableSet<Response> responses =
                responseSetMap.get().get(docEventFrameReference.eventFrameID()).asSet();
            final ImmutableSet<CharOffsetSpan> spans = FluentIterable.from(responses)
                .transformAndConcat(ResponseFunctions.predicateJustifications()).toSet();
            offsetsB.addAll(spans);
          }
          final QueryResponse2016 queryResponse2016 =
              QueryResponse2016.builder().docID(docID).queryID(query.id())
                  .addAllPredicateJustifications(offsetsB.build()).build();
          corpusQueryAssessmentsB.putAllQueryResponsesToSystemIDs(queryResponse2016,
              ImmutableList.of(Symbol.from(store.getKey())));
          corpusQueryAssessmentsB.addQueryReponses(queryResponse2016);
          assessmentsB.put(queryResponse2016, QueryAssessment2016.UNASSASSED);
        }
      }
    }
    corpusQueryAssessmentsB.assessments(assessmentsB.build());

    queryStoreWriter
        .saveTo(corpusQueryAssessmentsB.build(), Files.asCharSink(outputFile, Charsets.UTF_8));
  }

  private static ImmutableMap<String, SystemOutputStore2016> loadStores(
      final File storeDir, final List<String> storeList) throws IOException {
    final ImmutableMap.Builder<String, SystemOutputStore2016> ret = ImmutableMap.builder();
    for (final String storeName : storeList) {
      ret.put(storeName, SystemOutputStore2016.open(new File(storeDir, storeName)));
    }
    return ret.build();
  }
}
