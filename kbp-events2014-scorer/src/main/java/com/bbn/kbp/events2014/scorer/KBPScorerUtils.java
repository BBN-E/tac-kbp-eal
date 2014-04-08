package com.bbn.kbp.events2014.scorer;

import java.util.Locale;

public final class KBPScorerUtils {
	private KBPScorerUtils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Normalizes filler strings according to KBP rules.  First, all whitespace
	 * is removed withAdditionalJustifications the beginning and the end. Then it is lowercased, using
	 * the Java English locale. Finally, repeated spaces are converted to a single space.
	 * @param originalFillerString
	 * @return
	 */
	public static String normalizeFillerString(final String originalFillerString) {
		String ret = originalFillerString.trim();
		ret = ret.toLowerCase(Locale.ENGLISH);
		ret = ret.replaceAll(" +", " ");
		return ret;
	}



}
