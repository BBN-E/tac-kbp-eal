package com.bbn.kbp.events2014;

import com.bbn.bue.common.collections.CollectionUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events.EREAligner;
import com.bbn.kbp.events.ScoreKBPAgainstERE;
import com.bbn.kbp.events.ScoringCorefID;
import com.bbn.kbp.events.ScoringEntityType;
import com.bbn.kbp.events.ontology.EREToKBPEventOntologyMapper;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.transformers.QuoteFilter;
import com.bbn.nlp.corenlp.CoreNLPDocument;
import com.bbn.nlp.corenlp.CoreNLPParseNode;
import com.bbn.nlp.corenlp.CoreNLPXMLLoader;
import com.bbn.nlp.corpora.ere.EREDocument;
import com.bbn.nlp.corpora.ere.ERELoader;
import com.bbn.nlp.parsing.HeadFinders;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.filter;

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

  private final QuoteFilter quoteFiler;
  private final EREToKBPEventOntologyMapper ontologyMapper;
  private final ERELoader ereLoader;

  @Inject
  DerivedQuerySelector2016(
      final EREToKBPEventOntologyMapper ontologyMapper,
      final QuoteFilter quoteFilter,
      final ERELoader ereLoader) {
    this.ontologyMapper = checkNotNull(ontologyMapper);
    this.quoteFiler = checkNotNull(quoteFilter);
    this.ereLoader = checkNotNull(ereLoader);
  }

  private static final ImmutableSet<ScoringEntityType> GOOD_ALIGNMENT_TYPES = ImmutableSet.of(
      ScoringEntityType.Entity, ScoringEntityType.Filler, ScoringEntityType.Time);

  void go(Parameters params) throws IOException {
    final ImmutableSet<Symbol> docIDsToScore = ImmutableSet.copyOf(
        FileUtils.loadSymbolList(params.getExistingFile("docIDsToScore")));
    final ImmutableMap<Symbol, File> goldDocIDToFileMap = FileUtils.loadSymbolToFileMap(
        Files.asCharSource(params.getExistingFile("goldDocIDToFileMap"), Charsets.UTF_8));
    final File baseSystemDir = params.getExistingDirectory("systemsDir");
    final Set<String> systemsToUse = params.getStringSet("systemsToUse");

    if (systemsToUse.size() == 0) {
      throw new RuntimeException("No systems to use specified");
    }

    // we use a CoreNLP analysis of the souce documents to allow some degree of fuzzy matching
    // against ERE
    final CoreNLPXMLLoader coreNLPXMLLoader =
        CoreNLPXMLLoader.builder(HeadFinders.<CoreNLPParseNode>getEnglishPTBHeadFinder()).build();
    final ImmutableMap<Symbol, File> coreNLPProcessedRawDocs;
      log.info("Relaxing scoring using CoreNLP");
      coreNLPProcessedRawDocs = FileUtils.loadSymbolToFileMap(
          Files.asCharSource(params.getExistingFile("coreNLPDocIDMap"), Charsets.UTF_8));

    log.info("Scoring over {} documents", docIDsToScore.size());

    for (Symbol docID : docIDsToScore) {
      // EvalHack - 2016 dry run contains some files for which Serif spuriously adds this document ID
      docID = Symbol.from(docID.asString().replace("-kbp", ""));
      final EREDocument ereDoc = getEREDocument(docID, goldDocIDToFileMap);
      final CoreNLPDocument coreNLPDoc =
          coreNLPXMLLoader.loadFrom(coreNLPProcessedRawDocs.get(docID));

      // gather which ERE arguments each system matched
      final ImmutableSetMultimap.Builder<File, ScoringCorefID> alignedEREObjectsBySystemB =
          ImmutableSetMultimap.builder();

      for (final String system : systemsToUse) {
        final File systemDir = new File(baseSystemDir, system);
        final EREAligner ereAligner = EREAligner.create(ereDoc, Optional.of(coreNLPDoc),
            ontologyMapper);

        try (final SystemOutputStore systemOutput = KBPEA2016OutputLayout.get().open(systemDir)) {
          final Iterable<Response> responses =
              filter(systemOutput.read(docID).arguments().responses(),
                  ScoreKBPAgainstERE.BANNED_ROLES_FILTER);
          for (final Response response : responses) {
            final Optional<ScoringCorefID> argID =
                ereAligner.argumentForResponse(response);
            if (argID.isPresent() && GOOD_ALIGNMENT_TYPES.contains(argID.get().scoringEntityType())) {
              alignedEREObjectsBySystemB.put(systemDir, argID.get());
            }
          }
        }
      }

      // find all ERE arguments found by all systems
      final ImmutableSetMultimap<File, ScoringCorefID> alignedEREObjectsBySystem =
          alignedEREObjectsBySystemB.build();
      Set<ScoringCorefID> ereArgsFoundByAllSystems = null;
      for (final Collection<ScoringCorefID> scoringCorefIDsForSystem
          : alignedEREObjectsBySystem.asMap().values()) {
        // casts are safe because
        // "Though the method signature doesn't say so explicitly, the map returned by asMap()
        // has Set values." ~ SetMultimap Javadoc
        if (ereArgsFoundByAllSystems == null) {
          ereArgsFoundByAllSystems = (Set<ScoringCorefID>)scoringCorefIDsForSystem;
        } else {
          ereArgsFoundByAllSystems = Sets.intersection(ereArgsFoundByAllSystems,
              (Set<ScoringCorefID>)scoringCorefIDsForSystem);
        }
      }

      log.info("For {} system counts were {}, {} in common", docID,
          Maps.transformValues(alignedEREObjectsBySystem.asMap(), CollectionUtils.sizeFunction()),
          ereArgsFoundByAllSystems.size());
    }
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

  private static void trueMain(String[] argv) {

  }
}
