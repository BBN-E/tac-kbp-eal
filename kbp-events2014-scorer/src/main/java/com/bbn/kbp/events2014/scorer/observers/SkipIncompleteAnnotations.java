package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Wraps another observer and causes it to skip all documents which are not completely
 * annotated.
 * @param <Answerable>
 */
public class SkipIncompleteAnnotations<Answerable> extends KBPScoringObserver<Answerable> {
	private static final Logger log = LoggerFactory.getLogger(SkipIncompleteAnnotations.class);

	private final KBPScoringObserver<Answerable> innerObserver;
	private int docs = 0;
	private int skipped = 0;

	private SkipIncompleteAnnotations(final KBPScoringObserver<Answerable> innerObserver) {
        super(innerObserver.name()+".skipIncomplete");
		this.innerObserver = checkNotNull(innerObserver);
	}

	public static <Answerable> SkipIncompleteAnnotations<Answerable> wrap(
		final KBPScoringObserver<Answerable> innerObserver)
	{
		return new SkipIncompleteAnnotations<Answerable>(innerObserver);
	}

    @Override
    public void startCorpus() {
        innerObserver.startCorpus();
    }

    @Override
    public void endCorpus() {
        innerObserver.endCorpus();
    }

	@Override
	public void writeCorpusOutput(File directory) throws IOException {
        final String msg;
		if (docs > 0) {
			msg = String.format("Skipped %d of %d documents (%.2f%%)\n", skipped, docs,
				(100.0*skipped)/docs);
		} else {
			msg = "No documents observed.";
		}
        Files.asCharSink(new File(directory, "__skipped.txt"), Charsets.UTF_8).write(msg);
        innerObserver.writeCorpusOutput(directory);
    }


	@Override
	public KBPAnswerSourceObserver answerSourceObserver(
			final SystemOutputAnswerSource<Answerable> systemOutputSource,
			final AnswerKeyAnswerSource<Answerable> answerKeyAnswerSource)
	{
		++docs;
		if (answerKeyAnswerSource.answerKey().completelyAnnotated()) {
			return innerObserver.answerSourceObserver(systemOutputSource,
				answerKeyAnswerSource);
		} else {
            ++skipped;
            return new KBPAnswerSourceObserver(systemOutputSource,
				answerKeyAnswerSource) {};
		}
	}

	/*public static <Answerable> Function<KBPScoringObserver<Answerable>, KBPScoringObserver<Answerable>> SkipIncomplete() {
		return new Function<KBPScoringObserver<Answerable>, KBPScoringObserver<Answerable>>() {
			@Override
			public KBPScoringObserver<Answerable> apply(final KBPScoringObserver<Answerable> x) {
				return SkipIncompleteAnnotations.wrap(x);
			}
		};
	}*/
}
