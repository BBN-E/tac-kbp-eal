package com.bbn.kbp.events2014.io;


import com.bbn.kbp.events2014.AssessedQuery2016;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.QueryAssessment2016;
import com.bbn.kbp.events2014.QueryResponse2016;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

public final class SingleFileQueryStoreWriter {

  private final static Joiner commaJoiner = Joiner.on(",");
  private final static Joiner dashJoiner = Joiner.on("-");
  private final static Joiner tabJoiner = Joiner.on("\t");

  private SingleFileQueryStoreWriter() {

  }

  public void saveTo(final SingleFileQueryStore2016 store, final File f)
      throws FileNotFoundException {
    final PrintWriter out = new PrintWriter(f);
    for (final QueryResponse2016 q : store.queries()) {
      final Optional<String> metadata = Optional.fromNullable(store.metadata().get(q));
      if (metadata.isPresent()) {
        out.println("#" + metadata.get());
      }
      final Optional<AssessedQuery2016> assessmentOpt =
          Optional.fromNullable(store.assessments().get(q));
      final QueryAssessment2016 assessment;
      if (assessmentOpt.isPresent()) {
        assessment = assessmentOpt.get().assessment();
      } else {
        assessment = QueryAssessment2016.UNASSASSED;
      }
      final ImmutableList.Builder<String> pjStrings = ImmutableList.builder();
      for (final CharOffsetSpan pj : q.predicateJustifications()) {
        pjStrings.add(dashJoiner.join(pj.startInclusive(), pj.endInclusive()));
      }
      final String pjString = commaJoiner.join(pjStrings.build());
      final String line =
          tabJoiner.join(q.queryID(), q.docID(), q.systemID(), pjString, assessment.name());
      out.println(line);
    }
    out.close();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {


    private Builder() {
    }

    public SingleFileQueryStoreWriter build() {
      return new SingleFileQueryStoreWriter();
    }
  }

}
