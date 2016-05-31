package com.bbn.kbp.events2014;


import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.collections.LaxImmutableMapBuilder;
import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events2014.io.SingleFileQueryStoreWriter;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;
import com.bbn.nlp.corpora.ere.EREArgument;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.EREEntity;
import com.bbn.nlp.corpora.ere.EREEntityArgument;
import com.bbn.nlp.corpora.ere.EREEntityMention;
import com.bbn.nlp.corpora.ere.EREEvent;
import com.bbn.nlp.corpora.ere.EREEventMention;
import com.bbn.nlp.corpora.ere.ERELoader;
import com.bbn.nlp.corpora.ere.ERESpan;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.bbn.bue.common.symbols.SymbolUtils.byStringOrdering;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class QueryResponseFromERE {

  private static final Logger log = LoggerFactory.getLogger(QueryResponseFromERE.class);

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
    final CorpusQuerySet2016 queries =
        ERECorpusQueryLoader.create(ERELoader.builder().prefixDocIDToAllIDs(false).build(), ereMap)
            .loadQueries(Files.asCharSource(queryFile, Charsets.UTF_8));

    final ImmutableMap<String, SystemOutputStore2016> outputStores =
        loadStores(params.getExistingDirectory("com.bbn.tac.eal.storeDir"),
            params.getStringList("com.bbn.tac.eal.storesToProcess"));
    final File outputFile = params.getCreatableFile("com.bbn.tac.eal.outputFile");

    final CorpusQueryAssessments.Builder corpusQueryAssessmentsB = CorpusQueryAssessments.builder();
    final SingleFileQueryStoreWriter queryStoreWriter =
        SingleFileQueryStoreWriter.builder().build();

    final CorpusQueryExecutor2016 queryExecutor =
        DefaultCorpusQueryExecutor.createDefaultFor2016();

    final LaxImmutableMapBuilder<QueryResponse2016, QueryAssessment2016> assessmentsB =
        MapUtils.immutableMapBuilderAllowingSameEntryTwice();

    for (final Map.Entry<String, SystemOutputStore2016> store : outputStores.entrySet()) {
      for (final CorpusQuery2016 query : queries.queries()) {
        final ImmutableSet<DocEventFrameReference> docEventFrameReferences =
            queryExecutor.queryEventFrames(store.getValue(), query);
        for (final DocEventFrameReference docEventFrameReference : docEventFrameReferences) {
          final Optional<ImmutableBiMap<String, ResponseSet>> responseSetMap =
              store.getValue().read(docEventFrameReference.docID()).linking().responseSetIds();
          checkState(responseSetMap.isPresent());
          final ImmutableSet<Response> responses =
              responseSetMap.get().get(docEventFrameReference.eventFrameID()).asSet();
          // TODO do we want PJs instead of CAS?
          final ImmutableSet<CharOffsetSpan> spans =
              FluentIterable.from(responses).transform(ResponseFunctions.baseFiller()).toSet();
          final QueryResponse2016 queryResponse2016 =
              QueryResponse2016.builder().docID(docEventFrameReference.docID()).queryID(query.id())
                  .addAllPredicateJustifications(spans).build();
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

final class ERECorpusQueryLoader implements CorpusQueryLoader {

  private static final Logger log = LoggerFactory.getLogger(ERECorpusQueryLoader.class);

  private final ERELoader ereLoader;
  private final Map<Symbol, File> eremap;
  // some arbitrary fixed number of characters
  // TODO use surrounding sentences, e.g. the output of CoreNLP, here.
  private static final int WINDOW_FOR_PJS = 30;
  private final EREToKBPEventOntologyMapper typeMapper;

  ERECorpusQueryLoader(final ERELoader ereLoader, final Map<Symbol, File> eremap) {
    this.ereLoader = ereLoader;
    this.eremap = eremap;
    try {
      typeMapper = EREToKBPEventOntologyMapper.create2016Mapping();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static ERECorpusQueryLoader create(final ERELoader ereLoader,
      final Map<Symbol, File> ereMap) {
    return new ERECorpusQueryLoader(ereLoader, ereMap);
  }

  @Override
  public CorpusQuerySet2016 loadQueries(final CharSource source) throws IOException {
    final ImmutableMultimap.Builder<Symbol, CorpusQueryEntryPoint> queriesToEntryPoints =
        ImmutableMultimap.<Symbol, CorpusQueryEntryPoint>builder().orderKeysBy(byStringOrdering());
    final ImmutableSet.Builder<CorpusQuery2016> ret = ImmutableSet.builder();

    int numEntryPoints = 0;
    int lineNo = 1;
    for (final String line : source.readLines()) {
      try {
        final List<String> parts = StringUtils.onTabs().splitToList(line);
        if (parts.size() != 5) {
          throw new TACKBPEALException("Expected 5 tab-separated fields but got " + parts.size());
        }
        final Symbol queryID = Symbol.from(parts.get(0));
        final Symbol docID = Symbol.from(parts.get(1));
        final Symbol hopperID = Symbol.from(parts.get(2));
        final Symbol role = Symbol.from(parts.get(3));
        final Symbol entityID = Symbol.from(parts.get(4));

        final ImmutableSet<CorpusQueryEntryPoint> entryPoints =
            extractEntryPoints(docID, hopperID, role, entityID);
        queriesToEntryPoints.putAll(queryID, entryPoints);

        ++lineNo;
        ++numEntryPoints;

      } catch (Exception e) {
        throw new IOException("Invalid query line " + lineNo + " in " + source + ": " + line, e);
      }
    }

    for (final Map.Entry<Symbol, Collection<CorpusQueryEntryPoint>> e
        : queriesToEntryPoints.build().asMap().entrySet()) {
      ret.add(CorpusQuery2016.of(e.getKey(), e.getValue()));
    }

    final CorpusQuerySet2016 corpusQuerySet = CorpusQuerySet2016.of(ret.build());
    log.info("Loaded {} queries with {} entry points from {}", corpusQuerySet.queries().size(),
        numEntryPoints, source);
    return corpusQuerySet;

  }

  private ImmutableSet<CorpusQueryEntryPoint> extractEntryPoints(final Symbol docID,
      final Symbol hopperID, final Symbol role,
      final Symbol entityID) throws IOException {
    final ImmutableSet.Builder<CorpusQueryEntryPoint> ret = ImmutableSet.builder();

    final File ereFile = checkNotNull(eremap.get(docID));
    final EREDocument ereDoc = ereLoader.loadFrom(ereFile);
    EREEntity ereEntity = null;
    for (final EREEntity e : ereDoc.getEntities()) {
      if (e.getID().equals(entityID.asString())) {
        ereEntity = e;
        break;
      }
    }
    checkNotNull(ereEntity);

    EREEvent ereEvent = null;
    for (final EREEvent e : ereDoc.getEvents()) {
      if (e.getID().equals(hopperID.asString())) {
        ereEvent = e;
        break;
      }
    }
    checkNotNull(ereEvent);

    // TODO handle mixed type event hoppers
    final Symbol eventType = Symbol.from(
        typeMapper.eventType(Symbol.from(ereEvent.getEventMentions().get(0).getType())).get()
            .asString() + "." + typeMapper
            .eventSubtype(Symbol.from(ereEvent.getEventMentions().get(0).getSubtype())).get()
            .asString());

    // TODO what else do we need to include in the resulting events?
    for (final EREEventMention evm : ereEvent.getEventMentions()) {
      for (final EREArgument eva : evm.getArguments()) {
        // for an entity argument, the ID is the entity mention id.
        if (eva instanceof EREEntityArgument) {
          final Optional<EREEntity> ereEntityOptional = ((EREEntityArgument) eva).ereEntity();
          checkState(ereEntityOptional.isPresent(),
              "EREEntity ids must be provided in input ERE markup");
          if (ereEntityOptional.isPresent() && ereEntityOptional.get().getID()
              .equals(entityID.asString()) && eva.getRole().equals(role.asString())) {
            final EREEntityMention em = ((EREEntityArgument) eva).entityMention();
            final CorpusQueryEntryPoint ep = getCorpusQueryEntryPoint(docID, role, eventType, evm,
                (EREEntityArgument) eva, em);
            ret.add(ep);
          }
        }
      }
    }

    // add any match of the same entity in the same role for _any_ event.
    for(final EREEvent ev: ereDoc.getEvents()) {
      for(final EREEventMention evm: ev.getEventMentions()) {
        for(final EREArgument eva: evm.getArguments()) {
          if(eva instanceof EREEntityArgument) {
            final String id = ((EREEntityArgument) eva).ereEntity().get().getID();
            if(id.equals(entityID.asString()) && eva.getRole().equals(role.asString())) {
              final CorpusQueryEntryPoint ep = getCorpusQueryEntryPoint(docID, role, eventType, evm,
                  (EREEntityArgument) eva, ((EREEntityArgument) eva).entityMention());
              ret.add(ep);
            }
          }
        }
      }
    }

    return ret.build();
  }

  private CorpusQueryEntryPoint getCorpusQueryEntryPoint(final Symbol docID, final Symbol role,
      final Symbol eventType, final EREEventMention evm, final EREEntityArgument eva,
      final EREEntityMention em) {
    // TODO these are not CAS offsets
    final OffsetRange<CharOffset> casOffsets =
        OffsetRange.charOffsetRange(em.getExtent().getStart(), em.getExtent().getEnd());
    // TODO make these PJs better
    final OffsetRange<CharOffset> triggerPJ = OffsetRange
        .charOffsetRange(Math.max(evm.getTrigger().getStart() - WINDOW_FOR_PJS, 0),
            evm.getTrigger().getEnd() + WINDOW_FOR_PJS);
    final ERESpan mentionSpan = eva.entityMention().getExtent();
    final OffsetRange<CharOffset> mentionSpanPJ = OffsetRange
        .charOffsetRange(Math.max(mentionSpan.getStart() - WINDOW_FOR_PJS, 0),
            mentionSpan.getEnd() + WINDOW_FOR_PJS);
    return CorpusQueryEntryPoint.builder().docID(docID).eventType(eventType)
        .role(typeMapper.eventRole(role).get())
        .casOffsets(casOffsets)
        .predicateJustifications(ImmutableSet.of(triggerPJ, mentionSpanPJ)).build();
  }
}
