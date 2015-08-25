package com.bbn.kbp.events2014.bin.QA;

import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.bin.QA.Warnings.Warning;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;

import static com.bbn.kbp.events2014.bin.QA.QADocumentRenderer.closehref;
import static com.bbn.kbp.events2014.bin.QA.QADocumentRenderer.href;
import static com.google.common.base.Functions.compose;

/**
 * Created by jdeyoung on 8/24/15.
 */
public final class RenderableCASGroup {

  private final int CASGroup;
  private final ImmutableSet<RenderableCorefResponse> renderableResponses;
  private final ImmutableSet<Warning> warnings;

  private RenderableCASGroup(final int casGroup,
      final ImmutableSet<RenderableCorefResponse> renderableResponses,
      final ImmutableSet<Warning> warnings) {
    CASGroup = casGroup;
    this.renderableResponses = renderableResponses;
    this.warnings = warnings;
  }

  public static RenderableCASGroup create(final int casGroup,
      final ImmutableSet<RenderableCorefResponse> renderableResponses,
      final ImmutableSet<Warning> warnings) {
    return new RenderableCASGroup(casGroup, renderableResponses, warnings);
  }

  public static RenderableCASGroup create(final int casGroup,
      final ImmutableSet<RenderableCorefResponse> renderableResponses) {
    return new RenderableCASGroup(casGroup, renderableResponses, ImmutableSet.<Warning>of());
  }

  public static RenderableCASGroup createFromResponsesWithoutWarnings(final int casGroup,
      final ImmutableSet<AssessedResponse> responses) {
    final ImmutableSet.Builder<RenderableCorefResponse> corefResponses = ImmutableSet.builder();
    final ImmutableMultimap<KBPString, AssessedResponse> stringToResponses = ImmutableMultimap
        .copyOf(
            Multimaps.index(responses, compose(Response.CASFunction(), AssessedResponse.Response)));
    for (final KBPString string : stringToResponses.keySet()) {
      corefResponses.add(RenderableCorefResponse.create(string, ImmutableSet.copyOf(
          stringToResponses.get(string))));
    }

    return create(casGroup, corefResponses.build());
  }

  public static RenderableCASGroup create(final Integer casGroup,
      final ImmutableSet<AssessedResponse> casGroupToResponses,
      final ImmutableSet<Warning> warnings) {
    final ImmutableSet.Builder<RenderableCorefResponse> corefResponses = ImmutableSet.builder();
    final ImmutableMultimap<KBPString, AssessedResponse> stringToResponse =
        Multimaps.index(casGroupToResponses,
            compose(Response.CASFunction(), AssessedResponse.Response));
    for (final KBPString string : stringToResponse.keySet()) {
      corefResponses.add(
          RenderableCorefResponse
              .create(string, ImmutableSet.copyOf(stringToResponse.get(string))));
    }
    return create(casGroup, corefResponses.build(), warnings);
  }

  public ImmutableSet<RenderableCorefResponse> renderableResponses() {
    return renderableResponses;
  }

  public static Function<RenderableCASGroup, ImmutableSet<RenderableCorefResponse>> renderableResponsesAsFunction() {
    return new Function<RenderableCASGroup, ImmutableSet<RenderableCorefResponse>>() {
      @Override
      public ImmutableSet<RenderableCorefResponse> apply(final RenderableCASGroup input) {
        return input.renderableResponses();
      }
    };
  }

  public String renderToHTML(final String prefix) {
    final String id = String.format("%sCASGroup-%d", prefix, CASGroup);
    final StringBuilder sb = new StringBuilder();
    // allow for togglable warnings
    sb.append("<div>");
    if (warnings.size() > 0) {
      sb.append(href(id));
      sb.append("CASGroup-").append(CASGroup);
      sb.append(closehref());
    } else {
      sb.append("CASGroup-").append(CASGroup);
    }
    sb.append(" - ");

    final Joiner semicolon = Joiner.on("; ");
    sb.append(semicolon.join(ImmutableSet.copyOf(Iterables
        .transform(RenderableCorefResponse.byCASLength().sortedCopy(renderableResponses),
            RenderableCorefResponse.corefResponseToHTMLString()))));
    sb.append("<br/>");
    // warnings
    if (warnings.size() > 0) {
      sb.append("<div id=\"").append(id).append("\" style=\'display:block\' >");
      sb.append("<ul>");
      for (final Warning w : warnings) {
        sb.append("<li>");
        sb.append(w.warningString());
        sb.append("</li>");
      }
      sb.append("</ul>");
      sb.append("</div>");
    }
    sb.append("</div>");
    return sb.toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final RenderableCASGroup that = (RenderableCASGroup) o;

    if (CASGroup != that.CASGroup) {
      return false;
    }
    if (!renderableResponses.equals(that.renderableResponses)) {
      return false;
    }
    return warnings.equals(that.warnings);

  }

  @Override
  public int hashCode() {
    int result = CASGroup;
    result = 31 * result + renderableResponses.hashCode();
    result = 31 * result + warnings.hashCode();
    return result;
  }
}
