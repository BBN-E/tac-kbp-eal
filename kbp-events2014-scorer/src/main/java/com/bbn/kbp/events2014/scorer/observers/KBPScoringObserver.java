package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.kbp.events2014.AsssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.scorer.AnswerKeyAnswerSource;
import com.bbn.kbp.events2014.scorer.SystemOutputAnswerSource;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An abstract base class for scoring observers. The key method here is {@code answerSourceObserver}.
 * When the scorer examines the system
 * output for a document and compares it to the answer key, it will request from
 * the scoring observer an answer source observer whose methods will
 * be called for various sorts of events.  The default implementation is always a no-op.
 * Scoring observers should return an {@code KBPAnswerSourceObserver} object which
 * override the methods for those events they wish to act on. Please see the currently
 * implemented scorers as examples.
 *
 * @param <Answerable>
 */
public abstract class KBPScoringObserver<Answerable> {
	private final String name;

	protected KBPScoringObserver() {
		this.name=getClass().getName();
	}

	protected KBPScoringObserver(final String name) {
		this.name = checkNotNull(name);
		checkArgument(!name.isEmpty());
	}

    /**
     * Returns the object whose methods will be called
     * for document-level events.
     * @param systemOutputSource
     * @param answerKeyAnswerSource
     * @return
     */
	public abstract KBPAnswerSourceObserver answerSourceObserver(
		final SystemOutputAnswerSource<Answerable> systemOutputSource,
		final AnswerKeyAnswerSource<Answerable> answerKeyAnswerSource);

	public final String name() {
		return name;
	}

    /**
     * Called once, at the beginning of scoring.
     */
	public void startCorpus() {

	}

    /**
     * Called once, at the end of scoring.
     */
	public void endCorpus() {

	}

    /**
     * Writes corpus-level output to the specified directory
     * @param directory
     * @throws IOException
     */
	public void writeCorpusOutput(final File directory) throws IOException {

	}

    /**
     * Override the methods of this document to act on scoring events.
     */
	public abstract  class KBPAnswerSourceObserver {
		private final SystemOutputAnswerSource<Answerable> systemOutputSource;
		private final AnswerKeyAnswerSource<Answerable> answerKeyAnswerSource;

		public final AnswerKeyAnswerSource<Answerable> answerKeyAnswerSource() {
			return answerKeyAnswerSource;
		}

		public final SystemOutputAnswerSource<Answerable> systemOutputAnswerSource() {
			return systemOutputSource;
		}

        /**
         * Called once at the beginning of a document.
         */
		public void start() {
			// pass
		}

        /**
         * Called once at the end of a document.
         */
		public void end() {
			// pass
		}

        /**
         * Called once at the beginning of processing each "answerable"
         * (e.g. {@code TypeRoleFillerRealis} tuple).
         * @param answerable
         */
		public void startAnswerable(final Answerable answerable) {
			// pass
		}

        /**
         * Called once at the end of processing of each "answerable"
         * (e.g. {@code TypeRoleFillerRealis} tuple).
         * @param answerable
         */
        public void endAnswerable(final Answerable answerable) {
			// pass
		}

        /**
         * Called when the selected system response for an annotator is unannotated.
         * @param answerable
         * @param response
         */
		public void unannotatedSelectedResponse(final Answerable answerable, final Response response) {
			// pass
		}

        /**
         * Flexible method called for every answerable, giving all system responses and all
         * annotations. Typically users should override more specific methods if possible.
         * @param answerable
         * @param responses
         * @param annotations
         */
		public void observe(final Answerable answerable, final Set<Response> responses,
				final Set<AsssessedResponse> annotations)
		{
			// pass
		}

        /**
         * Called when there are system responses for an answerable, but no annotated responses
         * @param answerable
         * @param annotatedResponses
         */
		public void responsesOnlyNonEmpty(final Answerable answerable, final Set<Response> annotatedResponses) {
			// pass
		}

        /**
         * Called when there are annotated responses for an answerable, but no system responses
         * @param answerable
         * @param annotatedResponses
         */
		public void annotationsOnlyNonEmpty(final Answerable answerable, final Set<AsssessedResponse> annotatedResponses) {
			// pass
		}

        /**
         * Called when the selected system response is annotated.
         * @param answerable
         * @param response
         * @param annotatedResponse
         */
		public void annotatedSelectedResponse(final Answerable answerable, final Response response,
				final AsssessedResponse annotatedResponse)
		{
			// pass
		}

        /**
         * Get the system output for this document.
         * @return
         */
		protected final SystemOutputAnswerSource<Answerable> systemOutput() {
			return systemOutputSource;
		}

        /**
         * Get the answer key for this document.
         * @return
         */
		protected final AnswerKeyAnswerSource<Answerable> answerKey() {
            return answerKeyAnswerSource;
		}

		public void writeDocumentOutput(final File directory) throws IOException {
            /*checkNotNull(directory);
			final String docHTML = documentOut.toString();
			globalOut.append(docHTML);
			if (!docHTML.isEmpty()) {
				final String docid = systemOutputSource.systemOutput().docId().toString();
				final File output = new File(directory, docid+".html");
				Files.asCharSink(output, Charsets.UTF_8).write(writeHTML(docid, docHTML));
			}*/
		}

		public KBPAnswerSourceObserver(final SystemOutputAnswerSource<Answerable> systemOutputSource,
			final AnswerKeyAnswerSource<Answerable> answerKeyAnswerSource)
		{
			this.systemOutputSource = checkNotNull(systemOutputSource);
			this.answerKeyAnswerSource = checkNotNull(answerKeyAnswerSource);
			checkArgument(systemOutputSource.systemOutput().docId()
				== answerKeyAnswerSource.answerKey().docId());
		}
	}

    private static String writeHTML(String title, String html) {
        return "<html><head>" + title + "</head><body>" + html + "</body></html>";
    }
}