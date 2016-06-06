package com.bbn.kbp.events2014;

import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.serialization.jackson.JacksonSerializer;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.DefaultCorpusQueryWriter;
import com.bbn.kbp.events2014.io.SingleFileQueryStoreWriter;
import com.bbn.kbp.events2014.io.SystemOutputStore2016;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.assertEquals;

public class CorpusScoringTest {

  public static final double EPSILON = .000001;
  public static final String DUMMY_EVENT_FRAME_ID = "1";

  // ignored until fixed for the update to ERE-based queries
  @Ignore
  @Test
  public void testCorpusScoring() throws IOException {
    final File outputDir = Files.createTempDir();
    FileUtils.recursivelyDeleteDirectoryOnExit(outputDir);

    final Symbol doc1ID = Symbol.from("doc1");
    final String doc1 = "Mordor attacked Gondor. This is totally irrelevant.";
    final CharOffsetSpan allDoc1Sent1 = CharOffsetSpan.fromOffsetsOnly(0, 22);
    final CharOffsetSpan allDoc1Sent2 = CharOffsetSpan.fromOffsetsOnly(24, 51);

    final Symbol doc2ID = Symbol.from("doc2");
    final String doc2 = "The Witch King's invasion began with the conquest of Osgiliath.";
    final CharOffsetSpan allDoc2 = CharOffsetSpan.fromOffsetsOnly(0, 63);

    final Symbol doc3ID = Symbol.from("doc3");
    final String doc3 = "The Time Wars destroyed the planet Gallifrey.";
    final CharOffsetSpan allDoc3 = CharOffsetSpan.fromOffsetsOnly(0, 45);

    final KBPString mordor = KBPString.from("Mordor", CharOffsetSpan.fromOffsetsOnly(0, 5));
    final KBPString gondor = KBPString.from("Gondor", CharOffsetSpan.fromOffsetsOnly(17, 21));
    final KBPString witchKing = KBPString.from("Witch King", CharOffsetSpan.fromOffsetsOnly(4, 13));
    final KBPString osgiliath = KBPString.from("Osgiliath", CharOffsetSpan.fromOffsetsOnly(53, 62));
    final KBPString gallifrey = KBPString.from("Gallifrey", CharOffsetSpan.fromOffsetsOnly(35, 44));

    final Symbol conflictAttack = Symbol.from("Conflict.Attack");
    final Symbol attacker = Symbol.from("attacker");
    final Symbol target = Symbol.from("target");

    final Symbol query1 = Symbol.from("query1");

    // this needs to be fixed for the switch to ERE-based queries
    final CorpusQuery2016 gondorInvasionQuery = null/*CorpusQuery2016.of(query1,
        ImmutableSet.of(CorpusQueryEntryPoint.of(doc1ID, conflictAttack, attacker,
            mordor.charOffsetSpan().asCharOffsetRange(),
            ImmutableSet.of(allDoc1Sent1.asCharOffsetRange())),
            CorpusQueryEntryPoint.of(doc1ID, conflictAttack, target,
                gondor.charOffsetSpan().asCharOffsetRange(),
                ImmutableSet.of(allDoc1Sent1.asCharOffsetRange()))))*/;
    final CorpusQuerySet2016 querySet = CorpusQuerySet2016.builder()
        .addQueries(gondorInvasionQuery).build();

    final File queriesFile = new File(outputDir, "queries.txt");
    DefaultCorpusQueryWriter.create().writeQueries(querySet, Files.asCharSink(queriesFile,
        Charsets.UTF_8));

    final Symbol U_OF_BAR = Symbol.from("U_OF_BAR");
    final Symbol U_OF_FOO = Symbol.from("U_FOO");
    final Symbol ACME_CORP = Symbol.from("ACME_CORP");

    final QueryResponse2016 attackedInDoc1 = QueryResponse2016.of(query1, doc1ID,
        ImmutableSet.of(allDoc1Sent1));
    final QueryResponse2016 attackedInDoc1WithBadPJ = QueryResponse2016.of(query1, doc1ID,
        ImmutableSet.of(allDoc1Sent2));
    final QueryResponse2016 invasionInDoc2 = QueryResponse2016.of(query1, doc2ID,
        ImmutableSet.of(allDoc2));
    final QueryResponse2016 timeWars = QueryResponse2016.of(query1, doc3ID,
        ImmutableSet.of(allDoc3));

    final CorpusQueryAssessments assessments = CorpusQueryAssessments.builder()
        .queryReponses(ImmutableSet.of(attackedInDoc1, attackedInDoc1WithBadPJ,
            invasionInDoc2, timeWars))
        .queryResponsesToSystemIDs(ImmutableMultimap.of(
            attackedInDoc1, U_OF_BAR,
            invasionInDoc2, U_OF_BAR,
            attackedInDoc1, U_OF_FOO,
            timeWars, U_OF_FOO,
            attackedInDoc1WithBadPJ, ACME_CORP))
        .putAssessments(attackedInDoc1, QueryAssessment2016.CORRECT)
        .putAssessments(invasionInDoc2, QueryAssessment2016.CORRECT)
        .putAssessments(timeWars, QueryAssessment2016.ET_MATCH)
        .putAssessments(attackedInDoc1WithBadPJ, QueryAssessment2016.WRONG)
        .build();

    final File assessmentsFile = new File(outputDir, "assessments.txt");
    SingleFileQueryStoreWriter.builder().build().saveTo(assessments,
        Files.asCharSink(assessmentsFile, Charsets.UTF_8));

    final Response mordorAttacked = Response.builder().docID(doc1ID).type(conflictAttack)
        .role(attacker).canonicalArgument(mordor).baseFiller(mordor.charOffsetSpan())
        .addPredicateJustifications(allDoc1Sent1).realis(KBPRealis.Actual)
        .build();
    final Response attackedGondor = Response.builder().docID(doc1ID).type(conflictAttack)
        .role(target).canonicalArgument(gondor).baseFiller(gondor.charOffsetSpan())
        .realis(KBPRealis.Actual)
        .addPredicateJustifications(allDoc1Sent1).build();
    final Response mordorAttackedWithBadPJ = mordorAttacked.withPredicateJustifications(
        ImmutableSet.of(allDoc1Sent2));
    final Response witchKingInvaded = Response.builder().docID(doc2ID).type(conflictAttack)
        .role(attacker).canonicalArgument(witchKing).baseFiller(witchKing.charOffsetSpan())
        .realis(KBPRealis.Actual)
        .addPredicateJustifications(allDoc2).build();
    final Response invadedOsgiliath = Response.builder().docID(doc2ID).type(conflictAttack)
        .role(target).canonicalArgument(osgiliath).baseFiller(osgiliath.charOffsetSpan())
        .realis(KBPRealis.Actual)
        .addPredicateJustifications(allDoc2).build();
    final Response destroyedGallifrey = Response.builder().docID(doc3ID).type(conflictAttack)
        .role(target).canonicalArgument(gallifrey).baseFiller(gallifrey.charOffsetSpan())
        .realis(KBPRealis.Actual)
        .addPredicateJustifications(allDoc3).build();

    final File systemOutputDir = new File(outputDir, "systemOutputs");

    final ArgumentOutput uBarArgOutputDoc1 = ArgumentOutput.createWithConstantScore(doc1ID,
        ImmutableSet.of(attackedGondor, mordorAttacked), 1.0);
    final DocumentSystemOutput2015 uBarOutputDoc1 =
        DocumentSystemOutput2015.from(uBarArgOutputDoc1, linkAll(uBarArgOutputDoc1));

    final ArgumentOutput uBarArgOutputDoc2 = ArgumentOutput.createWithConstantScore(doc2ID,
        ImmutableSet.of(witchKingInvaded, invadedOsgiliath), 1.0);
    final DocumentSystemOutput2015 uBarOutputDoc2 =
        DocumentSystemOutput2015.from(uBarArgOutputDoc2, linkAll(uBarArgOutputDoc2));

    final ArgumentOutput uBarArgOutputDoc3 = ArgumentOutput.createWithConstantScore(doc3ID,
        ImmutableSet.<Response>of(), 1.0);
    final DocumentSystemOutput2015 uBarOutputDoc3 =
        DocumentSystemOutput2015.from(uBarArgOutputDoc3, linkAll(uBarArgOutputDoc3));

    final SystemOutputStore2016 uBarOutputStore = KBPEA2016OutputLayout.get().openOrCreate(
        new File(systemOutputDir, U_OF_BAR.asString()));

    uBarOutputStore.write(uBarOutputDoc1);
    uBarOutputStore.write(uBarOutputDoc2);
    uBarOutputStore.write(uBarOutputDoc3);
    uBarOutputStore.writeCorpusEventFrames(CorpusEventLinking.of(
        ImmutableSet.of(CorpusEventFrame.of("corpusFrame1",
            ImmutableSet.of(DocEventFrameReference.of(doc1ID, DUMMY_EVENT_FRAME_ID),
                DocEventFrameReference.of(doc2ID, DUMMY_EVENT_FRAME_ID))))));
    uBarOutputStore.close();

    final ArgumentOutput uFooArgOutputDoc1 = ArgumentOutput.createWithConstantScore(doc1ID,
        ImmutableSet.of(mordorAttacked), 1.0);
    final DocumentSystemOutput2015 uFooOutputDoc1 =
        DocumentSystemOutput2015.from(uFooArgOutputDoc1, linkAll(uFooArgOutputDoc1));
    final ArgumentOutput uFooArgOutputDoc2 = ArgumentOutput.createWithConstantScore(doc2ID,
        ImmutableSet.<Response>of(), 1.0);
    final DocumentSystemOutput2015 uFooOutputDoc2 =
        DocumentSystemOutput2015.from(uFooArgOutputDoc2, linkAll(uFooArgOutputDoc2));
    final ArgumentOutput uFooArgOutputDoc3 = ArgumentOutput.createWithConstantScore(doc3ID,
        ImmutableSet.of(destroyedGallifrey), 1.0);
    final DocumentSystemOutput2015 uFooOutputDoc3 =
        DocumentSystemOutput2015.from(uFooArgOutputDoc3, linkAll(uFooArgOutputDoc3));

    final SystemOutputStore2016 uFooOutputStore = KBPEA2016OutputLayout.get().openOrCreate(
        new File(systemOutputDir, U_OF_FOO.asString()));
    uFooOutputStore.write(uFooOutputDoc1);
    uFooOutputStore.write(uFooOutputDoc2);
    uFooOutputStore.write(uFooOutputDoc3);
    uFooOutputStore.writeCorpusEventFrames(
        CorpusEventLinking.builder()
            .addCorpusEventFrames(
                CorpusEventFrame.builder()
                    .id("corpusFrame1")
                    .addDocEventFrames(
                        DocEventFrameReference.of(doc1ID, DUMMY_EVENT_FRAME_ID),
                        DocEventFrameReference.of(doc3ID, DUMMY_EVENT_FRAME_ID)).build()).build());
    uFooOutputStore.close();

    final ArgumentOutput acmeArgOutputDoc1 = ArgumentOutput.createWithConstantScore(doc1ID,
        ImmutableSet.of(mordorAttackedWithBadPJ), 1.0);
    final DocumentSystemOutput2015 acmeOutputDoc1 =
        DocumentSystemOutput2015.from(acmeArgOutputDoc1, linkAll(acmeArgOutputDoc1));
    final ArgumentOutput acmeArgOutputDoc2 = ArgumentOutput.createWithConstantScore(doc2ID,
        ImmutableSet.<Response>of(), 1.0);
    final DocumentSystemOutput2015 acmeOutputDoc2 =
        DocumentSystemOutput2015.from(acmeArgOutputDoc2, linkAll(acmeArgOutputDoc2));
    final ArgumentOutput acmeArgOutputDoc3 = ArgumentOutput.createWithConstantScore(doc3ID,
        ImmutableSet.<Response>of(), 1.0);
    final DocumentSystemOutput2015 acmeOutputDoc3 =
        DocumentSystemOutput2015.from(acmeArgOutputDoc3, linkAll(acmeArgOutputDoc3));

    final SystemOutputStore2016 acmeOutputStore = KBPEA2016OutputLayout.get().openOrCreate(
        new File(systemOutputDir, ACME_CORP.asString()));
    acmeOutputStore.write(acmeOutputDoc1);
    acmeOutputStore.write(acmeOutputDoc2);
    acmeOutputStore.write(acmeOutputDoc3);
    acmeOutputStore.writeCorpusEventFrames(
        CorpusEventLinking.builder()
            .addCorpusEventFrames(CorpusEventFrame.builder()
                .id("corpusFrame1")
                .addDocEventFrames(DocEventFrameReference.of(doc1ID, DUMMY_EVENT_FRAME_ID)).build())
            .build());
    acmeOutputStore.close();

    final ImmutableMap.Builder<String, String> parameters = ImmutableMap.builder();
    final File scoringOutputDir = new File(outputDir, "scoringOutput");
    parameters.put("com.bbn.tac.eal.outputDir", scoringOutputDir.getAbsolutePath());
    parameters.put("com.bbn.tac.eal.queryFile", queriesFile.getAbsolutePath());
    parameters.put("com.bbn.tac.eal.queryAssessmentsFile", assessmentsFile.getAbsolutePath());
    parameters.put("com.bbn.tac.eal.systemOutputsDir", systemOutputDir.getAbsolutePath());

    CorpusScorer.trueMain(Parameters.fromMap(parameters.build()));

    final JacksonSerializer jsonDeserializer = JacksonSerializer.builder().forJson().build();

    final File uBarScoreFile = new File(new File(scoringOutputDir, U_OF_BAR.asString()),
        "aggregateF.txt.json");
    final FMeasureCounts uBarScores = (FMeasureCounts) jsonDeserializer.deserializeFrom(
        Files.asByteSource(uBarScoreFile));
    assertEquals(1.0, uBarScores.precision(), EPSILON);
    assertEquals(1.0, uBarScores.recall(), EPSILON);

    final File uFooScoreFile =
        new File(new File(scoringOutputDir, U_OF_FOO.asString()), "aggregateF.txt.json");
    final FMeasureCounts uFooScores =
        (FMeasureCounts) jsonDeserializer.deserializeFrom(Files.asByteSource(uFooScoreFile));
    assertEquals(0.5, uFooScores.precision(), EPSILON);
    assertEquals(0.5, uFooScores.recall(), EPSILON);

    final File acmeScoreFile = new File(new File(scoringOutputDir, ACME_CORP.asString()),
        "aggregateF.txt.json");
    final FMeasureCounts acmeScores =
        (FMeasureCounts) jsonDeserializer.deserializeFrom(Files.asByteSource(acmeScoreFile));
    assertEquals(0.0, acmeScores.precision(), EPSILON);
    assertEquals(0.0, acmeScores.recall(), EPSILON);
  }

  private static ResponseLinking linkAll(final ArgumentOutput bbnOutputDoc1) {
    final ResponseSet responseSet = ResponseSet.of(bbnOutputDoc1.responses());
    return ResponseLinking.builder().docID(bbnOutputDoc1.docId())
        .addResponseSets(responseSet)
        .responseSetIds(ImmutableBiMap.of(DUMMY_EVENT_FRAME_ID, responseSet))
        .build();
  }
}
