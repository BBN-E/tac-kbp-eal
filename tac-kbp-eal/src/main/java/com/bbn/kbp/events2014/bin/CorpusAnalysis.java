package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.gnuplot.Axis;
import com.bbn.bue.gnuplot.BarChart;
import com.bbn.bue.gnuplot.GnuPlotRenderer;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.AssessedResponseFunctions;
import com.bbn.kbp.events2014.EventArgumentLinking;
import com.bbn.kbp.events2014.FillerMentionType;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.TypeRoleFillerRealisFunctions;
import com.bbn.kbp.events2014.TypeRoleFillerRealisSet;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.LinkingStoreSource;
import com.bbn.kbp.events2014.linking.EventArgumentLinkingAligner;
import com.bbn.kbp.events2014.linking.ExactMatchEventArgumentLinkingAligner;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.bbn.kbp.events2014.TypeRoleFillerRealisFunctions.realis;
import static com.bbn.kbp.events2014.TypeRoleFillerRealisFunctions.type;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.getFirst;

/**
 * Various statistics on answer corpus
 *
 * @author Yee Seng Chan
 */
public final class CorpusAnalysis {
  private static final Logger log = LoggerFactory.getLogger(CorpusAnalysis.class);


  public static void main(String[] argv) {
    try {
      trueMain(argv);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  /*
   * Parameters:
   * - documentsToScore
   * - answerKey
   * - referenceLinking
   * - outputDir
   * - gnuplotBin
   */
  public static void trueMain(String[] argv) throws IOException {

    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    log.info(params.dump());

    final Set<Symbol> docsToScore = loadDocumentsToScore(params);

    // read the Response assessments , and reference ResponseLinkings
    final AnnotationStore goldAnswerStore = AssessmentSpecFormats.openAnnotationStore(params.getExistingDirectory("answerKey"), AssessmentSpecFormats.Format.KBP2015);
    final LinkingStore referenceLinkingStore = LinkingStoreSource.createFor2015()
        .openOrCreateLinkingStore(params.getExistingDirectory("referenceLinking"));

    // for each document, transform ResponseLinking to an EventArgumentLinking (which is a set of canonical event frames)
    // canonical in the sense that each event frame is a set of TRFR, instead of a set of Response
    // * we collect statistics over this canonical representation (instead of the original ResponseLinking), since the scorer scores on these canonical representations
    final ImmutableSet<EventArgumentLinking> linkings = toCanonicalEAL(goldAnswerStore, referenceLinkingStore, docsToScore);

    // from the entire assessment corpus, get mapping of TRFR -> Collection of AssessedResponses
    final ImmutableMultimap<TypeRoleFillerRealis, AssessedResponse> equivClassToAssessedResponse =
        getEquivClassToAssessedResponse(goldAnswerStore, docsToScore);

    // from the entire assessment corpus, get all: correct equivalence classes -> correct responses
    final ImmutableMultimap<TypeRoleFillerRealis, AssessedResponse>
        correctEquivClassToAssessedResponse =
        getCorrectEquivClassToAssessedResponse(equivClassToAssessedResponse);


    // num# correct TRFRs for each event type.role
    final ImmutableMultiset<Symbol> eventRoleCounts = toEventRoleCounts(correctEquivClassToAssessedResponse.keySet());
    // num# correct TRFRs for each realis
    final ImmutableMultiset<Symbol> realisCounts = toRealisCounts(correctEquivClassToAssessedResponse.keySet());
    // num# correct TRFRs for each mention type
    final ImmutableMultiset<Symbol> mentionTypeCounts = toMentionTypeCounts(correctEquivClassToAssessedResponse.keySet(), correctEquivClassToAssessedResponse);
    // num# event hoppers for each event type
    final ImmutableMultiset<Symbol> eventHopperPerEventTypeCounts = toEventHopperCounts(linkings);
    // num# documents containing each event type
    final ImmutableMultiset<Symbol> numberOfDocsPerEventTypeCounts = toNumberOfDocsPerEventType(correctEquivClassToAssessedResponse.keySet());

    final File outputDir = params.getCreatableDirectory("outputDir");   // root output dir

    writeToFile(eventRoleCounts, new File(outputDir, "eventRoleCount.txt"));
    writeToFile(realisCounts, new File(outputDir, "realisCount.txt"));
    writeToFile(mentionTypeCounts, new File(outputDir, "mentionTypeCount.txt"));
    writeToFile(eventHopperPerEventTypeCounts, new File(outputDir, "eventHopperPerEventTypeCount.txt"));
    writeToFile(numberOfDocsPerEventTypeCounts, new File(outputDir, "numberOfDocsPerEventTypeCount.txt"));

    final GnuPlotRenderer renderer = GnuPlotRenderer.createForGnuPlotExecutable(params.getExistingFile("gnuplotBin"));

    writeToChart(eventRoleCounts, new File(outputDir, "eventRoleCount.png"),
        renderer, "Event Role Counts", "Event Roles", "Counts");
    writeToChart(realisCounts, new File(outputDir, "realisCount.png"),
        renderer, "Realis Counts", "Realis", "Counts");
    writeToChart(mentionTypeCounts, new File(outputDir, "mentionTypeCount.png"),
        renderer, "Mention Type Counts", "MentionTypes", "Counts");
    writeToChart(eventHopperPerEventTypeCounts,
        new File(outputDir, "eventHopperPerEventTypeCount.png"),
        renderer, "Event Hopper Counts", "Event Types", "num# event hoppers");
    writeToChart(numberOfDocsPerEventTypeCounts,
        new File(outputDir, "numberOfDocsPerEventTypeCount.png"),
        renderer, "Number of documents per event type", "Event Types", "num# documents");
  }

  // EventRole counts on correct equivalence classes
  public static ImmutableMultiset<Symbol> toEventRoleCounts(
      final ImmutableSet<TypeRoleFillerRealis> equivClasses) {
    return Multisets.copyHighestCountFirst(
        ImmutableMultiset.copyOf(Iterables.transform(equivClasses, EventTypeRole)));
  }

  // Realis counts on correct equivalence classes
  public static ImmutableMultiset<Symbol> toRealisCounts(
      final ImmutableSet<TypeRoleFillerRealis> equivClasses) {
    return Multisets.copyHighestCountFirst(
        ImmutableMultiset.copyOf(Iterables.transform(equivClasses, Functions.compose(RealisSymbol, Realis))));
    /*
    final ImmutableMultimap<KBPRealis, TypeRoleFillerRealis> realisToEquivClass = Multimaps.index(equivClasses, TypeRoleFillerRealis.realisFunction());

    final List<ElementWithCount> elements = Lists.newArrayList();
    for(final Map.Entry<KBPRealis, Collection<TypeRoleFillerRealis>> entry : realisToEquivClass.asMap().entrySet()) {
      elements.add( ElementWithCount.from(entry.getKey(), entry.getValue().size()) );
    }

    Collections.sort(elements, ElementCount);
    return ImmutableList.copyOf(elements);
    */
  }

  // MentionType counts on correct equivalence classes
  public static ImmutableMultiset<Symbol> toMentionTypeCounts(
      final ImmutableSet<TypeRoleFillerRealis> targetEquivClasses,
      final ImmutableMultimap<TypeRoleFillerRealis, AssessedResponse> equivClassToAssessedResponse) {
    final ImmutableMultiset.Builder<Symbol> mentionTypes = ImmutableMultiset.builder();

    for(final TypeRoleFillerRealis equivClass : targetEquivClasses) {
      final AssessedResponse assessedResponse = Collections.max(equivClassToAssessedResponse.get(equivClass), Old2014ID);

      if(assessedResponse.response().role() == TIME) {
        mentionTypes.add(TIME);
      }
      else {
        final Optional<FillerMentionType> mentionType =
            assessedResponse.assessment().mentionTypeOfCAS();
        if (mentionType.isPresent()) {
          mentionTypes.add(Symbol.from(mentionType.get().name()));
        }
      }
    }

    return Multisets.copyHighestCountFirst(mentionTypes.build());
  }


  public static ImmutableMultiset<Symbol> toEventHopperCounts(final ImmutableSet<EventArgumentLinking> linkings) {
    final ImmutableMultiset.Builder<Symbol> ret = ImmutableMultiset.builder();

    for(final EventArgumentLinking eal : linkings) {
      for (final TypeRoleFillerRealisSet ef : eal.eventFrames()) {
        final ImmutableSet<Symbol> eventTypes = ImmutableSet.copyOf(FluentIterable.from(ef.asSet())
            .transform(type()));

        if(eventTypes.size()==1) {
          final Symbol eventType = FluentIterable.from(eventTypes).first().get();
          ret.add(eventType);
        }
        else {
          log.info("ERROR: a responseLinking set from document {} has multiple event types", eal.docID().toString());
        }
      }
    }

    return Multisets.copyHighestCountFirst(ret.build());
  }

  private static ImmutableMap<Symbol, ImmutableSet<Symbol>> getEventTypesInEachDoc(final ImmutableSet<TypeRoleFillerRealis> equivClasses) {
    final ImmutableMap.Builder<Symbol, ImmutableSet<Symbol>> ret = ImmutableMap.builder();

    final ImmutableMultimap<Symbol, TypeRoleFillerRealis> docEquivClasses =
        Multimaps.index(equivClasses, TypeRoleFillerRealisFunctions.docID());

    for(final Map.Entry<Symbol, Collection<TypeRoleFillerRealis>> entry : docEquivClasses.asMap().entrySet()) {
      final Symbol docId = entry.getKey();
      final ImmutableSet<Symbol> eventTypes = FluentIterable.from(entry.getValue()).transform(
          TypeRoleFillerRealisFunctions.type()).toSet();
      ret.put(docId, eventTypes);
    }

    return ret.build();
  }

  public static ImmutableMultiset<Symbol> toNumberOfDocsPerEventType(
      final ImmutableSet<TypeRoleFillerRealis> equivClasses) {

    // for each docid, a set of event types
    final ImmutableMap<Symbol, ImmutableSet<Symbol>> eventTypesInEachDoc = getEventTypesInEachDoc(equivClasses);

    final ImmutableMultiset.Builder<Symbol> ret = ImmutableMultiset.builder();

    for(final Map.Entry<Symbol, ImmutableSet<Symbol>> entry : eventTypesInEachDoc.entrySet()) {
      for(final Symbol et : entry.getValue()) {
        ret.add(et);
      }
    }

    return Multisets.copyHighestCountFirst(ret.build());
  }


  // transforms each ResponseLinking (a document containing sets of Responses) to an EventArgumentLinking (sets of eventFrames),
  // where each eventFrame is a canonical TypeRoleFillerRealisSet)
  // also filter away responses with Generic realis
  private static ImmutableSet<EventArgumentLinking> toCanonicalEAL(
      final AnnotationStore goldAnswerStore, final LinkingStore referenceLinkingStore,
      Set<Symbol> docsToScore)  throws IOException {
    final EventArgumentLinkingAligner aligner = ExactMatchEventArgumentLinkingAligner.create();
    final ImmutableSet.Builder<EventArgumentLinking> ret = ImmutableSet.builder();

    for (final Symbol docID : docsToScore) {
      try {
        final AnswerKey argumentKey = goldAnswerStore.read(docID);
        final Optional<ResponseLinking> referenceLinking = referenceLinkingStore.read(argumentKey);

        if (!referenceLinking.isPresent()) {
          throw new RuntimeException("Reference linking missing for " + docID);
        }

        // transform ResponseLinking to EventArgumentLinking
        final EventArgumentLinking referenceArgumentLinking = aligner.align(referenceLinking.get(), argumentKey);
        final EventArgumentLinking filteredReferenceArgumentLinking = referenceArgumentLinking.filteredCopy(REALIS_IS_NOT_GENERIC);

        ret.add(filteredReferenceArgumentLinking);

      } catch (Exception e) {
        throw new RuntimeException("Exception while processing " + docID, e);
      }
    }

    return ret.build();
  }

  // ==== methods dealing with equivalence classes ====
  private static ImmutableMultimap<TypeRoleFillerRealis, AssessedResponse> getEquivClassToAssessedResponse(final AnnotationStore goldAnswerStore, Set<Symbol> docsToScore) {
    final ImmutableMultimap.Builder<TypeRoleFillerRealis, AssessedResponse> ret = ImmutableMultimap.builder();

    for (final Symbol docID : docsToScore) {
      try {
        final AnswerKey argumentKey = goldAnswerStore.read(docID);
        ret.putAll(getEquivClassToAssessedResponse(argumentKey));
      } catch (Exception e) {
        throw new RuntimeException("Exception while processing " + docID, e);
      }
    }

    return ret.build();
  }

  private static ImmutableMultimap<TypeRoleFillerRealis, AssessedResponse> getEquivClassToAssessedResponse(final AnswerKey answerKey) {
    final Function<Response, TypeRoleFillerRealis> equivalenceClassFunction =
        TypeRoleFillerRealis.extractFromSystemResponse(answerKey
            .corefAnnotation().strictCASNormalizerFunction());

    final ImmutableMultimap<TypeRoleFillerRealis, AssessedResponse> equivClassToAnswerKeyResponses =
        Multimaps.index(
            answerKey.annotatedResponses(),
            Functions.compose(equivalenceClassFunction, AssessedResponseFunctions.response()));

    return equivClassToAnswerKeyResponses;
  }

  // NOTE: only AssessedResponses that are correct will be returned
  private static ImmutableMultimap<TypeRoleFillerRealis, AssessedResponse> getCorrectEquivClassToAssessedResponse(
      final ImmutableMultimap<TypeRoleFillerRealis, AssessedResponse> equivClasses) {
    final ImmutableMultimap.Builder<TypeRoleFillerRealis, AssessedResponse> ret = ImmutableMultimap.builder();

    for (final Map.Entry<TypeRoleFillerRealis, Collection<AssessedResponse>> entry : equivClasses.asMap().entrySet()) {
      final Collection<AssessedResponse> responses = entry.getValue();

      // a key equivalence class is correct if anyone found a correct response in that class
      final ImmutableSet<AssessedResponse> correctResponses = ImmutableSet.copyOf(FluentIterable.from(responses).filter(AssessedResponse.IsCorrectUpToInexactJustifications));

      if(correctResponses.size() >= 1) {
        ret.putAll(entry.getKey(), correctResponses);
      }
    }

    return ret.build();
  }
  // ========


  public Optional<AssessedResponse> selectFromMultipleAssessedResponses(final Collection<AssessedResponse> args) {
    if (args.isEmpty()) {
      return Optional.absent();
    }
    if (args.size() == 1) {
      return Optional.of(getFirst(args, null));
    }

    return Optional.of(Collections.max(args, Old2014ID));
  }

  private static ImmutableSet<Symbol> loadDocumentsToScore(Parameters params) throws IOException {
    final File docsToScoreList = params.getExistingFile("documentsToScore");
    final ImmutableSet<Symbol> ret = ImmutableSet.copyOf(FileUtils.loadSymbolList(docsToScoreList));
    log.info("Scoring over {} documents specified in {}", ret.size(), docsToScoreList);
    return ret;
  }

  static void writeToFile(final Multiset<Symbol> counts, final File outFile) throws IOException {
    Files.asCharSink(outFile, Charsets.UTF_8).write(
        Joiner.on("\n").join(
            FluentIterable.from(counts.entrySet())
                .transform(new Function<Multiset.Entry<Symbol>, String>() {
                             @Override
                             public String apply(final Multiset.Entry<Symbol> input) {
                               return String.format("%s\t%d", input.getElement().toString(),
                                   input.getCount());
                             }
                           }
                )) + "\n");
  }

  /*
  static void writeToFile(final ImmutableList<ElementWithCount> counts, final File outFile) throws IOException {
    Files.asCharSink(outFile, Charsets.UTF_8).write(
        Joiner.on("\n").join(
         FluentIterable.from(counts)
           .transform(new Function<ElementWithCount, String>() {
                        @Override
                        public String apply(final ElementWithCount input) {
                          return String
                              .format("%s\t%d", input.getElement().toString(), input.getCount());
                        }
                      }
           )) + "\n");
  }
  */

  static void writeToChart(final Multiset<Symbol> counts, final File outFile,
      final GnuPlotRenderer renderer,
      final String chartTitle, final String xAxisLabel, final String yAxisLabel)
      throws IOException {

    final Axis X_AXIS = Axis.xAxis().setLabel(xAxisLabel).rotateLabels().build();
    final Axis Y_AXIS = Axis.yAxis().setLabel(yAxisLabel).build();

    final BarChart.Builder chartBuilder =
        BarChart.builder().setTitle(chartTitle).setXAxis(X_AXIS).setYAxis(Y_AXIS).hideKey();

    for (final Multiset.Entry<Symbol> e : counts.entrySet()) {
      chartBuilder
          .addBar(BarChart.Bar.builder(e.getCount()).setLabel(e.getElement().toString()).build());
    }

    renderer.renderTo(chartBuilder.build(), outFile);
  }

  /*
  static void writeToChart(final ImmutableList<ElementWithCount> counts, final File outFile,
      final GnuPlotRenderer renderer,
      final String chartTitle, final String xAxisLabel, final String yAxisLabel) throws IOException {

    final Axis X_AXIS = Axis.xAxis().setLabel(xAxisLabel).rotateLabels().build();
    final Axis Y_AXIS = Axis.yAxis().setLabel(yAxisLabel).build();

    final BarChart.Builder chartBuilder = BarChart.builder().setTitle(chartTitle).setXAxis(X_AXIS).setYAxis(Y_AXIS).hideKey();

    for(final ElementWithCount e : counts) {
      chartBuilder.addBar(BarChart.Bar.builder(e.getCount()).setLabel(e.getElement().toString()).build());
    }

    renderer.renderTo(chartBuilder.build(), outFile);
  }
  */

  private static final class ElementWithCount<T> {
    private final T element;
    private final int count;

    private ElementWithCount(final T element, final int count) {
      this.element = element;
      this.count = count;
    }

    public static <T> ElementWithCount<T> from(final T element, final int count) {
      return new ElementWithCount(element, count);
    }

    public T getElement() {
      return element;
    }

    public int getCount() {
      return count;
    }
  }


  // == Functions ==
  private static final Function<TypeRoleFillerRealis, Symbol> EventTypeRole =
      new Function<TypeRoleFillerRealis, Symbol>() {
        @Override
        public Symbol apply(TypeRoleFillerRealis input) {
          return Symbol.from(input.type().toString()+"."+input.role().toString());
        }
      };

  private static final Function<TypeRoleFillerRealis, KBPRealis> Realis =
      new Function<TypeRoleFillerRealis, KBPRealis>() {
        @Override
        public KBPRealis apply(TypeRoleFillerRealis input) {
          return input.realis();
        }
      };

  private static final Function<KBPRealis, Symbol> RealisSymbol =
      new Function<KBPRealis, Symbol>() {
        @Override
        public Symbol apply(KBPRealis input) {
          return Symbol.from(input.name());
        }
      };

  // == Predicates ==
  private static final Predicate<TypeRoleFillerRealis> REALIS_IS_NOT_GENERIC =
      compose(not(equalTo(KBPRealis.Generic)), realis());


  // == Comparators ==
  private static final Ordering<AssessedResponse> Old2014ID = new Ordering<AssessedResponse>() {
    @Override
    public int compare(final AssessedResponse left, final AssessedResponse right) {
      return ComparisonChain.start()
          .compare(left.response().old2014ResponseID(), right.response().old2014ResponseID())
          .result();
    }
  };

  private static final Ordering<ElementWithCount> ElementCount = new Ordering<ElementWithCount>() {
    @Override
    public int compare(final ElementWithCount left, final ElementWithCount right) {
      return ComparisonChain.start()
          .compare(right.getCount(), left.getCount())
          .result();
    }
  };

  private static final Symbol TIME = Symbol.from("Time");
}
