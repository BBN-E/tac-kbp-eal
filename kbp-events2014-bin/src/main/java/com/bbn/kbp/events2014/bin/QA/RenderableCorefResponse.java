package com.bbn.kbp.events2014.bin.QA;

import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CharOffsetSpan;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Created by jdeyoung on 8/24/15.
 */
public final class RenderableCorefResponse {
  
  private final String string;
  private final ImmutableSet<AssessedResponse> sourceResponses;

  private RenderableCorefResponse(final String string,
      final ImmutableSet<AssessedResponse> sourceResponses) {
    this.string = string;
    this.sourceResponses = sourceResponses;
  }

  public static RenderableCorefResponse create(final KBPString string,
      final ImmutableSet<AssessedResponse> sourceResponses) {
    return create(string.string(), sourceResponses);
  }

  public static RenderableCorefResponse create(final String string,
      final ImmutableSet<AssessedResponse> sourceResponses) {
    // check that these all are compatible KBPStrings
    checkArgument(
        FluentIterable.from(sourceResponses)
            .transform(AssessedResponse.Response)
            .transform(Response.CASFunction())
            .transform(KBPString.Text)
            .toSet().size() == 1);
    return new RenderableCorefResponse(string, sourceResponses);
  }

  public String renderToHTMNL() {
    final ImmutableSet<String> pjs = FluentIterable.from(sourceResponses).transform(AssessedResponse.Response)
        .transformAndConcat(Response.predicateJustificationsFunction()).transform(
        new Function<CharOffsetSpan, String>() {
          @Override
          public String apply(final CharOffsetSpan input) {
            return input.string().orNull();
          }
        }).filter(Predicates.notNull()).toSet();
    final Joiner semicolon = Joiner.on("; ");
    final StringBuilder sb = new StringBuilder();
    sb.append("<span title=\"");
    sb.append(semicolon.join(pjs));
    sb.append("\">");
    sb.append(string);

    sb.append("</span>");
    return sb.toString();
  }

  public static Function<RenderableCorefResponse, String> corefResponseToHTMLString() {
    return new Function<RenderableCorefResponse, String>() {
      @Override
      public String apply(final RenderableCorefResponse input) {
        return input.renderToHTMNL();
      }
    };
  }

  public static Function<RenderableCorefResponse, ImmutableSet<AssessedResponse>> sourceResponsesFunction() {
    return new Function<RenderableCorefResponse, ImmutableSet<AssessedResponse>>() {
      @Override
      public ImmutableSet<AssessedResponse> apply(final RenderableCorefResponse input) {
        return input.sourceResponses;
      }
    };
  }

  public static Ordering<RenderableCorefResponse> byCASLength() {
    return new Ordering<RenderableCorefResponse>() {
      @Override
      public int compare(final RenderableCorefResponse left, final RenderableCorefResponse right) {
        return left.string.length() - right.string.length();
      }
    };
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final RenderableCorefResponse that = (RenderableCorefResponse) o;

    if (!string.equals(that.string)) {
      return false;
    }
    return sourceResponses.equals(that.sourceResponses);

  }

  @Override
  public int hashCode() {
    int result = string.hashCode();
    result = 31 * result + sourceResponses.hashCode();
    return result;
  }
}
