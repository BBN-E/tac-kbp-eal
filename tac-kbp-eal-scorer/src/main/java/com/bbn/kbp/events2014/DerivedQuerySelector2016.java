package com.bbn.kbp.events2014;

import com.bbn.bue.common.IntIDSequence;
import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.collections.CollectionUtils;
import com.bbn.bue.common.collections.ShufflingCollection;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events.EREAligner;
import com.bbn.kbp.events.ScoreKBPAgainstERE;
import com.bbn.kbp.events.ScoringCorefID;
import com.bbn.kbp.events.ScoringEntityType;
import com.bbn.kbp.events.ScoringUtils;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events2014.io.DefaultCorpusQueryWriter;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.nlp.corenlp.CoreNLPDocument;
import com.bbn.nlp.corenlp.CoreNLPParseNode;
import com.bbn.nlp.corenlp.CoreNLPXMLLoader;
import com.bbn.nlp.corpora.ere.EREArgument;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.EREEntity;
import com.bbn.nlp.corpora.ere.EREEntityArgument;
import com.bbn.nlp.corpora.ere.EREEvent;
import com.bbn.nlp.corpora.ere.EREEventMention;
import com.bbn.nlp.corpora.ere.ERELoader;
import com.bbn.nlp.parsing.HeadFinders;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Provides;

import org.immutables.func.Functional;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.bbn.kbp.events2014.ResponseFunctions.type;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;

/**
 * Since there was additional query assessment time available in the TAC KBP 2016 evaluation,
 * the LDC offered to assess additional diagnostic queries.
 *
 * This program generated two query-sets, N1 and N2. N1 consists of entry points found by those
 * systems that (a) produced cross-document results and (b) had document-level recall great than 1
 * N2 consists of Y per-system entry points found by at least one of those systems (distributed
 * evenly across the systems). For establishing N1 and N2 we will take the highest recall
 * submission from each site.
 *
 * Entry points are automatically generated from correct responses with the following filters
 * (that were a part of LDC's query generation process): (1) Remove all responses that are from
 * an non-cross document event type; (2) Remove all responses that align with a non-ACTUAL assertion
 * (3) Remove all responses for which the CAS is non-name in the reference ERE
 */
public final class DerivedQuerySelector2016 {
  private static final Logger log = LoggerFactory.getLogger(DerivedQuerySelector2016.class);

  private final EREToKBPEventOntologyMapper ontologyMapper;
  private final ERELoader ereLoader;

  private static final ImmutableSet<Symbol> BANNED_EVENTS = SymbolUtils.setFrom(
      "Transaction.Transaction", "Contact.Contact", "Contact.Broadcast");

  @Inject
  DerivedQuerySelector2016(
      final EREToKBPEventOntologyMapper ontologyMapper,
      final ERELoader ereLoader) {
    this.ontologyMapper = checkNotNull(ontologyMapper);
    this.ereLoader = checkNotNull(ereLoader);
  }

  void go(Parameters params) throws IOException {
    final ImmutableSet<Symbol> docIDsToScore = ImmutableSet.copyOf(
        FileUtils.loadSymbolList(params.getExistingFile("docIDsToScore")));
    final ImmutableMap<Symbol, File> goldDocIDToFileMap = FileUtils.loadSymbolToFileMap(
        Files.asCharSource(params.getExistingFile("eremap"), Charsets.UTF_8));
    final File baseSystemDir = params.getExistingDirectory("systemsDir");

    final Set<String> systemsToUse = params.getStringSet("systemsToUse");
    if (systemsToUse.size() == 0) {
      throw new RuntimeException("No systems to use specified");
    }

    final File outputQueriesFile = params.getCreatableFile("outputQueriesFile");
    final int numCommonQueries = params.getOptionalInteger("numCommonQueries").or(140);
    final int numSingleSystemQueries = params.getOptionalInteger("numSingleSystemQueries").or(45);
    log.info("Will include {} queries which may be matched by only a single system",
        numSingleSystemQueries);

    // we use a CoreNLP analysis of the source documents to allow some degree of fuzzy matching
    // against ERE
    final CoreNLPXMLLoader coreNLPXMLLoader =
        CoreNLPXMLLoader.builder(HeadFinders.<CoreNLPParseNode>getEnglishPTBHeadFinder()).build();
    final ImmutableMap<Symbol, File> coreNLPProcessedRawDocs;
      log.info("Relaxing scoring using CoreNLP");
      coreNLPProcessedRawDocs = FileUtils.loadSymbolToFileMap(
          Files.asCharSource(params.getExistingFile("coreNLPDocIDMap"), Charsets.UTF_8));

    log.info("Scoring over {} documents", docIDsToScore.size());

    final ImmutableSetMultimap<String, EntryPoint> entryPointsFoundBySystems =
        gatherEntryPointsFound(docIDsToScore, baseSystemDir, systemsToUse, goldDocIDToFileMap,
            coreNLPXMLLoader, coreNLPProcessedRawDocs);

    final ImmutableListMultimap<DocAndHopper, EntryPoint> docEventsToMatchedEntryPoints =
        FluentIterable.from(entryPointsFoundBySystems.values())
            .index(EntryPointFunctions.docAndHopper());

    final ImmutableSetMultimap<String, DocAndHopper> docEventsFoundBySystemMap =
        ImmutableSetMultimap.copyOf(Multimaps.transformValues(entryPointsFoundBySystems,
            EntryPointFunctions.docAndHopper()));

    log.info("Number of document events found by each system: {}",
        Maps.transformValues(docEventsFoundBySystemMap.asMap(),
            CollectionUtils.sizeFunction()));

    final CorpusQuerySet2016.Builder queriesB = CorpusQuerySet2016.builder();

    final ImmutableSet<DocAndHopper> docEventsFoundByAllSystems =
        intersectedCopy(docEventsFoundBySystemMap.asMap().values());
    log.info("# document events with where each system finds at least one entry point: {}",
        docEventsFoundByAllSystems.size());

    // track the distribution of queries across event types for diagnostic purposes
    final ImmutableMultiset.Builder<String> queryEventTypesB = ImmutableMultiset.builder();

    for (final Map.Entry<String, Collection<EntryPoint>> e : entryPointsFoundBySystems
        .asMap().entrySet()) {
      final String systemName = e.getKey();
      final ImmutableSet<DocAndHopper> docEventsFoundBySystemButNotAll = FluentIterable.from(e.getValue())
          .transform(EntryPointFunctions.docAndHopper())
          .filter(not(in(docEventsFoundByAllSystems)))
          .toSet();
      final ImmutableSet<DocAndHopper> selected =
          FluentIterable.from(ShufflingCollection.shufflingCopy(docEventsFoundBySystemButNotAll, new Random(0)))
          .limit(numSingleSystemQueries)
          .toSet();

      log.info("Selecting {} document events found by {} but not all systems", selected.size(),
          systemName);

      final IntIDSequence sysIDSeq = IntIDSequence.startingFrom(0);
      for (final DocAndHopper docAndHopper : selected) {
        final Symbol queryID = Symbol.from("SINGLE_SYS_" + systemName + "_"
            + String.format("%04d", sysIDSeq.nextID()));
        final CorpusQuery2016.Builder queryB = new CorpusQuery2016.Builder().id(queryID);
        for (final EntryPoint entryPoint : docEventsToMatchedEntryPoints.get(docAndHopper)) {
          queryB.addEntryPoints(CorpusQueryEntryPoint.of(Symbol.from(entryPoint.docAndHopper().docID()),
              Symbol.from(entryPoint.docAndHopper().hopperID()),
              Symbol.from(entryPoint.roleAndID().role()),
              Symbol.from(entryPoint.roleAndID().entityID())));
        }
        queryEventTypesB.add(docAndHopper.type());
        queriesB.addQueries(queryB.build());
      }
    }

    final ImmutableSet<DocAndHopper> selectedFromCommon = FluentIterable
        .from(ShufflingCollection.shufflingCopy(docEventsFoundByAllSystems, new Random(0)))
        .limit(numCommonQueries)
        .toSet();

    final IntIDSequence commonIdSeq = IntIDSequence.startingFrom(0);
    for (final DocAndHopper docEventFoundByAll : selectedFromCommon) {
      final Symbol queryID = Symbol.from("COMMON_" + String.format("%04d", commonIdSeq.nextID()));
      final CorpusQuery2016.Builder queryB = new CorpusQuery2016.Builder().id(queryID);
      for (final EntryPoint entryPoint : docEventsToMatchedEntryPoints.get(docEventFoundByAll)) {
        queryB.addEntryPoints(CorpusQueryEntryPoint.of(Symbol.from(entryPoint.docAndHopper().docID()),
            Symbol.from(entryPoint.docAndHopper().hopperID()),
            Symbol.from(entryPoint.roleAndID().role()),
            Symbol.from(entryPoint.roleAndID().entityID())));
      }
      queryEventTypesB.add(docEventFoundByAll.type());
      queriesB.addQueries(queryB.build());
    }

    final CorpusQuerySet2016 queries = queriesB.build();
    log.info("Query event distribution: {}",
        Multisets.copyHighestCountFirst(queryEventTypesB.build()));
    log.info("Writing {} queries to {}", queries.queries().size(), outputQueriesFile);
    DefaultCorpusQueryWriter.create().writeQueries(queries,
        Files.asCharSink(outputQueriesFile, Charsets.UTF_8));
  }

  private ImmutableSetMultimap<String, EntryPoint> gatherEntryPointsFound(
      final ImmutableSet<Symbol> docIDsToScore, final File baseSystemDir,
      final Set<String> systemsToUse, final ImmutableMap<Symbol, File> goldDocIDToFileMap,
      final CoreNLPXMLLoader coreNLPXMLLoader,
      final ImmutableMap<Symbol, File> coreNLPProcessedRawDocs) throws IOException {
    final ImmutableSetMultimap.Builder<String, EntryPoint> entryPointsFoundBySystemB =
        ImmutableSetMultimap.builder();

    // we log the distribution of event types in the corpus for diagnostic purposes
    final ImmutableMultiset.Builder<String> allEREEventTypes = ImmutableMultiset.builder();

    for (Symbol docID : docIDsToScore) {
      // EvalHack - 2016 dry run contains some files for which Serif spuriously adds this document ID
      docID = Symbol.from(docID.asString().replace("-kbp", ""));
      final EREDocument ereDoc = getEREDocument(docID, goldDocIDToFileMap);
      final CoreNLPDocument coreNLPDoc =
          coreNLPXMLLoader.loadFrom(coreNLPProcessedRawDocs.get(docID));
      final EREAligner ereAligner = EREAligner.create(ereDoc, Optional.of(coreNLPDoc),
          ontologyMapper);

      // build an index of RoleAndIds to hoppers for lookup in the loop below
      final ImmutableSetMultimap<RoleAndID, DocAndHopper> argsToDocEvents =
          indexArgsToEventHopper(docID, ereDoc, allEREEventTypes);

      for (final String system : systemsToUse) {
        final File systemDir = new File(baseSystemDir, system);

        try (final SystemOutputStore systemOutput = KBPEA2016OutputLayout.get().open(systemDir)) {
          final ImmutableSet.Builder<RoleAndID> alignedEREArgs = ImmutableSet.builder();

          // first, gather all document-level arguments which this system found which can
          // be aligned to ERE
          final Iterable<Response> responses = FluentIterable
              .from(systemOutput.read(docID).arguments().responses())
              .filter(ScoreKBPAgainstERE.BANNED_ROLES_FILTER)
              .filter(Predicates.compose(not(in(BANNED_EVENTS)), type()));

          for (final Response response : responses) {
            final Optional<ScoringCorefID> argID =
                ereAligner.argumentForResponse(response);
            // query entry points can only be entities, not value fillers
            if (argID.isPresent() && ScoringEntityType.Entity.equals(argID.get().scoringEntityType())) {
              alignedEREArgs.add(RoleAndID.of(response.type().asString(), response.role().asString(),
                  argID.get().withinTypeID()));
            }
          }

          // second, map these to document-level hoppers
          for (final RoleAndID alignedArg : alignedEREArgs.build()) {
            for (final DocAndHopper docAndHopper : argsToDocEvents.get(alignedArg)) {
              entryPointsFoundBySystemB.put(system, EntryPoint.of(docAndHopper, alignedArg));
            }
          }
        }
      }
    }

    log.info("Distribution of event types in ERE: {}",
        Multisets.copyHighestCountFirst(allEREEventTypes.build()));

    return entryPointsFoundBySystemB.build();
  }

  private ImmutableSetMultimap<RoleAndID, DocAndHopper> indexArgsToEventHopper(final Symbol docID,
      final EREDocument ereDoc, final ImmutableMultiset.Builder<String> allEREEventTypes) {
    final ImmutableSetMultimap.Builder<RoleAndID, DocAndHopper> argsToDocEventsB =
        ImmutableSetMultimap.builder();

    for (final EREEvent ereEvent : ereDoc.getEvents()) {
      boolean loggedType = false;

      for (final EREEventMention ereEventMention : ereEvent.getEventMentions()) {
        for (final EREArgument ereEventArg : ereEventMention.getArguments()) {
          if (ereEventArg instanceof EREEntityArgument) {
            final Optional<EREEntity> entityFiller = ((EREEntityArgument) ereEventArg).ereEntity();
            if (entityFiller.isPresent()) {
              final Optional<Symbol> mappedEventType =
                  ScoringUtils.mapERETypesToDotSeparated(ontologyMapper, ereEventMention);
              final Optional<Symbol> mappedEventRole =
                  ontologyMapper.eventRole(Symbol.from(ereEventArg.getRole()));

              if (mappedEventType.isPresent() && mappedEventRole.isPresent()) {
                if (!loggedType) {
                  // we only want to log events which meet the criteria above, but we only
                  // want to count each document event once
                  allEREEventTypes.add(mappedEventType.get().asString());
                  loggedType = true;
                }
                argsToDocEventsB.put(
                    RoleAndID.of(
                        mappedEventType.get().asString(),
                        mappedEventRole.get().asString(), entityFiller.get().getID()),
                    DocAndHopper.of(docID.asString(), ereEvent.getID(),
                        mappedEventType.get().asString()));
              }
            }
          }
        }
      }
    }

    return argsToDocEventsB.build();
  }

  private EREDocument getEREDocument(final Symbol docID,
      final ImmutableMap<Symbol, File> goldDocIDToFileMap) throws IOException {
    final File ereFileName = goldDocIDToFileMap.get(docID);
    if (ereFileName == null) {
      throw new RuntimeException("Missing key file for " + docID);
    }
    final EREDocument ereDoc = ereLoader.loadFrom(ereFileName);
    // the LDC provides certain ERE documents with "-kbp" in the name. The -kbp is used by them
    // internally for some form of tracking but doesn't appear to the world, so we remove it.
    if (!ereDoc.getDocId().replace("-kbp", "").equals(docID.asString().replace(".kbp", ""))) {
      log.warn("Fetched document ID {} does not equal stored {}", ereDoc.getDocId(), docID);
    }
    return ereDoc;
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
    Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        try {
          bind(EREToKBPEventOntologyMapper.class)
              .toInstance(EREToKBPEventOntologyMapper.create2016Mapping());
        } catch (IOException ioe) {
          throw new TACKBPEALException(ioe);
        }
      }

      @Provides
      ERELoader ereLoader() {
        return ERELoader.builder().build();
      }
    }).getInstance(DerivedQuerySelector2016.class)
        .go(params.copyNamespace("com.bbn.tac.eal"));
  }

  @MoveToBUECommon
  private static <T> ImmutableSet<T> intersectedCopy(Iterable<? extends Iterable<? extends T>> iterables) {
    if (Iterables.isEmpty(iterables)) {
      return ImmutableSet.of();
    }

    ImmutableSet<T> ret = null;

    for (final Iterable<? extends T> iterable : iterables) {
      if (ret == null) {
        ret = ImmutableSet.copyOf(iterable);
      } else {
        ret = Sets.intersection(ret, ImmutableSet.copyOf(iterable)).immutableCopy();
      }

      // as soon as we realize the intersection is empty there is no need to do more work
      if (ret.isEmpty()) {
        break;
      }
    }

    return ret;
  }
}

@TextGroupImmutable
@Value.Immutable
@Functional
abstract class RoleAndID {
  abstract String type();
  abstract String role();
  abstract String entityID();

  public static RoleAndID of(String type, String role, String entityID) {
    return ImmutableRoleAndID.builder().type(type).role(role).entityID(entityID).build();
  }
}

@TextGroupImmutable
@Value.Immutable
@Functional
abstract class DocAndHopper {
  abstract String docID();
  abstract String hopperID();
  abstract String type();

  public static DocAndHopper of(String docID, String hopperID, String type) {
    return ImmutableDocAndHopper.builder().docID(docID).hopperID(hopperID).type(type).build();
  }
}

@TextGroupImmutable
@Value.Immutable
@Functional
abstract class EntryPoint {
  abstract DocAndHopper docAndHopper();
  abstract RoleAndID roleAndID();

  public static EntryPoint of(DocAndHopper docAndHopper, RoleAndID roleAndID) {
    return ImmutableEntryPoint.builder().docAndHopper(docAndHopper).roleAndID(roleAndID)
        .build();
  }
}

