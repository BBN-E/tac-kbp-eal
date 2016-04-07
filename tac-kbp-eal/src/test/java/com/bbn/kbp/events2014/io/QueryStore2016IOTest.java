package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.CorpusQueryAssessments;
import com.bbn.kbp.events2014.QueryResponse2016;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

/**
 * Created by jdeyoung on 3/21/16.
 */
public class QueryStore2016IOTest {

  private static final SingleFileQueryAssessmentsLoader loader =
      SingleFileQueryAssessmentsLoader.create();
  private static final SingleFileQueryStoreWriter writer =
      SingleFileQueryStoreWriter.builder().build();

  private static final Symbol foo = Symbol.from("foo");
  private static final Symbol foo2 = Symbol.from("foo2");
  private static final ImmutableList<CharOffsetSpan> spans = ImmutableList
      .of(CharOffsetSpan.fromOffsetsOnly(0, 2), CharOffsetSpan.fromOffsetsOnly(3, 4));
  private static final QueryResponse2016 fooQuery1 =
      QueryResponse2016.builder().docID(foo).queryID(foo)
          .addAllPredicateJustifications(spans).build();
  private static final QueryResponse2016 fooQuery2 =
      QueryResponse2016.builder().docID(foo2).queryID(foo2)
          .addAllPredicateJustifications(spans).build();
  private static final CorpusQueryAssessments fooStore =
      CorpusQueryAssessments.builder()
          .addAllQueryReponses(ImmutableList.of(fooQuery1, fooQuery2))
          .putAllQueryResponsesToSystemIDs(fooQuery1, ImmutableSet.of(foo))
          .putAllQueryResponsesToSystemIDs(fooQuery1, ImmutableSet.of(foo2))
          .build();

  @Test
  public void writeTest() throws IOException {
    final File out = Files.createTempFile("tmp", "f").toFile();
    out.deleteOnExit();
    writer.saveTo(fooStore, com.google.common.io.Files.asCharSink(out, Charsets.UTF_8));
  }

  @Test
  public void writeReadTest() throws IOException {
    final File out = Files.createTempFile("tmp", "f").toFile();
    out.deleteOnExit();
    writer.saveTo(fooStore, com.google.common.io.Files.asCharSink(out, Charsets.UTF_8));
    final CorpusQueryAssessments barStore =
        loader.loadFrom(com.google.common.io.Files.asCharSource(out, Charsets.UTF_8));
    assertTrue(barStore.queryReponses().equals(fooStore.queryReponses()));
    assertTrue(barStore.queryReponses().size() == 2);
    assertTrue(ImmutableList.copyOf(barStore.queryReponses()).get(0).queryID().equalTo(foo));
  }


  @Test(expected = IllegalStateException.class)
  public void emptyPJsTest() {
    final QueryResponse2016 fail = QueryResponse2016.builder().docID(foo).queryID(foo).build();
  }
}
