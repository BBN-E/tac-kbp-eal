package com.bbn.kbp.events2014.scorer;

import com.bbn.kbp.events2014.Response;

public final class IncompleteAnnotationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private IncompleteAnnotationException(final String msg) {
		super(msg);
	}

	public static IncompleteAnnotationException forMissingResponse(final Response missingArg) {
		return new IncompleteAnnotationException(String.format(
			"Expected complete assessment, but no assessment found for response %s", missingArg));
	}
}
