package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;

import static com.google.common.base.Preconditions.checkNotNull;

public final class EntityNormalizer {
	private EntityNormalizer(final Table<Symbol, KBPString, KBPString> docidNameToCanonicalName) {
		this.docidNameToCanonicalName = ImmutableTable.copyOf(docidNameToCanonicalName);
	}

	public static EntityNormalizer fromAnnotation(final AnswerKey answerKey) {
		final Table<Symbol, Integer, KBPString> equivalenceClassToCanonical = HashBasedTable.create();
		final Table<Symbol, KBPString, KBPString> docIdAndStringToCanonical = HashBasedTable.create();

		for (final AssessedResponse arg : answerKey.annotatedResponses()) {
			if (arg.assessment().coreferenceId().isPresent()
                    // we ignore coreference assessment when the CAS is not acceptable,
                    // since it is optional in this case
                    && FieldAssessment.isAcceptable(arg.assessment().entityCorrectFiller()))
            {
				final int corefId = arg.assessment().coreferenceId().get();
				final KBPString canonicalString = equivalenceClassToCanonical.get(arg.response().docID(), corefId);
				if (canonicalString != null) {
					final KBPString currentCanonicalization = docIdAndStringToCanonical.get(arg.response().docID(), arg.response().canonicalArgument());
					if (currentCanonicalization == null) {
						docIdAndStringToCanonical.put(arg.response().docID(), arg.response().canonicalArgument(), canonicalString);
					} else if (!currentCanonicalization.equals(canonicalString)) {
						throw new RuntimeException(String.format(
							"The same string appears with multiple coreference IDs. Docid= %s; offending string = %s; first canonicalization = %s; second canonicalization = %s",
							arg.response().docID(), arg.response().canonicalArgument(), currentCanonicalization, canonicalString));
					}
				} else {
					equivalenceClassToCanonical.put(arg.response().docID(), corefId, arg.response().canonicalArgument());
				}
			}
		}
		
		return new EntityNormalizer(docIdAndStringToCanonical);
	}

	public static EntityNormalizer createDummy() {
		return new EntityNormalizer(ImmutableTable.<Symbol, KBPString, KBPString>of());
	}

	public KBPString normalizeFiller(final Symbol docid, final KBPString s) {
		checkNotNull(s);
		final KBPString ret = docidNameToCanonicalName.get(docid, s);
		if (ret != null) {
			return ret;
		} else {
			return s;
		}
	}

	private final Table<Symbol, KBPString, KBPString> docidNameToCanonicalName;
}
