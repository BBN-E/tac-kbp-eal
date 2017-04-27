package com.bbn.kbp;

import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.OffsetRange;

/**
 * - provenances
 *   - column right after the triple
 *   - comma separated
 *   - (QUESTION) document ID followed by a colon followed by dash-separated offsets (check if inclusive)
 *   - EG: docID:1-10,doc2ID:5-12
 *   - first provenance should contain a name as canonical_mention for a named entity if possible
 *     - (QUESTION) look into bottom of page 14 of CondStartTaskDescription
 *   - max 4 provenances
 *   - the offset of the first justification must represent the String filler, if not an entity
 *   - type requires no provenance, but all others do
 * - provenances (new)
 *   - each span must be max 200 UTF-8 characters
 *   - each span must come from a single document
 *   - four different groups of provenance spans
 *     - FILLER_STRING (exactly 1 span)
 *     - PREDICATE_JUSTIFICATION (1-4 spans, separated by comma)
 *     - BASE_FILLER (exactly 1 span)
 *     - ADDITIONAL_JUSTIFICATION (any number of spans, separated by comma)
 *   - groups of spans separated by semi-colon
 *   - see provenance section of Revisions A-G
 */
interface Kbp2017Provenance {
  String documentId();
  OffsetRange<CharOffset> offsets();
}
