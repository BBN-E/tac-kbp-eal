package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.kbp.linking.EALScorer2015Style;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

final class PerDocResultWriter implements KBP2015Scorer.SimpleResultWriter {

  private final double lambda;

  PerDocResultWriter(final double lambda) {
    checkArgument(lambda >= 0.0);
    this.lambda = lambda;
  }


  @Override
  public void writeResult(final List<EALScorer2015Style.Result> perDocResults,
      final File outputDir) throws IOException {
    Files.asCharSink(new File(outputDir, "scoresByDocument.txt"), Charsets.UTF_8).write(
        String.format("%40s\t%10s\t%10s\t%10s\t%10s\n", "Document", "Arg", "Link-P,R,F", "Link",
            "Combined") +
            Joiner.on("\n").join(
                FluentIterable.from(perDocResults)
                    .transform(new Function<EALScorer2015Style.Result, String>() {
                      @Override
                      public String apply(final EALScorer2015Style.Result input) {
                        return String.format("%40s\t%10.2f\t%7s%7s%7s\t%10.2f\t%10.2f",
                            input.docID(),
                            100.0 * input.argResult().scaledArgumentScore(),
                            String
                                .format("%.1f",
                                    100.0 * input.linkResult().linkingScore().precision()),
                            String.format("%.1f",
                                100.0 * input.linkResult().linkingScore().recall()),
                            String.format("%.1f", 100.0 * input.linkResult().linkingScore().F1()),
                            100.0 * input.linkResult().scaledLinkingScore(),
                            100.0 * input.scaledScore());
                      }
                    })));
  }
}
