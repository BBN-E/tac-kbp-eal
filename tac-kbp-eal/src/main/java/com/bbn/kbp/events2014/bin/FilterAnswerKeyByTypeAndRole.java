package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.LinkingStoreSource;
import com.bbn.kbp.events2014.transformers.ResponseMapping;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class FilterAnswerKeyByTypeAndRole {

  private static final Logger log = LoggerFactory.getLogger(FilterOutUnannotated.class);

  private static final String USAGE = "usage: FilterAnswerKeyByTypeAndRole paramFile\n" +
      "Removes all responses whose type and role are not found\n" +
      "in the provided ontology file:\n" +
      "\tinputArguments: argument answer key to filter\n" +
      "\tinputLinking: linking answer key to filter\n" +
      "\toutputArguments: directory to write filtered argument answer key to\n" +
      "\toutputLinking: directory to write filtered linking answer key to\n" +
      "\ttypeAndRoleFile: file listing types and roles which are valid. Format is one "
      + "\t\tline per type, tab-separated, with the event type followed by its roles";

  private FilterAnswerKeyByTypeAndRole() {
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
    if (argv.length != 1) {
      System.err.println(USAGE);
      System.exit(1);
    }

    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final AnnotationStore argumentAnnotationStore = AssessmentSpecFormats.openAnnotationStore(
        params.getExistingDirectory("inputArguments"), AssessmentSpecFormats.Format.KBP2015);
    final Optional<LinkingStore> optLinkingAnnotationStore =
        getOptionalLinkingStore("inputLinking", params);
    final Optional<LinkingStore> optLinkingOut = getOptionalLinkingStore("outputLinking", params);
    checkArgument(optLinkingAnnotationStore.isPresent() == optLinkingOut.isPresent(),
        "Must specify neither or both of inputLinking and outputLinking");

    final Multimap<Symbol, Symbol> typesToValidRoles = loadTypesToValidRolesMap(params);

    if (optLinkingAnnotationStore.isPresent() &&
        !argumentAnnotationStore.docIDs().containsAll(optLinkingAnnotationStore.get().docIDs())) {
      throw new RuntimeException(
          "Linking store contains documents not contained in argument store");
    }

    final AnnotationStore outputArgumentStore = AssessmentSpecFormats.createAnnotationStore(
        params.getCreatableDirectory("outputArguments"), AssessmentSpecFormats.Format.KBP2015);

    int numDeletedTotal = 0;
    for (final Symbol docID : argumentAnnotationStore.docIDs()) {
      final AnswerKey original = argumentAnnotationStore.read(docID);
      final ResponseMapping toDelete = selectWhichToDelete(original, typesToValidRoles);

      outputArgumentStore.write(toDelete.apply(original));
      if (optLinkingAnnotationStore.isPresent()) {
        final Optional<ResponseLinking> originalLinking =
            optLinkingAnnotationStore.get().read(original);
        if (originalLinking.isPresent()) {
          optLinkingOut.get().write(toDelete.apply(originalLinking.get()));
        }
      }
    }

    argumentAnnotationStore.close();
    outputArgumentStore.close();
    if (optLinkingAnnotationStore.isPresent()) {
      optLinkingAnnotationStore.get().close();
      optLinkingOut.get().close();
    }
  }

  private static Multimap<Symbol, Symbol> loadTypesToValidRolesMap(final Parameters params)
      throws IOException {
    final Multimap<Symbol, Symbol> typesToValidRolesInitial = FileUtils.loadSymbolMultimap(
        Files.asCharSource(params.getExistingFile("typeAndRoleFile"), Charsets.UTF_8));
    final Set<Symbol> alwaysValidRoles = params.getSymbolSet("alwaysValidRoles");
    final ImmutableMultimap.Builder<Symbol, Symbol> typesToValidRolesB = ImmutableMultimap.builder();
    typesToValidRolesB.putAll(typesToValidRolesInitial);
    for (final Symbol eventType : typesToValidRolesInitial.keySet()) {
      typesToValidRolesB.putAll(eventType, alwaysValidRoles);
    }
    return typesToValidRolesB.build();
  }

  private static ResponseMapping selectWhichToDelete(final AnswerKey answerKey,
      final Multimap<Symbol, Symbol> typesToValidRoles) {
    final Set<Response> toDelete = Sets.newHashSet();
    for (final Response r : answerKey.allResponses()) {
      if (!typesToValidRoles.get(r.type()).contains(r.role())) {
        toDelete.add(r);
      }
    }
    return ResponseMapping.delete(toDelete);
  }

  private static Optional<LinkingStore> getOptionalLinkingStore(final String linkingParam,
      final Parameters params) throws FileNotFoundException {
    final Optional<File> optLinkingAnnotationStoreDir = params.getOptionalExistingDirectory(
        linkingParam);
    final Optional<LinkingStore> optLinkingAnnotationStore;
    if (optLinkingAnnotationStoreDir.isPresent()) {
      optLinkingAnnotationStore = Optional.of(
          LinkingStoreSource.createFor2015().openLinkingStore(optLinkingAnnotationStoreDir.get()));
    } else {
      optLinkingAnnotationStore = Optional.absent();
    }
    return optLinkingAnnotationStore;
  }

}
