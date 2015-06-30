package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

/**
 * Created by jdeyoung on 6/15/15.
 */
public final class VeryLongCASWarningRule extends TRFRWarning {

  protected final int DEFAULT_TOO_BIG = 7;

  protected VeryLongCASWarningRule() {

  }

  @Override
  public SetMultimap<Response, Warning> applyWarning(final AnswerKey answerKey) {
    final Multimap<TypeRoleFillerRealis, Response> trfrToResponse = extractTRFRs(answerKey);
    final ImmutableSetMultimap.Builder<Response, Warning> warnings =
        ImmutableSetMultimap.builder();
    for (TypeRoleFillerRealis trfr : trfrToResponse.keySet()) {
      for (Response r : trfrToResponse.get(trfr)) {
        if (r.canonicalArgument().string().split("\\s+").length
            >= DEFAULT_TOO_BIG) {
          warnings.put(r, Warning.create(getTypeString(), String
              .format("contained more than %d tokens", DEFAULT_TOO_BIG - 1),
              Warning.Severity.MAJOR));
        }
      }
    }
    return warnings.build();
  }

  @Override
  public String getTypeString() {
    return "<b>Very Long CAS</b>";
  }

  @Override
  public String getTypeDescription() {
    final StringBuilder sb = new StringBuilder();
    sb.append("The CAS seems atypically long. Most like the CAS is WRONG, however in certain cases (e.g. a very long description of a crime, a highly modified noun phrase) the CAS can be correct. Remember, the CAS is also WRONG if it is a non-name and there is a name for the entity in the document. Rules of thumb for CAS length\n");
    sb.append("<ul>\n");
    sb.append("<li>If a name is available, a non-name is always WRONG (example “the leading doctor of the city” is WRONG if “Dr. John Smith” is available).</li>\n");
    sb.append("<li>If it is unclear what the referent entity is the CAS is WRONG (example: Jeffrey P. Minehart of the Court of Common Pleas, granted motions for acquittal on the charges against the physician, Dr. Kermit Gosnell, who ran the Women’s Medical Center, a West Philadelphia abortion clinic)</li>\n");
    sb.append("<li>In general, we would expect no more than one modifying prepositional pharse/relative clause.</li>\n");
    sb.append("</ul>\n");
    return sb.toString();
  }

  protected static final Splitter WHITESPACE_SPLITTER =
      Splitter.on("\\s+").omitEmptyStrings().trimResults();

  public static VeryLongCASWarningRule create() {
    return new VeryLongCASWarningRule();
  }
}
