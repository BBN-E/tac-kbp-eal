package com.bbn.kbp.events2014.bin;


import com.bbn.bue.common.parameters.Parameters;
import com.bbn.kbp.events2014.CorpusQueryAssessments;
import com.bbn.kbp.events2014.io.SingleFileQueryAssessmentsLoader;
import com.bbn.kbp.events2014.io.SingleFileQueryStoreWriter;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class QueryResponseMerger {

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
    final List<String> queryFiles = params.getStringList("com.bbn.tac.eal.queryResponseFiles");
    final File resultFile = params.getCreatableFile("com.bbn.tac.eal.mergedQueryResponses");
    final SingleFileQueryAssessmentsLoader loader = SingleFileQueryAssessmentsLoader.create();
    final SingleFileQueryStoreWriter writer = SingleFileQueryStoreWriter.builder().build();
    final ImmutableList.Builder<CorpusQueryAssessments> assessmentsB = ImmutableList.builder();
    for (final String queryFile : queryFiles) {
      assessmentsB.add(loader.loadFrom(Files.asCharSource(new File(queryFile), Charsets.UTF_8)));
    }
    final ImmutableList<CorpusQueryAssessments> assessments = assessmentsB.build();
    final CorpusQueryAssessments.Builder mergedB = CorpusQueryAssessments.builder();
    for (final CorpusQueryAssessments output : assessments) {
      mergedB.addAllQueryReponses(output.queryReponses());
      mergedB.putAllQueryResponsesToSystemIDs(output.queryResponsesToSystemIDs());
      mergedB.putAllAssessments(output.assessments());
      mergedB.putAllMetadata(output.metadata());
    }
    final CorpusQueryAssessments merged = mergedB.build();
    writer.saveTo(merged, Files.asCharSink(resultFile, Charsets.UTF_8));
  }
}
