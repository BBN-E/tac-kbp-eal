package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.FillerMentionType;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

import junit.framework.TestCase;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class TestLDCIO extends TestCase {

  private final Symbol docid = Symbol.from("AFP_ENG_20030304.0250");
  private Scored<Response> arg;
  private ResponseAssessment ann;
  private AssessedResponse annArg;
  private String argMetadata;

  @Override
  public void setUp() {
    final KBPString cas = KBPString.from("Phillipines",
        CharOffsetSpan.fromOffsetsOnly(42, 64));
    arg = Scored.from(Response.of(docid,
        Symbol.from("Conflict.Attack"),
        Symbol.from("Attacker"),
        cas,
        cas.charOffsetSpan(),
        ImmutableSet.of(CharOffsetSpan.fromOffsetsOnly(327, 397)),
        ImmutableSet.of(CharOffsetSpan.fromOffsetsOnly(373, 383)),
        KBPRealis.Actual), 0.5465980768203735);
    ann = ResponseAssessment
        .of(Optional.of(FieldAssessment.CORRECT), Optional.of(FieldAssessment.CORRECT),
            Optional.of(FieldAssessment.CORRECT),
            Optional.of(KBPRealis.Actual),
            Optional.of(FieldAssessment.CORRECT),
            Optional.of(FillerMentionType.NAME));
    argMetadata = "metadata";
    annArg = AssessedResponse.of(arg.item(), ann);
  }

  @Test
  public void testArgumentRoundtrip2014() throws IOException {
    testArgumentRoundtrip(AssessmentSpecFormats.Format.KBP2014);
  }

  @Test
  public void testArgumentRoundtrip2015() throws IOException {
    testArgumentRoundtrip(AssessmentSpecFormats.Format.KBP2015);
  }

  @Test
  public void testFilter() throws IOException {
    ArgumentOutput raw = ArgumentOutput
        .from(docid, ImmutableList.of(arg), ImmutableMap.of(arg.item(), argMetadata));
    ArgumentOutput filtered = raw.copyWithFilteredResponses(Predicates.<Scored<Response>>alwaysTrue());
    assertEquals(raw.metadata(arg.item()), filtered.metadata(arg.item()));
    assertEquals(raw.metadata(arg.item()), argMetadata);
  }

  public void testArgumentRoundtrip(AssessmentSpecFormats.Format format) throws IOException {
    final File tmpDir = Files.createTempDir();
    tmpDir.deleteOnExit();

    final ArgumentStore store1 = AssessmentSpecFormats.createSystemOutputStore(tmpDir, format);
    store1.write(
        ArgumentOutput.from(docid, ImmutableList.of(arg), ImmutableMap.of(arg.item(), argMetadata)));
    store1.close();

    final ArgumentStore source2 = AssessmentSpecFormats.openSystemOutputStore(tmpDir, format);
    final ArgumentOutput rereadArg = source2.read(docid);
    source2.close();

    assertEquals(1, rereadArg.size());
    assertEquals(arg, Iterables.getFirst(rereadArg.scoredResponses(), null));
    assertEquals(argMetadata, rereadArg.metadata(arg.item()));
  }

  @Test
  public void testAnnotationRoundtrip2014() throws IOException {
    testAnnotationRoundtrip(AssessmentSpecFormats.Format.KBP2014);
  }

  @Test
  public void testAnnotationRoundtrip2015() throws IOException {
    testAnnotationRoundtrip(AssessmentSpecFormats.Format.KBP2015);
  }

  public void testAnnotationRoundtrip(AssessmentSpecFormats.Format format) throws IOException {
    final File tmpDir = Files.createTempDir();
    tmpDir.deleteOnExit();

    final AnnotationStore store1 = AssessmentSpecFormats.createAnnotationStore(tmpDir, format);
    final CorefAnnotation coref = CorefAnnotation.strictBuilder(docid)
        .corefCAS(annArg.response().canonicalArgument(), 1).build();
    store1.write(
        AnswerKey.from(docid, ImmutableList.of(annArg), ImmutableList.<Response>of(), coref));
    store1.close();
    final AnnotationStore store2 = AssessmentSpecFormats.openAnnotationStore(tmpDir, format);
    final AnswerKey rereadArg = store2.read(docid);
    store2.close();
    assertEquals(1, rereadArg.annotatedResponses().size());
    assertEquals(annArg, Iterables.getFirst(rereadArg.annotatedResponses(), null));
  }

    /*@Ignore("temporarily ignored until we can update the file")
    @Test
    public void existing() throws IOException {
        final String docid = "APW_ENG_20030513.0139";

        final File tmpDir = Files.createTempDir();
        tmpDir.deleteOnExit();
        final File tmpFile = new File(tmpDir, docid);
        tmpFile.deleteOnExit();

        Resources.asCharSource(Resources.getResource(TestLDCIO.class, "/"+docid),
                Charsets.UTF_8).copyTo(Files.asCharSink(tmpFile, Charsets.UTF_8));
        final AnnotationStore annStore = AssessmentSpecFormats.openAnnotationStore(tmpDir);
        final AnswerKey key = annStore.read(Symbol.from(docid));

    } */
}
