package com.bbn.kbp.events2014;


import com.bbn.bue.common.collections.ShufflingIterable;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events2014.io.DefaultCorpusQueryLoader;
import com.bbn.kbp.events2014.io.SingleFileQueryStoreWriter;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;
import com.bbn.nlp.corpora.ere.ERELoader;

import com.google.common.base.Charsets;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.limit;

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

  private static final String MULTIPLE_STORES_PARAM =  "com.bbn.tac.eal.storesToProcess";
  private static final String SINGLE_STORE_PARAM =  "com.bbn.tac.eal.storeToProcess";

  private static void trueMain(String[] argv) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    log.info(params.dump());
    final File queryFile = params.getExistingFile("com.bbn.tac.eal.queryFile");
    final ImmutableMap<Symbol, File> ereMap =
        FileUtils.loadSymbolToFileMap(params.getExistingFile("com.bbn.tac.eal.eremap"));
    final CorpusQuerySet2016 queries = DefaultCorpusQueryLoader.create().loadQueries(
        Files.asCharSource(queryFile, Charsets.UTF_8));

    final int maxResponsesPerQueryPerSystem = params.getOptionalPositiveInteger(
        "com.bbn.tac.eal.maxResponsesPerQueryPerSystem").or(Integer.MAX_VALUE);
    final Random rng = new Random(params.getOptionalPositiveInteger(
        "com.bbn.tac.eal.randSeed").or(0));

    params.assertExactlyOneDefined(SINGLE_STORE_PARAM, MULTIPLE_STORES_PARAM);
    final ImmutableMap<String, SystemOutputStore2016> outputStores;
    if (params.isPresent(SINGLE_STORE_PARAM)) {
      outputStores = loadSingleStore(params.getExistingDirectory(SINGLE_STORE_PARAM),
          params.getString("com.bbn.tac.eal.systemName"));
    } else {
      outputStores = loadStores(params.getExistingDirectory(MULTIPLE_STORES_PARAM),
          params.getStringList("com.bbn.tac.eal.storesToProcess"));
    }
    final File outputFile = params.getCreatableFile("com.bbn.tac.eal.outputFile");

    final CorpusQueryExecutor2016 queryExecutor = queryExecutorFromParamsFor2016(params);

    final ImmutableMultimap.Builder<QueryResponse2016, Symbol> queryResponseToFindingSystemB =
        ImmutableMultimap.builder();

    for (final Map.Entry<String, SystemOutputStore2016> storeEntry : outputStores.entrySet()) {
      final Symbol systemName = Symbol.from(storeEntry.getKey());
      final SystemOutputStore2016 store = storeEntry.getValue();

      for (final CorpusQuery2016 query : queries.queries()) {
        final ImmutableSet<DocEventFrameReference> systemMatchesForQuery =
            queryExecutor.queryEventFrames(store, query);
        // we group matches by doc ID to minimize the number of times
        // we need to read the system output when generating justifications
        final ImmutableMultimap<Symbol, DocEventFrameReference> matchesByDocID =
            FluentIterable.from(systemMatchesForQuery)
                .index(DocEventFrameReferenceFunctions.docID());

        // we potentially limit the number of responses returned for each system/query combination
        final Iterable<Map.Entry<Symbol, Collection<DocEventFrameReference>>> matchesByDocument =
            limit(ShufflingIterable.from(matchesByDocID.asMap().entrySet(), rng),
                maxResponsesPerQueryPerSystem);

        for (final Map.Entry<Symbol, Collection<DocEventFrameReference>> matchEntry : matchesByDocument) {
          final Symbol docID = matchEntry.getKey();
          final Collection<DocEventFrameReference> eventFramesMatchedInDoc =
              matchEntry.getValue();
          final DocumentSystemOutput2015 docSystemOutput = store.read(docID);
          final ImmutableSet<CharOffsetSpan> matchJustifications =
              matchJustificationsForDoc(eventFramesMatchedInDoc, docSystemOutput);

          final QueryResponse2016 queryResponse2016 =
              QueryResponse2016.builder().docID(docID).queryID(query.id())
                  .addAllPredicateJustifications(matchJustifications).build();

          queryResponseToFindingSystemB.put(queryResponse2016, systemName);
        }
      }
    }

    final SingleFileQueryStoreWriter queryStoreWriter =
        SingleFileQueryStoreWriter.builder().build();

    final ImmutableMultimap<QueryResponse2016, Symbol> queryResponseToFindingSystem =
        queryResponseToFindingSystemB.build();

    final CorpusQueryAssessments corpusQueryAssessments =
        CorpusQueryAssessments.builder()
            .queryReponses(queryResponseToFindingSystem.keySet())
            .queryResponsesToSystemIDs(queryResponseToFindingSystem)
            .assessments(Maps.asMap(
                queryResponseToFindingSystem.keySet(),
                // all responses start unassessed
                Functions.constant(QueryAssessment2016.UNASSASSED))).build();

    log.info("Writing {} query matches to {}", corpusQueryAssessments.queryReponses().size(),
        outputFile);
    queryStoreWriter.saveTo(corpusQueryAssessments, Files.asCharSink(outputFile, Charsets.UTF_8));
  }

  public static EREBasedCorpusQueryExecutor queryExecutorFromParamsFor2016(final Parameters params)
      throws IOException {
    return EREBasedCorpusQueryExecutor.createDefaultFor2016(
        FileUtils.loadSymbolToFileMap(params.getExistingFile("com.bbn.tac.eal.eremap")),
        ERELoader.builder().build(),
        EREToKBPEventOntologyMapper.create2016Mapping(),
        // how much difference in PJ offsets we allow
        params.getNonNegativeInteger("com.bbn.tac.eal.slack"),
        // the minimum fraction of overlap requires to match nominal CASes against each other
        params.getPositiveDouble("com.bbn.tac.eal.minNominalCASOverlap"),
        // can we match an entry point against a nominal if a name is available?
        params.getBoolean("com.bbn.tac.eal.matchBestCASTypesOnly"));
  }

  private static ImmutableSet<CharOffsetSpan> matchJustificationsForDoc(
      final Iterable<DocEventFrameReference> eventFramesMatchedInDoc,
      final DocumentSystemOutput2015 docSystemOutput) {

    final Optional<ImmutableBiMap<String, ResponseSet>> responseSetMap =
        docSystemOutput.linking().responseSetIds();
    checkState(responseSetMap.isPresent());
    final ImmutableSet.Builder<CharOffsetSpan> offsetsB = ImmutableSet.builder();

    for (final DocEventFrameReference docEventFrameReference : eventFramesMatchedInDoc) {
      final ImmutableSet<Response> responses =
          responseSetMap.get().get(docEventFrameReference.eventFrameID()).asSet();
      final ImmutableSet<CharOffsetSpan> spans = FluentIterable.from(responses)
          .transformAndConcat(ResponseFunctions.predicateJustifications()).toSet();
      offsetsB.addAll(spans);
    }
    return offsetsB.build();
  }

  private static ImmutableMap<String, SystemOutputStore2016> loadStores(
      final File storeDir, final List<String> storeList) throws IOException {
    final ImmutableMap.Builder<String, SystemOutputStore2016> ret = ImmutableMap.builder();
    for (final String storeName : storeList) {
      ret.put(storeName, SystemOutputStore2016.open(new File(storeDir, storeName)));
    }
    return ret.build();
  }

  private static ImmutableMap<String, SystemOutputStore2016> loadSingleStore(
      final File outputStoreDir, final String systemName) throws IOException {
    return ImmutableMap.of(systemName, SystemOutputStore2016.open(outputStoreDir));
  }

}
