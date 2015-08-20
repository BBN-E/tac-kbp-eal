package com.bbn.kbp.events2014.bin.QA;

import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.bin.QA.Warnings.Warning;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Ordering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by jdeyoung on 6/30/15.
 */
abstract class QADocumentRenderer {

  private static final Logger log = LoggerFactory.getLogger(QADocumentRenderer.class);

  protected static final Ordering<Response>
      DEFAULT_OVERALL_ORDERING = Ordering.compound(ImmutableList
      .of(Response.byEvent(), Response.byRole(), Response.byFillerString(),
          Response.byCASSttring()));
  protected static final Ordering<TypeRoleFillerRealis> DEFAULT_TRFR_ORDERING =
      Ordering.compound(ImmutableList.of(
          TypeRoleFillerRealis.byType(), TypeRoleFillerRealis.byRole(),
          TypeRoleFillerRealis.byCAS(),
          TypeRoleFillerRealis.byRealis()));

  protected final Ordering<Response> overallOrdering;
  protected final Ordering<TypeRoleFillerRealis> trfrOrdering;
  protected final ImmutableMap<String, String> warningTypeToDescription;

  QADocumentRenderer(final Ordering<Response> overallOrdering,
      final Ordering<TypeRoleFillerRealis> trfrOrdering,
      final Map<String, String> warningTypeToDescription) {
    this.trfrOrdering = trfrOrdering;
    this.overallOrdering = overallOrdering;
    this.warningTypeToDescription = ImmutableMap.copyOf(warningTypeToDescription);
  }

  /* html utilities */

  protected static String bodyHeader() {
    return "<body>";
  }

  protected static String htmlHeader() {
    return "<!doctype html>\n"
        + "<html>\n"
        + "  <head>\n"
        + "    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n";
  }

  protected static String bodyFooter() {
    return "</body>";
  }

  protected static String htmlFooter() {
    return "</html>";
  }

  protected static String javascript() {
    return "<script type=\"text/javascript\">" +
        "function toggle(element) {\n " +
        "\tvar e = document.getElementById(element); "
        + "\nif(e.style.display == \"none\") { \n "
        + "\te.style.display = \"block\"; \n}"
        + "else {\n "
        + "\te.style.display = \"none\";\n}"
        + "}"
        + "</script>";
  }

  protected static String defaultStyle() {
    return "body {\n"
        + "background-color: rgb(128,128,128);\n"
        + "}\n"
        + "* {\n"
        + "\tvisibility: inherit;\n"
        + "}\n";
  }

  protected static String CSS() {
    final StringBuilder sb = new StringBuilder("<style>\n");
    for (Warning.Severity s : Warning.Severity.values()) {
      sb.append(s.CSS());
      sb.append("\n");
    }
    sb.append(defaultStyle());
    sb.append("</style>\n");
    return sb.toString();
  }

  protected static String href(final String id) {
    return String.format("<a href=\"javascript:toggle('%s')\">\n", id);
  }

  protected static String closehref() {
    return "</a>";
  }


  protected static int warningsDiv(final StringBuilder sb,
      final Iterable<Warning.Severity> severities) {
    int total = 0;
    for (final Warning.Severity s : severities) {
      sb.append("<div style=\"");
      sb.append(s.CSSClassName());
      sb.append("\" class=\"");
      sb.append(s.CSSClassName());
      sb.append("\">");
      total += 1;
    }
    return total;
  }

  protected static <K> Optional<Warning.Severity> extractWarningForType(
      Iterable<Warning> warnings,
      Iterable<K> all) {
    int numWarnings = 0;
    for (Warning w : warnings) {
      numWarnings++;
      // any severe warning is propogated
      if (w.severity().equals(Warning.Severity.MAJOR)) {
        return Optional.of(Warning.Severity.MAJOR);
      }
    }
    int totalItems = ImmutableList.copyOf(all).size();
    // if there are a lot of warning compared to the number of things, this is severe
    // there can be more than one warning per thing
    // 1 is a UI hack - this way singletons don't get propogated
    if ((numWarnings >= totalItems / 2) && totalItems > 1) {
      return Optional.of(Warning.Severity.MAJOR);
    }
    if (numWarnings > 0) {
      return Optional.of(Warning.Severity.MINOR);
    }
    return Optional.absent();
  }

  protected static final Function<Response, String> responseToString() {
    return new Function<Response, String>() {
      @Override
      public String apply(final Response r) {
        return String.format("%s: %s", r.realis().name(), r.canonicalArgument().string());
      }
    };
  }

  protected void addSection(final StringBuilder sb, final Iterable<Response> responses,
      final ImmutableMultimap<Response, Warning> warnings) {
    sb.append("<ul>\n");
    int with_warning = 0;
    int without_warning = 0;
    for (Response r : responses) {
      sb.append("<li>\n");
      final String responseString = responseToString().apply(r);
      if (warnings.containsKey(r)) {
        int totalWarnings = warningsDiv(sb, Warning
            .extractSeverity(warnings.get(r)));
        sb.append(href(r.hashCode() + ""));
        sb.append(responseString);
        sb.append(closehref());
        sb.append(String.format("<div id=\"%s\" style=\"display:inherit\" >", r.hashCode()));
        sb.append("<ul>\n");
        for (Warning w : warnings.get(r)) {
          sb.append(String.format("<li>%s: %s</li>\n", w.typeString(), w.warningString()));
        }
        sb.append("</ul>\n");
        sb.append("</div>");
        sb.append(Strings.repeat("</div>", totalWarnings));
        with_warning++;
      } else {
        sb.append(responseString);
        without_warning++;
      }
      sb.append("</li>\n");
    }
    sb.append("</ul>\n");
    log.info("saved responses with {} and without {} warnings", with_warning, without_warning);
  }

}
