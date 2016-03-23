package com.bbn.kbp.events2014.io;


import com.bbn.kbp.events2014.AssessedQuery2016;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.QueryAssessment2016;
import com.bbn.kbp.events2014.QueryResponse2016;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.io.CharSink;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * See {@link SingleFileQueryStoreLoader} for documentation on file format.
 */
public final class SingleFileQueryStoreWriter {

  private final static Joiner commaJoiner = Joiner.on(",");
  private final static Joiner dashJoiner = Joiner.on("-");
  private final static Joiner tabJoiner = Joiner.on("\t");

  private SingleFileQueryStoreWriter() {

  }

  public void saveTo(final QueryAssessmentStore2016 store, final CharSink sink)
      throws IOException {
    final Writer out = sink.openStream();
    for (final QueryResponse2016 q : by2016Ordering().immutableSortedCopy(store.queries())) {
      final Optional<String> metadata = Optional.fromNullable(store.metadata().get(q));
      if (metadata.isPresent()) {
        out.write("#" + metadata.get() + "\n");
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
      for (final CharOffsetSpan pj : Ordering.natural()
          .immutableSortedCopy(q.predicateJustifications())) {
        pjStrings.add(dashJoiner.join(pj.startInclusive(), pj.endInclusive()));
      }
      final String pjString = commaJoiner.join(pjStrings.build());
      final String line =
          tabJoiner.join(q.queryID(), q.docID(), q.systemID(), pjString, assessment.name());
      out.write(line + "\n");
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


  private static final Ordering<QueryResponse2016> byQueryID = new Ordering<QueryResponse2016>() {
    @Override
    public int compare(@Nullable final QueryResponse2016 left,
        @Nullable final QueryResponse2016 right) {
      checkNotNull(left);
      checkNotNull(right);
      return left.queryID().asString().compareTo(right.queryID().asString());
    }
  };

  private static Ordering<QueryResponse2016> byQueryID() {
    return byQueryID;
  }

  private static final Ordering<QueryResponse2016> byDocID = new Ordering<QueryResponse2016>() {
    @Override
    public int compare(@Nullable final QueryResponse2016 left,
        @Nullable final QueryResponse2016 right) {
      checkNotNull(left);
      checkNotNull(right);
      return left.docID().asString().compareTo(right.docID().asString());
    }
  };

  private static Ordering<QueryResponse2016> byDocID() {
    return byDocID;
  }

  private static final Ordering<QueryResponse2016> bySystemID = new Ordering<QueryResponse2016>() {
    @Override
    public int compare(@Nullable final QueryResponse2016 left,
        @Nullable final QueryResponse2016 right) {
      checkNotNull(left);
      checkNotNull(right);
      return left.systemID().asString().compareTo(right.systemID().asString());
    }
  };

  private static Ordering<QueryResponse2016> bySystemID() {
    return bySystemID;
  }

  private static final Ordering<QueryResponse2016> byPJOffsets = new Ordering<QueryResponse2016>() {
    @Override
    public int compare(@Nullable final QueryResponse2016 left,
        @Nullable final QueryResponse2016 right) {
      checkNotNull(left);
      checkNotNull(right);
      checkArgument(left.docID() == right.docID(),
          "Refusing to compare PJs between different documents!");
      // iterate through comparing the PJs. These are guaranteed to be sorted by lowest index, then shortest length.
      final List<CharOffsetSpan> leftSpans = ImmutableList.copyOf(left.predicateJustifications());
      final List<CharOffsetSpan> rightSpans =
          ImmutableList.copyOf(right.predicateJustifications());
      for (int i = 0; i < leftSpans.size() && i < rightSpans.size(); i++) {
        final int cmp = leftSpans.get(i)
            .compareTo(rightSpans.get(i));
        if (cmp != 0) {
          return cmp;
        }
      }
      // if everything has been the same, the shorter list appears earlier
      return new Integer(leftSpans.size()).compareTo(rightSpans.size());
    }
  };

  private static Ordering<QueryResponse2016> byPJOffsets() {
    return byPJOffsets;
  }

  private static Ordering<QueryResponse2016> by2016Ordering() {
    return byQueryID.compound(byDocID).compound(bySystemID).compound(byPJOffsets());
  }

}
