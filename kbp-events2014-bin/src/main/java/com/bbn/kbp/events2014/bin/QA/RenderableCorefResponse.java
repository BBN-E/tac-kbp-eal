package com.bbn.kbp.events2014.bin.QA;

import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
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
    final StringBuilder sb = new StringBuilder();
    final ImmutableList<AssessedResponse> mostlyCorrect = ImmutableList.copyOf(
        Iterables.filter(sourceResponses, AssessedResponse.IsCorrectUpToInexactJustifications));
    if(mostlyCorrect.size() > 0) {
      sb.append("<b>");
      sb.append(string);
      sb.append("</b>");
    } else {
      sb.append(string);
    }


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


}
